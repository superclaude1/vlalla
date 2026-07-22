package com.storybrain.app.tts

import android.content.Context
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.data.TtsScriptEntity
import com.storybrain.app.data.TtsScriptSegmentEntity
import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.reader.TextToChatParser
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.TtsSettingsStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

data class TtsGenerationResult(val manifestPath: String, val segmentCount: Int)

class ChapterTtsEngine(
    private val context: Context,
    private val repository: StoryRepository,
    private val settings: TtsSettingsStore = TtsSettingsStore(context),
    private val llmSettings: LlmSettingsStore = LlmSettingsStore(context),
    private val edgeProvider: TtsProvider = EdgeTtsProvider(),
    private val directingService: TtsDirectingService = TtsDirectingService(llmSettings),
    private val resolver: VoiceResolver = VoiceResolver(repository, settings)
) {
    suspend fun generate(
        bookId: String,
        chapterId: String,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
        onStage: suspend (String) -> Unit = {}
    ): TtsGenerationResult {
        repository.ensureDefaultTtsProfiles()
        val chapter = repository.getChapter(chapterId) ?: error("找不到本章")
        val characters = repository.getCharacters(bookId)
        val aliases = aliases(characters)
        val characterByName = characters.associateBy { it.canonicalName }
        val blocks = TextToChatParser.parse(chapter.content, aliases)
        require(blocks.isNotEmpty()) { "本章没有可配音内容" }
        val sourceHash = sha256(chapter.content)
        val llmConfig = llmSettings.config.first()
        val scriptId = "tts-$chapterId"
        val previousScript = repository.getTtsScript(chapterId)
        val previousSegments = previousScript?.takeIf {
            it.sourceHash == sourceHash && it.llmModel == llmConfig.model && it.promptVersion == TtsDirectingService.PROMPT_VERSION
        }?.let { repository.getTtsScriptSegments(it.id) }.orEmpty()
        val cachedDirectives = previousSegments.groupBy { it.blockIndex }
            .mapValues { (_, values) -> directivesFromJson(values.first().directivesJson) }
        val directions = if (cachedDirectives.keys.containsAll(blocks.indices.toList())) {
            blocks.indices.associateWith { cachedDirectives.getValue(it) }
        } else {
            onStage("正在调用 LLM 生成演绎脚本…")
            directingService.direct(blocks) { completed, total -> onStage("正在生成演绎脚本 $completed/$total") }
                .associate { it.segmentId.toInt() to it.directives }
        }
        onStage("正在解析角色平台与音色…")
        val jobs = mutableListOf<SpeechJob>()
        blocks.forEachIndexed { blockIndex, block ->
            val speaker = (block as? ReadingBlock.Dialogue)?.speaker
            val character = speaker?.let(characterByName::get)
            val resolved = resolver.resolve(bookId, speaker, character)
            val kind = TtsProviderKind.valueOf(resolved.profile.kind)
            val chunks = splitText(block.text, when (kind) {
                TtsProviderKind.FISH_AUDIO -> 220
                TtsProviderKind.OPENAI_COMPATIBLE -> 1_000
                TtsProviderKind.EDGE -> 700
            })
            chunks.forEachIndexed { chunkIndex, text ->
                val directives = directions[blockIndex] ?: LocalTtsDirector.direct(blocks, blockIndex)
                val rendered = when (kind) {
                    TtsProviderKind.FISH_AUDIO -> TtsDirectiveRenderer.fishText(text, directives)
                    else -> text
                }
                val cacheKey = sha256("${resolved.profile.id}|${resolved.profile.model}|${resolved.voiceId}|$rendered|${directives.toJson()}|v1")
                jobs += SpeechJob(
                    index = jobs.size,
                    blockIndex = blockIndex,
                    chunkIndex = chunkIndex,
                    speaker = speaker ?: "旁白",
                    text = text,
                    directives = directives,
                    resolved = resolved,
                    renderedText = rendered,
                    cacheKey = cacheKey
                )
            }
        }
        require(jobs.isNotEmpty()) { "本章没有可配音内容" }

        val now = System.currentTimeMillis()
        val initialRows = jobs.map { it.toEntity(scriptId, TaskStatus.PENDING, null, null, now) }
        repository.saveTtsScript(
            TtsScriptEntity(
                id = scriptId,
                bookId = bookId,
                chapterId = chapterId,
                sourceHash = sourceHash,
                llmModel = llmConfig.model,
                promptVersion = TtsDirectingService.PROMPT_VERSION,
                status = TaskStatus.RUNNING.name,
                createdAt = previousScript?.createdAt ?: now,
                updatedAt = now
            ),
            initialRows
        )

        val root = File(context.filesDir, "tts").apply { mkdirs() }
        val directory = File(root, chapterId)
        val staging = File(root, "$chapterId.building")
        val backup = File(root, "$chapterId.backup")
        val cache = File(root, "$chapterId.cache").apply { mkdirs() }
        val previousManifest = chapter.ttsManifestPath?.takeIf { File(it).exists() }
        staging.deleteRecursively()
        staging.mkdirs()
        repository.updateTtsStatus(chapterId, TaskStatus.RUNNING)
        val manifestSegments = JSONArray()
        return runCatching {
            jobs.forEachIndexed { index, job ->
                coroutineContext.ensureActive()
                onStage("正在使用 ${job.resolved.profile.displayName} 生成 ${index + 1}/${jobs.size} 段")
                val fileName = "%04d.mp3".format(index)
                val cacheFile = File(cache, "${job.cacheKey}.mp3")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    synthesizeWithRetry(job, cacheFile)
                }
                val stagingFile = File(staging, fileName)
                cacheFile.copyTo(stagingFile, overwrite = true)
                val row = job.toEntity(
                    scriptId, TaskStatus.COMPLETED,
                    File(directory, fileName).absolutePath, null, System.currentTimeMillis()
                )
                repository.updateTtsScriptSegments(listOf(row))
                manifestSegments.put(job.manifest(File(directory, fileName)))
                onProgress(index + 1, jobs.size)
            }
            File(staging, "manifest.json").writeText(
                JSONObject()
                    .put("bookId", bookId)
                    .put("chapterId", chapterId)
                    .put("sourceHash", sourceHash)
                    .put("llmModel", llmConfig.model)
                    .put("promptVersion", TtsDirectingService.PROMPT_VERSION)
                    .put("generatedAt", System.currentTimeMillis())
                    .put("segments", manifestSegments)
                    .toString(2),
                Charsets.UTF_8
            )
            backup.deleteRecursively()
            if (directory.exists() && !directory.renameTo(backup)) error("无法替换旧配音文件")
            if (!staging.renameTo(directory)) {
                if (backup.exists()) backup.renameTo(directory)
                error("无法保存新配音文件")
            }
            backup.deleteRecursively()
            val manifest = File(directory, "manifest.json")
            repository.updateTtsResult(chapterId, TaskStatus.COMPLETED, manifest.absolutePath)
            repository.saveTtsScript(
                TtsScriptEntity(scriptId, bookId, chapterId, sourceHash, llmConfig.model, TtsDirectingService.PROMPT_VERSION, TaskStatus.COMPLETED.name, previousScript?.createdAt ?: now, System.currentTimeMillis()),
                jobs.mapIndexed { index, job -> job.toEntity(scriptId, TaskStatus.COMPLETED, File(directory, "%04d.mp3".format(index)).absolutePath, null, System.currentTimeMillis()) }
            )
            TtsGenerationResult(manifest.absolutePath, jobs.size)
        }.getOrElse { cause ->
            staging.deleteRecursively()
            if (!directory.exists() && backup.exists()) backup.renameTo(directory)
            repository.updateTtsResult(chapterId, if (previousManifest != null) TaskStatus.COMPLETED else TaskStatus.FAILED, previousManifest)
            val failedIndex = jobs.indexOfFirst { !File(cache, "${it.cacheKey}.mp3").exists() }.coerceAtLeast(0)
            repository.updateTtsScriptSegments(
                listOf(jobs[failedIndex].toEntity(scriptId, TaskStatus.FAILED, null, cause.message, System.currentTimeMillis()))
            )
            throw cause
        }
    }

    private suspend fun synthesizeWithRetry(job: SpeechJob, output: File) {
        val provider = provider(job.resolved)
        val request = TtsSynthesisRequest(
            text = job.text,
            voice = job.resolved.voiceId,
            model = job.resolved.profile.model,
            profileId = job.resolved.profile.id,
            directives = job.directives,
            supportsInstructions = job.resolved.profile.supportsInstructions,
            idempotencyKey = job.cacheKey
        )
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                coroutineContext.ensureActive()
                provider.synthesize(request, output)
                return
            } catch (error: Throwable) {
                last = error
                val retryable = (error as? TtsProviderException)?.retryable == true
                if (!retryable || attempt == 2) throw error
                val retryAfter = (error as? TtsProviderException)?.retryAfterMillis
                val backoff = retryAfter ?: (500L * (1 shl attempt) + Random.nextLong(0, 250))
                delay(backoff.coerceAtMost(30_000L))
            }
        }
        throw last ?: error("配音生成失败")
    }

    private suspend fun provider(resolved: ResolvedTtsVoice): TtsProvider = when (TtsProviderKind.valueOf(resolved.profile.kind)) {
        TtsProviderKind.EDGE -> edgeProvider
        TtsProviderKind.FISH_AUDIO -> {
            val key = settings.readApiKey(resolved.profile.id)
            require(key.isNotBlank()) { "请先在设置中保存 Fish Audio API Key" }
            FishAudioProvider(
                FishAudioClient(
                    resolved.profile.baseUrl,
                    allowInsecureHttp = settings.isInsecureHttpAllowed(resolved.profile.id, resolved.profile.baseUrl)
                ),
                key
            )
        }
        TtsProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleTtsProvider(
            OpenAiTtsClient(
                resolved.profile.baseUrl,
                allowInsecureHttp = settings.isInsecureHttpAllowed(resolved.profile.id, resolved.profile.baseUrl)
            ),
            settings.readApiKey(resolved.profile.id)
        )
    }

    fun deleteAudio(chapterIds: Iterable<String>) {
        val root = File(context.filesDir, "tts")
        chapterIds.forEach { chapterId ->
            File(root, chapterId).deleteRecursively()
            File(root, "$chapterId.building").deleteRecursively()
            File(root, "$chapterId.backup").deleteRecursively()
            File(root, "$chapterId.cache").deleteRecursively()
        }
    }

    fun recoverAndCleanup(validChapterIds: Set<String>) {
        val root = File(context.filesDir, "tts")
        if (!root.exists()) return
        root.listFiles()?.filter(File::isDirectory)?.forEach { entry ->
            val chapterId = entry.name.removeSuffix(".building").removeSuffix(".backup").removeSuffix(".cache")
            if (chapterId !in validChapterIds) { entry.deleteRecursively(); return@forEach }
            when {
                entry.name.endsWith(".building") -> entry.deleteRecursively()
                entry.name.endsWith(".backup") -> {
                    val destination = File(root, chapterId)
                    if (destination.exists()) entry.deleteRecursively() else entry.renameTo(destination)
                }
            }
        }
    }

    private fun aliases(characters: List<StoryCharacterEntity>) = buildMap {
        characters.forEach { character ->
            put(character.canonicalName, character.canonicalName)
            val array = runCatching { JSONArray(character.aliasesJson) }.getOrNull() ?: JSONArray()
            for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let { putIfAbsent(it, character.canonicalName) }
        }
    }

    private fun splitText(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                val breakAt = (end downTo start + limit / 2).firstOrNull { text[it - 1] in "。！？!?；;" }
                if (breakAt != null) end = breakAt
            }
            text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(result::add)
            start = end
        }
        return result
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class SpeechJob(
        val index: Int,
        val blockIndex: Int,
        val chunkIndex: Int,
        val speaker: String,
        val text: String,
        val directives: TtsDirectives,
        val resolved: ResolvedTtsVoice,
        val renderedText: String,
        val cacheKey: String
    ) {
        fun toEntity(scriptId: String, status: TaskStatus, audioPath: String?, error: String?, now: Long) =
            TtsScriptSegmentEntity(
                id = "$scriptId-$index",
                scriptId = scriptId,
                segmentIndex = index,
                blockIndex = blockIndex,
                speaker = speaker,
                rawText = text,
                directivesJson = directives.toJson(),
                profileId = resolved.profile.id,
                model = resolved.profile.model,
                voiceId = resolved.voiceId,
                renderedText = renderedText,
                cacheKey = cacheKey,
                status = status.name,
                audioPath = audioPath,
                error = error,
                updatedAt = now
            )

        fun manifest(path: File) = JSONObject()
            .put("index", index)
            .put("blockIndex", blockIndex)
            .put("chunkIndex", chunkIndex)
            .put("speaker", speaker)
            .put("providerProfileId", resolved.profile.id)
            .put("provider", resolved.profile.kind)
            .put("model", resolved.profile.model)
            .put("voice", resolved.voiceId)
            .put("voiceName", resolved.voiceName)
            .put("rawText", text)
            .put("directedText", renderedText)
            .put("directives", JSONObject(directives.toJson()))
            .put("cacheKey", cacheKey)
            .put("generatedAt", System.currentTimeMillis())
            .put("path", path.absolutePath)
    }
}
