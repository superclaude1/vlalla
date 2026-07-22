package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.settings.LlmMessage
import com.storybrain.app.settings.LlmConnectionException
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.OpenAiCompatibleClient
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.flow.first
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
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): AnalysisRunResult {
        val config = settings.config.first()
        val apiKey = settings.readApiKey()
        require(config.model.isNotBlank()) { "请先在设置中检测并选择分析模型" }

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

        var runningBatchIds = emptyList<String>()
        return runCatching {
            var completed = book.analysisCompleted
            var calls = 0
            var processed = 0
            groupByCharacterBudget(target, 24_000).forEach { batch ->
                runningBatchIds = batch.map { it.id }
                repository.updateAnalysisStatus(runningBatchIds, TaskStatus.RUNNING)
                val knownCharacters = repository.getCharacters(bookId)
                val knownNodes = repository.getPlotNodes(bookId)
                val messages = listOf(
                    LlmMessage("system", SYSTEM_PROMPT),
                    LlmMessage("user", buildUserPrompt(batch, knownCharacters, knownNodes))
                )
                val response = try {
                    client.chatCompletion(
                        config.baseUrl, apiKey, config.model, messages,
                        temperature = 0.1,
                        jsonMode = true,
                        allowInsecureHttp = config.allowInsecureHttp
                    )
                } catch (error: LlmConnectionException) {
                    val unsupportedJsonMode = error.statusCode == 400 &&
                        error.message.orEmpty().contains(Regex("response[_ -]?format|json", RegexOption.IGNORE_CASE))
                    if (!unsupportedJsonMode) throw error
                    client.chatCompletion(
                        config.baseUrl, apiKey, config.model, messages,
                        temperature = 0.1,
                        jsonMode = false,
                        allowInsecureHttp = config.allowInsecureHttp
                    )
                }
                val delta = parseDelta(bookId, response, knownCharacters, knownNodes)
                completed = maxOf(completed, batch.maxOf { it.chapterIndex } + 1)
                repository.saveAnalysisDelta(
                    bookId,
                    completed,
                    batch.map { it.id },
                    delta.characters,
                    delta.relations,
                    delta.nodes
                )
                runningBatchIds = emptyList()
                calls++
                processed += batch.size
                onProgress(processed, target.size)
            }
            AnalysisRunResult(target.size, completed, "已完成 ${target.size} 章 LLM 分析（$calls 次请求）")
        }.getOrElse { error ->
            if (runningBatchIds.isNotEmpty()) {
                repository.updateAnalysisStatus(runningBatchIds, TaskStatus.FAILED)
            }
            throw error
        }
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
        existingNodes: List<PlotNodeEntity>
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
        return AnalysisDelta(characters, relations, nodes)
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
        val nodes: List<PlotNodeEntity>
    )

    companion object {
        private val SYSTEM_PROMPT = """
            你是“章境”小说知识图谱分析器。只依据提供的原文，不补写、不剧透。
            统一角色真名与别名；关系必须有方向、章节范围、原文证据和置信度。
            剧情允许分支与汇合。gender 只允许 MALE、FEMALE、UNKNOWN。
            仅输出一个合法 JSON 对象，不要 Markdown，不要解释。结构必须为：
            {
              "characters":[{"name":"标准名","aliases":["别名"],"gender":"UNKNOWN","personality":"简述","firstChapterIndex":0,"lastChapterIndex":0,"confidence":0.8,"importanceScore":0.8,"importanceReason":"主线参与度与叙事作用"}],
              "relations":[{"from":"标准名","to":"标准名","type":"PROTECTS|FRIEND|ENEMY|FAMILY|MENTOR|LOVES|ALLY|USES|BETRAYS|RELATED_TO","strength":0.8,"startChapterIndex":0,"endChapterIndex":null,"evidence":"原文短证据","confidence":0.8}],
              "plotNodes":[{"title":"事件名","summary":"摘要","startChapterIndex":0,"endChapterIndex":null,"parentTitles":[],"participants":["角色标准名"],"location":"地点或空字符串","confidence":0.8}]
            }
        """.trimIndent()
    }
}

data class AnalysisRunResult(val chapterCount: Int, val completed: Int, val message: String)
