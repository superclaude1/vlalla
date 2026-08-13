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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TtsGenerationResult(val manifestPath: String, val segmentCount: Int)
data class TtsDeletionStage(val directory: File, val entries: List<Pair<File, File>>)

class ChapterTtsEngine(
    private val context: Context,
    private val repository: StoryRepository,
    private val settings: TtsSettingsStore = TtsSettingsStore(context),
    private val llmSettings: LlmSettingsStore = LlmSettingsStore(context, repository),
    private val edgeProvider: TtsProvider = EdgeTtsProvider(),
    private val androidSystemProvider: TtsProvider = AndroidSystemTtsProvider(context),
    private val directingService: TtsDirectingService = TtsDirectingService(llmSettings),
    private val resolver: VoiceResolver = VoiceResolver(repository, settings)
) {
    suspend fun generate(
        bookId: String,
        chapterId: String,
        onProgress: (completed: Int, total: Int) -> Unit,
        onStage: (String) -> Unit = {}
    ): TtsGenerationResult {
        repository.ensureDefaultTtsProfiles()
        val chapter = repository.getChapter(chapterId) ?: error("找不到本章")
        val characters = repository.getCharacters(bookId)
        val aliases = aliases(characters)
        val characterByName = characters.associateBy { it.canonicalName }
        val blocks = TextToChatParser.parse(chapter.content, aliases)
        require(blocks.isNotEmpty()) { "本章没有可配音内容" }
        val sourceHash = sha256(chapter.content)
        val llmConfig = llmSettings.snapshot()
        val llmModelIdentity = ttsCacheIdentity(llmConfig.apiProfileId, llmConfig.baseUrl, llmConfig.modelId)
        val scriptId = "tts-$chapterId"
        val previousScript = repository.getTtsScript(chapterId)
        val previousSegments = previousScript?.takeIf {
            it.sourceHash == sourceHash && it.llmModel == llmModelIdentity && it.promptVersion == TtsDirectingService.PROMPT_VERSION
        }?.let { repository.getTtsScriptSegments(it.id) }.orEmpty()
        val cachedDirectives = previousSegments.groupBy { it.blockIndex }
            .mapValues { (_, values) -> directivesFromJson(values.first().directivesJson) }
        val directions = if (cachedDirectives.keys.containsAll(blocks.indices.toList())) {
            blocks.indices.associateWith { cachedDirectives.getValue(it) }
        } else {
            onStage("正在调用 LLM 生成演绎脚本…")
            directingService.direct(blocks, llmConfig) { completed, total -> onStage("正在生成演绎脚本 $completed/$total") }
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
                TtsProviderKind.ANDROID_SYSTEM -> 700
            })
            chunks.forEachIndexed { chunkIndex, text ->
                val directives = directions[blockIndex] ?: LocalTtsDirector.direct(blocks, blockIndex)
                val rendered = when (kind) {
                    TtsProviderKind.FISH_AUDIO -> TtsDirectiveRenderer.fishText(text, directives)
                    else -> text
                }
                val providerIdentity = ttsCacheIdentity(resolved.profile.id, resolved.profile.baseUrl, resolved.profile.model)
                val cacheKey = sha256("$providerIdentity|${resolved.voiceId}|$rendered|${directives.toJson()}|v1")
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
                llmModel = llmModelIdentity,
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
                currentCoroutineContext().ensureActive()
                onStage("正在使用 ${job.resolved.profile.displayName} 生成 ${index + 1}/${jobs.size} 段")
                val cachedArtifact = findCachedArtifact(cache, job.cacheKey)
                val artifact = cachedArtifact ?: synthesizeWithRetry(job, File(cache, "${job.cacheKey}.audio"))
                val normalizedArtifact = normalizeCachedArtifact(artifact, job.cacheKey, cache)
                val fileName = "%04d.${normalizedArtifact.fileExtension()}".format(index)
                val stagingFile = File(staging, fileName)
                normalizedArtifact.file.copyTo(stagingFile, overwrite = true)
                val row = job.toEntity(
                    scriptId, TaskStatus.COMPLETED,
                    File(directory, fileName).absolutePath, null, System.currentTimeMillis()
                )
                repository.updateTtsScriptSegments(listOf(row))
                manifestSegments.put(job.manifest(File(directory, fileName), normalizedArtifact))
                onProgress(index + 1, jobs.size)
            }
            File(staging, "manifest.json").writeText(
                JSONObject()
                    .put("bookId", bookId)
                    .put("chapterId", chapterId)
                    .put("sourceHash", sourceHash)
                    .put("llmModel", llmModelIdentity)
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
                TtsScriptEntity(scriptId, bookId, chapterId, sourceHash, llmModelIdentity, TtsDirectingService.PROMPT_VERSION, TaskStatus.COMPLETED.name, previousScript?.createdAt ?: now, System.currentTimeMillis()),
                jobs.mapIndexed { index, job -> job.toEntity(scriptId, TaskStatus.COMPLETED, manifestSegments.getJSONObject(index).getString("path"), null, System.currentTimeMillis()) }
            )
            TtsGenerationResult(manifest.absolutePath, jobs.size)
        }.getOrElse { cause ->
            withContext(NonCancellable) {
                staging.deleteRecursively()
                if (!directory.exists() && backup.exists()) backup.renameTo(directory)
                val cancelled = cause is CancellationException
                repository.updateTtsResult(
                    chapterId,
                    if (previousManifest != null) TaskStatus.COMPLETED else if (cancelled) TaskStatus.PENDING else TaskStatus.FAILED,
                    previousManifest
                )
                val failedIndex = jobs.indexOfFirst { findCachedArtifact(cache, it.cacheKey) == null }.coerceAtLeast(0)
                repository.updateTtsScriptSegments(
                    listOf(jobs[failedIndex].toEntity(
                        scriptId,
                        if (cancelled) TaskStatus.PENDING else TaskStatus.FAILED,
                        null,
                        if (cancelled) null else cause.message,
                        System.currentTimeMillis()
                    ))
                )
            }
            throw cause
        }
    }

    private suspend fun synthesizeWithRetry(job: SpeechJob, output: File): TtsAudioArtifact {
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
            currentCoroutineContext().ensureActive()
            try {
                return synthesizeCancellably(provider, request, output)
            } catch (error: Throwable) {
                last = error
                val retryable = when (error) {
                    is TtsProviderException -> error.retryable
                    is EdgeTtsException -> error.retryable
                    else -> false
                }
                if (!retryable || attempt == 2) throw error
                delay(500L * (1 shl attempt))
            }
        }
        throw last ?: error("配音生成失败")
    }

    private fun provider(resolved: ResolvedTtsVoice): TtsProvider = when (TtsProviderKind.valueOf(resolved.profile.kind)) {
        TtsProviderKind.EDGE -> edgeProvider
        TtsProviderKind.ANDROID_SYSTEM -> androidSystemProvider
        TtsProviderKind.FISH_AUDIO -> {
            val key = settings.readApiKey(resolved.profile.id)
            require(key.isNotBlank()) { "请先在设置中保存 Fish Audio API Key" }
            FishAudioProvider(FishAudioClient(resolved.profile.baseUrl), key)
        }
        TtsProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleTtsProvider(
            OpenAiTtsClient(resolved.profile.baseUrl),
            settings.readApiKey(resolved.profile.id)
        )
    }

    private fun normalizeCachedArtifact(artifact: TtsAudioArtifact, cacheKey: String, cache: File): TtsAudioArtifact {
        val extension = artifact.fileExtension()
        val destination = File(cache, "$cacheKey.$extension")
        if (artifact.file.absolutePath != destination.absolutePath) {
            artifact.file.copyTo(destination, overwrite = true)
            artifact.file.delete()
        }
        return artifact.copy(file = destination)
    }

    private fun findCachedArtifact(cache: File, cacheKey: String): TtsAudioArtifact? =
        cache.listFiles()?.firstOrNull { it.name.startsWith("$cacheKey.") && it.length() > 0L }?.let { file ->
            val extension = file.extension.lowercase()
            val mime = when (extension) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg", "opus" -> "audio/ogg"
                "m4a", "mp4" -> "audio/mp4"
                else -> null
            }
            TtsAudioArtifact(file, mime, extension.takeIf(String::isNotBlank))
        }

    fun stageAudioDeletion(bookId: String, chapterIds: Iterable<String>): TtsDeletionStage {
        val root = File(context.filesDir, "tts")
        val trash = File(root, ".trash-$bookId-${System.currentTimeMillis()}")
        check(!trash.exists() || trash.deleteRecursively()) { "无法准备配音回收区" }
        trash.mkdirs()
        val moved = mutableListOf<Pair<File, File>>()
        try {
            chapterIds.forEach { chapterId ->
                listOf(
                    File(root, chapterId),
                    File(root, "$chapterId.building"),
                    File(root, "$chapterId.backup"),
                    File(root, "$chapterId.cache")
                ).forEach { entry ->
                    if (entry.exists()) {
                        val staged = File(trash, entry.name)
                        check(entry.renameTo(staged)) { "无法暂存本地配音：${entry.name}" }
                        moved += entry to staged
                    }
                }
            }
        } catch (error: Throwable) {
            restoreAudioDeletion(TtsDeletionStage(trash, moved))
            throw error
        }
        return TtsDeletionStage(trash, moved)
    }

    fun commitAudioDeletion(stage: TtsDeletionStage) {
        // Database deletion has committed; leftovers are harmless trash and are retried at startup.
        stage.directory.deleteRecursively()
    }

    fun restoreAudioDeletion(stage: TtsDeletionStage) {
        stage.entries.asReversed().forEach { (original, staged) ->
            if (staged.exists()) {
                original.parentFile?.mkdirs()
                check(staged.renameTo(original)) { "无法恢复本地配音：${original.name}" }
            }
        }
        stage.directory.deleteRecursively()
    }

    fun recoverAndCleanup(validChapterIds: Set<String>) {
        val root = File(context.filesDir, "tts")
        if (!root.exists()) return
        root.listFiles()?.filter(File::isDirectory)?.forEach { entry ->
            if (entry.name.startsWith(".trash-")) {
                entry.listFiles()?.forEach { staged ->
                    val chapterId = staged.name.removeSuffix(".building").removeSuffix(".backup").removeSuffix(".cache")
                    val destination = File(root, staged.name)
                    if (chapterId in validChapterIds && !destination.exists()) staged.renameTo(destination)
                    else staged.deleteRecursively()
                }
                entry.deleteRecursively()
                return@forEach
            }
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

        fun manifest(path: File, artifact: TtsAudioArtifact) = JSONObject()
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
            .put("mimeType", artifact.mimeType)
            .put("format", artifact.format)
    }
}
