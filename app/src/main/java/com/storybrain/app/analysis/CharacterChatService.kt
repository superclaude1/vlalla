package com.storybrain.app.analysis

import com.storybrain.app.data.ChatMessageEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.settings.LlmMessage
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.OpenAiCompatibleClient
import java.util.UUID
import kotlinx.coroutines.flow.first

class CharacterChatService(
    private val repository: StoryRepository,
    private val settings: LlmSettingsStore,
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient()
) {
    suspend fun send(bookId: String, characterId: String, sessionId: String, userText: String): String {
        val text = userText.trim()
        require(text.isNotBlank()) { "请输入要和角色说的话" }
        val config = settings.config.first()
        val apiKey = settings.readApiKey()
        require(config.model.isNotBlank()) { "请先在设置中检测并选择模型" }

        val book = repository.getBook(bookId) ?: error("找不到这本小说")
        val character = repository.getCharacters(bookId).firstOrNull { it.id == characterId }
            ?: error("找不到这个角色")
        val session = repository.getChatSession(sessionId) ?: error("找不到当前对话")
        require(session.bookId == bookId && session.characterId == characterId) { "对话与角色不匹配" }

        val now = System.currentTimeMillis()
        repository.insertChatMessage(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                characterId = characterId,
                sessionId = sessionId,
                role = "user",
                content = text,
                createdAt = now
            )
        )
        repository.titleSessionFromFirstMessage(sessionId, text)
        val selectedMemories = repository.getSelectedMemoryGroups(characterId, sessionId, book.analysisCompleted)
        val history = trimChatHistory(repository.getRecentChatMessages(sessionId), StoryRepository.MAX_HISTORY_CHARS)
        val prompt = CharacterPromptBuilder.build(
            character,
            book.title,
            book.analysisCompleted,
            selectedMemories.defaultMemories,
            selectedMemories.sessionMemories
        )
        val response = client.chatCompletion(
            baseUrl = config.baseUrl,
            apiKey = apiKey,
            model = config.model,
            messages = buildList {
                add(LlmMessage("system", prompt))
                history.forEach { message -> add(LlmMessage(message.role, message.content)) }
            },
            temperature = 0.7
        ).trim()
        require(response.isNotBlank()) { "角色没有返回内容，请重试" }
        repository.insertChatMessage(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                characterId = characterId,
                sessionId = sessionId,
                role = "assistant",
                content = response,
                createdAt = System.currentTimeMillis()
            )
        )
        return response
    }

}

internal fun trimChatHistory(messages: List<ChatMessageEntity>, maxChars: Int): List<ChatMessageEntity> {
    var used = 0
    val kept = mutableListOf<ChatMessageEntity>()
    for (message in messages.asReversed()) {
        if (kept.isNotEmpty() && used + message.content.length > maxChars) break
        kept += message
        used += message.content.length
    }
    return kept.asReversed()
}

internal object CharacterPromptBuilder {
    fun build(
        character: StoryCharacterEntity,
        bookTitle: String,
        analysisCompleted: Int,
        defaultMemories: List<MemoryItemEntity>,
        sessionMemories: List<MemoryItemEntity>
    ): String = buildString {
        appendLine("你正在扮演小说《$bookTitle》中的角色“${character.canonicalName}”。")
        appendLine("性别：${character.gender}；性格：${character.personality.ifBlank { "以原文表现为准" }}。")
        character.aliasesJson.trim().takeUnless { it.isBlank() || it == "[]" }
            ?.let { aliases -> appendLine("别名：$aliases。") }
        appendLine("当前只允许使用已经分析完成的前 $analysisCompleted 章信息，禁止透露之后剧情，禁止编造未出现的设定。")
        appendLine("以下仅包含用户明确选择、允许本次对话使用的记忆；推荐但未选择的记忆不得使用：")
        appendMemoryGroup("default-memories", defaultMemories)
        appendMemoryGroup("session-memories", sessionMemories)
        appendLine("始终以该角色第一人称自然简短地回答；可以表达角色立场和情绪，但不要声称自己是AI，不要输出舞台说明、JSON或记忆标签。")
    }

    private fun StringBuilder.appendMemoryGroup(tag: String, memories: List<MemoryItemEntity>) {
        appendLine("<$tag>")
        if (memories.isEmpty()) appendLine("无")
        memories.forEach { memory ->
            val chapter = when {
                memory.chapterStartIndex == null -> "无章节"
                memory.chapterEndIndex == null || memory.chapterEndIndex == memory.chapterStartIndex ->
                    "第${memory.chapterStartIndex + 1}章"
                else -> "第${memory.chapterStartIndex + 1}-${memory.chapterEndIndex + 1}章"
            }
            appendLine("<memory id=\"${memory.id}\" type=\"${memory.type}\" chapter=\"$chapter\">")
            appendLine("${memory.title}：${memory.content}")
            appendLine("</memory>")
        }
        appendLine("</$tag>")
    }
}
