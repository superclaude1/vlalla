package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskRunType
import com.storybrain.app.data.TaskRunStatus
import com.storybrain.app.settings.LlmMessage
import com.storybrain.app.settings.LlmDomainException
import com.storybrain.app.settings.NetworkFailureClassifier
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.LlmProfileSnapshot
import com.storybrain.app.settings.OpenAiCompatibleClient
import com.storybrain.app.settings.ChatCompletionUsage
import com.storybrain.app.settings.UsageQuality
import java.nio.charset.StandardCharsets
import java.util.UUID

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LlmStoryAnalyzer(
    private val repository: StoryRepository,
    private val settings: LlmSettingsStore,
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient()
) {
    suspend fun analyzeNext(
        bookId: String,
        requestedChapterCount: Int? = null,
        onProgress: (AnalysisProgress) -> Unit = {}
    ): AnalysisRunResult {
        val profile = settings.snapshot()
        return analyzeNextWithProfile(profile, bookId, requestedChapterCount, onProgress)
    }

    private suspend fun analyzeNextWithProfile(
        profile: LlmProfileSnapshot,
        bookId: String,
        requestedChapterCount: Int? = null,
        onProgress: (AnalysisProgress) -> Unit = {}
    ): AnalysisRunResult {
        require(profile.modelId.isNotBlank()) { "请先在设置中检测并选择分析模型" }

        val book = repository.getBook(bookId) ?: error("找不到这本小说")
        val allChapters = repository.getChapters(bookId)
        val initializationTarget = minOf(15, allChapters.size)
        val targetCount = if (book.analysisCompleted < initializationTarget) {
            initializationTarget - book.analysisCompleted
        } else {
            (requestedChapterCount ?: 1).coerceAtLeast(1)
        }
        val target = allChapters.drop(book.analysisCompleted).take(targetCount)
        if (target.isEmpty()) return AnalysisRunResult(0, book.analysisCompleted, "全书已经分析完成")

        val runId = repository.startTaskRun(TaskRunType.ANALYSIS, bookId)
        var runningBatchIds = emptyList<String>()
        return runCatching {
            var completed = book.analysisCompleted
            var calls = 0
            var usage = AnalysisUsage()
            val batches = groupByCharacterBudget(target, 24_000)
            batches.forEachIndexed { batchIndex, batch ->
                currentCoroutineContext().ensureActive()
                runningBatchIds = batch.map { it.id }
                repository.updateAnalysisStatus(runningBatchIds, TaskStatus.RUNNING)
                val knownCharacters = repository.getCharacters(bookId)
                val knownNodes = repository.getPlotNodes(bookId)
                val messages = listOf(
                    LlmMessage("system", SYSTEM_PROMPT),
                    LlmMessage("user", buildUserPrompt(batch, knownCharacters, knownNodes))
                )
                val response = try {
                    client.chatCompletionResult(profile.baseUrl, profile.apiKey, profile.modelId, messages, temperature = 0.1, jsonMode = true)
                } catch (error: LlmDomainException) {
                    val unsupportedJsonMode = error.failure.statusCode == 400
                    if (!unsupportedJsonMode) throw error
                    client.chatCompletionResult(profile.baseUrl, profile.apiKey, profile.modelId, messages, temperature = 0.1, jsonMode = false)
                }
                usage += AnalysisUsage(response.usage)
                withContext(NonCancellable) {
                    repository.recordTaskUsage(runId, TaskRunType.ANALYSIS, bookId, batchIndex + 1, response.usage, response.requestId, response.responseModel)
                }
                val delta = parseDelta(bookId, response.content, knownCharacters, knownNodes, batch)
                completed = maxOf(completed, batch.maxOf { it.chapterIndex } + 1)
                repository.saveAnalysisDelta(bookId, completed, batch.map { it.id }, delta.characters, delta.relations, delta.nodes, delta.mentions)
                runningBatchIds = emptyList()
                calls++
                onProgress(AnalysisProgress(completed, usage))
            }
            repository.finishTaskRun(runId, TaskRunStatus.COMPLETED)
            AnalysisRunResult(target.size, completed, "已完成 ${target.size} 章 LLM 分析（$calls 次请求）", usage)
        }.getOrElse { error ->
            if (error is CancellationException) {
                if (runningBatchIds.isNotEmpty()) {
                    withContext(NonCancellable) {
                        repository.updateRunningAnalysisStatus(runningBatchIds, TaskStatus.CANCELLED)
                    }
                }
                withContext(NonCancellable) {
                    repository.finishTaskRun(runId, TaskRunStatus.CANCELLED)
                }
                throw error
            }
            if (runningBatchIds.isNotEmpty()) {
                withContext(NonCancellable) {
                    repository.updateRunningAnalysisStatus(runningBatchIds, TaskStatus.FAILED)
                }
            }
            val failure = (error as? LlmDomainException)?.failure
                ?: NetworkFailureClassifier.classifyMalformedJson(
                    requestId = null,
                    payloadBytes = 0
                )
            val batchIndex = target.let { chapters ->
                groupByCharacterBudget(chapters, 24_000).indexOfFirst { batch -> batch.any { it.id in runningBatchIds } }
            }.takeIf { it >= 0 }?.plus(1) ?: 1
            withContext(NonCancellable) {
                repository.recordTaskFailure(
                    runId, TaskRunType.ANALYSIS, bookId, "ERROR", failure.stage.name,
                    failure.retryable, failure.statusCode, failure.attempt, failure.message
                )
            }
            throw AnalysisFailureException(
                failure = failure,
                failedBatch = batchIndex,
                totalBatches = groupByCharacterBudget(target, 24_000).size,
                cause = error
            )
        }
    }

    suspend fun analyzeAll(bookId: String, onProgress: (AnalysisProgress) -> Unit = {}): AnalysisRunResult {
        val profile = settings.snapshot()
        return analyzeNextWithProfile(profile, bookId, Int.MAX_VALUE, onProgress)
    }

    private fun buildUserPrompt(
        chapters: List<ChapterEntity>,
        knownCharacters: List<StoryCharacterEntity>,
        knownNodes: List<PlotNodeEntity>
    ): String = buildString {
        appendLine("已知全局角色（用于别名去重）：")
        if (knownCharacters.isEmpty()) appendLine("[]") else appendLine(
            knownCharacters.joinToString(prefix = "[", postfix = "]") { "${it.canonicalName}:${it.aliasesJson}" }
        )
        appendLine("已知剧情节点：")
        appendLine(knownNodes.takeLast(20).joinToString { "${it.title}(第${it.startChapterIndex + 1}章)" })
        appendLine("\n请分析以下章节。章节编号必须使用给出的 chapterIndex，不要自行换算：")
        chapters.forEach { chapter ->
            appendLine("\n<chapter chapterIndex=\"${chapter.chapterIndex}\" title=\"${chapter.title}\">")
            appendLine(chapter.content)
            appendLine("</chapter>")
        }
    }

    private fun parseDelta(
        bookId: String,
        raw: String,
        existingCharacters: List<StoryCharacterEntity>,
        existingNodes: List<PlotNodeEntity>,
        chapters: List<ChapterEntity>
    ): AnalysisDelta {
        val jsonText = extractJsonObject(raw)
        val root = JSONObject(jsonText)
        val characters = root.optJSONArray("characters").objects().mapNotNull { item ->
            val name = item.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            val incomingAliases = item.optJSONArray("aliases").stringValues()
            val old = CharacterIdentityMatcher.find(existingCharacters, name, incomingAliases)
            val canonicalName = old?.canonicalName ?: name
            val mergedAliases = CharacterIdentityMatcher.mergedAliases(old, name, incomingAliases, canonicalName)
            StoryCharacterEntity(
                id = old?.id ?: stableId(bookId, "character", canonicalName),
                bookId = bookId,
                canonicalName = canonicalName,
                aliasesJson = JSONArray(mergedAliases).toString(),
                gender = item.optString("gender", old?.gender ?: "UNKNOWN"),
                personality = item.optString("personality", old?.personality.orEmpty()),
                voiceId = old?.voiceId,
                firstChapterIndex = minOf(old?.firstChapterIndex ?: Int.MAX_VALUE, item.optInt("firstChapterIndex", 0)),
                lastChapterIndex = maxOf(old?.lastChapterIndex ?: 0, item.optInt("lastChapterIndex", 0)),
                confidence = item.optDouble("confidence", old?.confidence?.toDouble() ?: 0.5).toFloat(),
                importanceScore = item.optDouble(
                    "importanceScore",
                    old?.importanceScore?.toDouble() ?: 0.0
                ).toFloat().coerceIn(0f, 1f),
                importanceReason = item.optString("importanceReason", old?.importanceReason.orEmpty()).trim()
            )
        }.distinctBy { it.id }
        val allCharacters = buildMap<String, StoryCharacterEntity> {
            (existingCharacters + characters).forEach { character ->
                CharacterIdentityMatcher.names(character).forEach { name -> put(name, character) }
            }
        }
        val relations = root.optJSONArray("relations").objects().mapNotNull { item ->
            val from = allCharacters[item.optString("from")] ?: return@mapNotNull null
            val to = allCharacters[item.optString("to")] ?: return@mapNotNull null
            val type = item.optString("type", "RELATED_TO")
            val start = item.optInt("startChapterIndex", 0)
            StoryRelationEntity(
                id = stableId(bookId, "relation", "${from.id}:${to.id}:$type:$start"),
                bookId = bookId,
                fromCharacterId = from.id,
                toCharacterId = to.id,
                relationType = type,
                strength = item.optDouble("strength", 0.5).toFloat().coerceIn(0f, 1f),
                startChapterIndex = start,
                endChapterIndex = item.optIntOrNull("endChapterIndex"),
                evidence = item.optString("evidence").take(160),
                confidence = item.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)
            )
        }
        val nodeItems = root.optJSONArray("plotNodes").objects()
        val newNodeIdsByTitle = nodeItems.mapNotNull { item ->
            val title = item.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val start = item.optInt("startChapterIndex", 0)
            title to stableId(bookId, "plot", "$title:$start")
        }.toMap()
        val nodeIdsByTitle = existingNodes.associate { it.title to it.id } + newNodeIdsByTitle
        val nodes = nodeItems.mapNotNull { item ->
            val title = item.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val start = item.optInt("startChapterIndex", 0)
            val parentTitles = item.optJSONArray("parentTitles") ?: JSONArray()
            val participantNames = item.optJSONArray("participants") ?: JSONArray()
            PlotNodeEntity(
                id = newNodeIdsByTitle.getValue(title),
                bookId = bookId,
                title = title,
                summary = item.optString("summary"),
                startChapterIndex = start,
                endChapterIndex = item.optIntOrNull("endChapterIndex"),
                parentIdsJson = JSONArray((0 until parentTitles.length()).map { index ->
                    nodeIdsByTitle[parentTitles.optString(index)]
                }.filterNotNull()).toString(),
                participantIdsJson = JSONArray((0 until participantNames.length()).mapNotNull { index ->
                    allCharacters[participantNames.optString(index)]?.id
                }.distinct()).toString(),
                locationName = item.optString("location").takeIf { it.isNotBlank() },
                confidence = item.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)
            )
        }
        val mentions = ChapterCharacterMentionParser.parse(
            bookId = bookId,
            raw = raw,
            characters = existingCharacters + characters,
            chapters = chapters,
            sourceHashByChapterIndex = chapters.associate { it.chapterIndex to sourceHash(it.content) },
            analysisVersion = ANALYSIS_VERSION
        )
        return AnalysisDelta(characters, relations, nodes, mentions)
    }

    private fun groupByCharacterBudget(chapters: List<ChapterEntity>, budget: Int): List<List<ChapterEntity>> {
        val groups = mutableListOf<MutableList<ChapterEntity>>()
        chapters.forEach { chapter ->
            val current = groups.lastOrNull()
            if (current == null || current.sumOf { it.content.length } + chapter.content.length > budget) {
                groups += mutableListOf(chapter)
            } else current += chapter
        }
        return groups
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONArray?.stringValues(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf(String::isNotBlank)
        }.distinct()
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun stableId(vararg parts: String): String = UUID.nameUUIDFromBytes(
        parts.joinToString(":").toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private data class AnalysisDelta(
        val characters: List<StoryCharacterEntity>,
        val relations: List<StoryRelationEntity>,
        val nodes: List<PlotNodeEntity>,
        val mentions: List<com.storybrain.app.data.ChapterCharacterMentionEntity>
    )

    companion object {
        private const val ANALYSIS_VERSION = 1
        private val SYSTEM_PROMPT = """
            你是“章境”小说知识图谱分析器。只依据提供的原文，不补写、不剧透。
            统一角色真名与别名；关系必须有方向、章节范围、原文证据和置信度。
            剧情允许分支与汇合。gender 只允许 MALE、FEMALE、UNKNOWN。
            仅输出一个合法 JSON 对象，不要 Markdown，不要解释。结构必须为：
            {
              "characters":[{"name":"标准名","aliases":["别名"],"gender":"UNKNOWN","personality":"简述","firstChapterIndex":0,"lastChapterIndex":0,"confidence":0.8,"importanceScore":0.8,"importanceReason":"主线参与度与叙事作用","chapterMentions":[{"chapterIndex":0,"evidence":"原文短证据","confidence":0.8}]}],
              "relations":[{"from":"标准名","to":"标准名","type":"PROTECTS|FRIEND|ENEMY|FAMILY|MENTOR|LOVES|ALLY|USES|BETRAYS|RELATED_TO","strength":0.8,"startChapterIndex":0,"endChapterIndex":null,"evidence":"原文短证据","confidence":0.8}],
              "plotNodes":[{"title":"事件名","summary":"摘要","startChapterIndex":0,"endChapterIndex":null,"parentTitles":[],"participants":["角色标准名"],"location":"地点或空字符串","confidence":0.8}]
            }
        """.trimIndent()
    }

    private fun sourceHash(content: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

}

data class AnalysisUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val quality: UsageQuality = UsageQuality.MISSING
) {
    constructor(usage: ChatCompletionUsage) : this(
        usage.promptTokens,
        usage.completionTokens,
        usage.totalTokens,
        usage.quality
    )

    operator fun plus(other: AnalysisUsage): AnalysisUsage = AnalysisUsage(
        promptTokens = promptTokens.addKnown(other.promptTokens),
        completionTokens = completionTokens.addKnown(other.completionTokens),
        totalTokens = totalTokens.addKnown(other.totalTokens),
        quality = quality.combine(other.quality)
    )
}

private fun Int?.addKnown(other: Int?): Int? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}

private fun UsageQuality.combine(other: UsageQuality): UsageQuality = when {
    this == UsageQuality.MISSING && other == UsageQuality.MISSING -> UsageQuality.MISSING
    this == UsageQuality.COMPLETE && other == UsageQuality.COMPLETE -> UsageQuality.COMPLETE
    else -> UsageQuality.PARTIAL
}

data class AnalysisProgress(
    val completed: Int,
    val usage: AnalysisUsage
)

data class AnalysisRunResult(
    val chapterCount: Int,
    val completed: Int,
    val message: String,
    val usage: AnalysisUsage = AnalysisUsage()
)

class AnalysisFailureException(
    val failure: com.storybrain.app.settings.NetworkFailure,
    val failedBatch: Int,
    val totalBatches: Int,
    cause: Throwable
) : Exception(failure.message, cause)
