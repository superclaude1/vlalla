package com.storybrain.app.tts

import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.settings.LlmMessage
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.OpenAiCompatibleClient
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class DirectedBlock(
    val segmentId: String,
    val directives: TtsDirectives,
    val usedFallback: Boolean
)

class TtsDirectingService(
    private val settings: LlmSettingsStore,
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient()
) {
    suspend fun direct(blocks: List<ReadingBlock>, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): List<DirectedBlock> {
        if (blocks.isEmpty()) return emptyList()
        val config = settings.config.first()
        if (config.model.isBlank() || settings.readApiKey().isBlank()) return localAll(blocks)
        val output = mutableListOf<DirectedBlock>()
        val batches = blocks.indices.chunked(BATCH_SIZE)
        batches.forEachIndexed { batchIndex, indices ->
            val byId = indices.associate { it.toString() to blocks[it] }
            val parsed = runCatching { requestBatch(config.baseUrl, settings.readApiKey(), config.model, byId, config.allowInsecureHttp) }
                .recoverCatching { requestBatch(config.baseUrl, settings.readApiKey(), config.model, byId, config.allowInsecureHttp, repair = true) }
                .getOrNull()
            indices.forEach { index ->
                output += DirectedBlock(
                    segmentId = index.toString(),
                    directives = parsed?.get(index.toString()) ?: LocalTtsDirector.direct(blocks, index),
                    usedFallback = parsed == null || index.toString() !in parsed
                )
            }
            onProgress(batchIndex + 1, batches.size)
        }
        return output.sortedBy { it.segmentId.toIntOrNull() ?: Int.MAX_VALUE }
    }

    private suspend fun requestBatch(
        baseUrl: String,
        apiKey: String,
        model: String,
        blocks: Map<String, ReadingBlock>,
        allowInsecureHttp: Boolean,
        repair: Boolean = false
    ): Map<String, TtsDirectives> {
        val input = JSONArray().apply {
            blocks.entries.forEach { (id, block) ->
                put(JSONObject()
                    .put("segmentId", id)
                    .put("speaker", (block as? ReadingBlock.Dialogue)?.speaker ?: "旁白")
                    .put("text", block.text))
            }
        }
        val system = """
            你是中文有声小说演绎导演。只为每个输入分段返回演绎参数，禁止改写、复述、删除或新增正文。
            返回严格JSON对象：{"segments":[{"segmentId":"0","emotion":"calm","delivery":"soft tone","pauseBeforeMs":0,"pauseAfterMs":300,"rate":1.0,"volume":0.0}]}。
            emotion仅可为 happy,sad,angry,excited,calm,nervous,surprised,moved,curious,scared,worried,hopeful,mysterious 或空字符串。
            delivery仅可为 soft tone,whispering,shouting,in a hurry tone 或空字符串。每句最多一个emotion和一个delivery。
            停顿0到2000毫秒，rate 0.7到1.4，volume -20到20。必须逐个返回全部segmentId。
        """.trimIndent()
        val user = if (repair) "上一次输出格式无效。请严格按指定JSON架构重新标注：$input" else "请标注这些分段：$input"
        val raw = client.chatCompletion(
            baseUrl, apiKey, model,
            listOf(LlmMessage("system", system), LlmMessage("user", user)),
            temperature = 0.15,
            jsonMode = true,
            allowInsecureHttp = allowInsecureHttp
        )
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "演绎标注不是JSON" }
        val array = JSONObject(raw.substring(start, end + 1)).getJSONArray("segments")
        val result = mutableMapOf<String, TtsDirectives>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("segmentId")
            require(id in blocks && id !in result) { "演绎分段ID无效" }
            result[id] = parseDirectives(item)
        }
        require(result.keys == blocks.keys) { "演绎标注缺少分段" }
        return result
    }

    private fun parseDirectives(item: JSONObject) = TtsDirectives(
        emotion = item.optString("emotion").lowercase().takeIf(ALLOWED_EMOTIONS::contains).orEmpty(),
        delivery = item.optString("delivery").lowercase().takeIf(ALLOWED_DELIVERIES::contains).orEmpty(),
        pauseBeforeMs = item.optInt("pauseBeforeMs").coerceIn(0, 2_000),
        pauseAfterMs = item.optInt("pauseAfterMs").coerceIn(0, 2_000),
        rate = item.optDouble("rate", 1.0).toFloat().coerceIn(0.7f, 1.4f),
        volume = item.optDouble("volume", 0.0).toFloat().coerceIn(-20f, 20f)
    )

    private fun localAll(blocks: List<ReadingBlock>) = blocks.indices.map { index ->
        DirectedBlock(index.toString(), LocalTtsDirector.direct(blocks, index), true)
    }

    companion object {
        const val PROMPT_VERSION = 1
        private const val BATCH_SIZE = 30
        private val ALLOWED_EMOTIONS = setOf("happy", "sad", "angry", "excited", "calm", "nervous", "surprised", "moved", "curious", "scared", "worried", "hopeful", "mysterious", "")
        private val ALLOWED_DELIVERIES = setOf("soft tone", "whispering", "shouting", "in a hurry tone", "")
    }
}

object LocalTtsDirector {
    fun direct(blocks: List<ReadingBlock>, index: Int): TtsDirectives {
        val text = buildString {
            if (index > 0) append(blocks[index - 1].text.takeLast(40))
            append(blocks[index].text)
            if (index + 1 < blocks.size) append(blocks[index + 1].text.take(40))
        }
        val emotion = when {
            text.containsAny("哭", "哽咽", "泪", "悲伤") -> "sad"
            text.containsAny("怒", "愤怒", "吼道") -> "angry"
            text.containsAny("害怕", "恐惧", "颤抖") -> "scared"
            text.containsAny("惊讶", "震惊", "竟然") -> "surprised"
            text.containsAny("感动", "动容") -> "moved"
            text.containsAny("笑", "开心", "欢快") -> "happy"
            text.containsAny("阴森", "神秘", "夜色", "黑暗") -> "mysterious"
            text.trimEnd().endsWith("？") || text.trimEnd().endsWith("?") -> "curious"
            text.trimEnd().endsWith("！") || text.trimEnd().endsWith("!") -> "excited"
            else -> "calm"
        }
        val delivery = when {
            text.containsAny("轻声", "柔声", "低声") -> "soft tone"
            text.containsAny("耳语", "悄声", "呢喃") -> "whispering"
            text.containsAny("大喊", "喊道", "吼道", "咆哮") -> "shouting"
            text.containsAny("急道", "急促", "飞快") -> "in a hurry tone"
            else -> ""
        }
        val own = blocks[index].text
        val pauseAfter = when {
            own.contains("……") || own.contains("...") -> 850
            own.lastOrNull()?.let { it in "。！？!?；;" } == true -> 280
            else -> 120
        }
        return TtsDirectives(emotion = emotion, delivery = delivery, pauseAfterMs = pauseAfter)
    }

    private fun String.containsAny(vararg values: String) = values.any(::contains)
}

fun TtsDirectives.toJson(): String = JSONObject()
    .put("emotion", emotion)
    .put("delivery", delivery)
    .put("pauseBeforeMs", pauseBeforeMs)
    .put("pauseAfterMs", pauseAfterMs)
    .put("rate", rate.toDouble())
    .put("volume", volume.toDouble())
    .toString()

fun directivesFromJson(raw: String): TtsDirectives = runCatching {
    val json = JSONObject(raw)
    TtsDirectives(
        json.optString("emotion"), json.optString("delivery"),
        json.optInt("pauseBeforeMs"), json.optInt("pauseAfterMs"),
        json.optDouble("rate", 1.0).toFloat(), json.optDouble("volume", 0.0).toFloat()
    )
}.getOrDefault(TtsDirectives())
