package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionReachabilityTest {
    private val ui = File("src/main/java/com/storybrain/app/ui")
    private val screens = ui.resolve("Screens.kt").readText()
    private val memories = ui.resolve("MemoryScreens.kt").readText()
    private val viewModel = ui.resolve("AppViewModel.kt").readText()
    private val repository = File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()

    @Test
    fun sessionCreateSwitchRenameClearAndDeleteReachRealActionsWithFailureFeedback() {
        assertTrue(screens.contains("onSelect = { currentSessionId = it; sessionMenuExpanded = false }"))
        assertTrue(screens.contains("viewModel.createChatSession(bookId, characterId)"))
        assertTrue(screens.contains("viewModel.renameChatSession(currentSessionId, characterId, title)"))
        assertTrue(screens.contains("viewModel.clearCharacterChat(currentSessionId, characterId)"))
        assertTrue(screens.contains("viewModel.deleteChatSession(currentSessionId, bookId, characterId)"))
        assertTrue(screens.contains("state.error?.takeIf { state.characterId == characterId }"))

        assertActionReportsFailure("fun renameChatSession(", "fun deleteChatSession(", "重命名对话失败")
        assertActionReportsFailure("fun deleteChatSession(", "fun sendCharacterMessage(", "删除对话失败")
        assertActionReportsFailure("fun clearCharacterChat(", "fun loadMemoryPicker(", "清空对话失败")
    }

    @Test
    fun defaultMemoryNoticeSuggestionsSearchTypeCharacterChapterBudgetsAndSpoilerLockAreReachable() {
        assertTrue(screens.contains("showDefaultMemoryNotice = seededNow"))
        assertTrue(screens.contains("已建立角色默认记忆"))
        assertTrue(screens.contains("showMemoryPicker = true"))
        assertTrue(memories.contains("根据当前输入推荐（不会自动使用）"))
        assertTrue(memories.contains("label = { Text(\"本地搜索记忆\") }"))
        assertTrue(memories.contains("MemoryTypeFilters(type)"))
        assertTrue(memories.contains("label = { Text(\"仅当前角色\") }"))
        assertTrue(memories.contains("label = { Text(\"截至章节（空为全部已分析章节）\") }"))
        assertTrue(memories.contains("StoryRepository.MAX_SELECTED_MEMORIES"))
        assertTrue(memories.contains("StoryRepository.MAX_MEMORY_CHARS"))
        assertTrue(memories.contains("Text(\"默认\""))
        assertTrue(memories.contains("Text(\"本次\""))
        assertTrue(memories.contains("enabled = !memory.isLocked"))
        assertTrue(memories.contains("enabled = !memory.isDefault && !memory.isLocked"))
        assertTrue(memories.contains("尚未分析到该章节，记忆已锁定且不会发送给模型"))
    }

    @Test
    fun messageCopySaveAndManualMemoryCrudRemainReachableWhileAutomaticMemoryIsReadOnly() {
        assertTrue(screens.contains("clipboard.setText(AnnotatedString(message.content))"))
        assertTrue(screens.contains("Text(\"存为记忆\")"))
        assertTrue(screens.contains("viewModel.saveNewMemory(bookId, MemoryType.CHAT"))
        assertTrue(memories.contains("title = \"新建手工记忆\""))
        assertTrue(memories.contains("viewModel.saveNewMemory(bookId, MemoryType.NOTE"))
        assertTrue(memories.contains("viewModel.updateMemory(memory.copy("))
        assertTrue(memories.contains("onDelete = if (memory.editable)"))
        assertTrue(memories.contains("if (memory.editable) editing = memory"))
        assertTrue(repository.contains("require(memory.editable) { \"自动分析记忆不能直接修改\" }"))
        assertTrue(repository.contains("require(memory.editable) { \"自动分析记忆不能删除\" }"))
    }

    @Test
    fun staleMemoryPickerResponseCannotOverwriteAnotherSession() {
        val latest = MemoryPickerUiState(
            bookId = "book",
            characterId = "character",
            sessionId = "session-new",
            query = "new query",
            requestId = 2L,
            loading = true
        )

        assertTrue(MemoryPickerRequestPolicy.matches(latest, "book", "character", "session-new", "new query", 2L))
        assertFalse(MemoryPickerRequestPolicy.matches(latest, "book", "character", "session-old", "old query", 1L))
        assertFalse(MemoryPickerRequestPolicy.matches(latest, "book", "character", "session-new", "old query", 1L))
        assertTrue(viewModel.contains("if (!MemoryPickerRequestPolicy.matches("))
        assertTrue(viewModel.contains("items = emptyList()"))
        assertTrue(viewModel.contains("_memoryPickerState.value.requestId != state.requestId"))
        assertTrue(memories.contains("val stateMatches = state.bookId == bookId"))
        assertTrue(memories.contains("if (!state.loading) viewModel.setMemorySelected"))
        assertTrue(viewModel.contains("error = it.message ?: \"创建对话失败\""))
    }

    private fun assertActionReportsFailure(start: String, end: String, fallback: String) {
        val body = viewModel.substringAfter(start).substringBefore(end)
        assertTrue(body.contains("runCatching"))
        assertTrue(body.contains("onFailure"))
        assertTrue(body.contains(fallback))
        assertTrue(body.contains("CharacterChatUiState(characterId = characterId"))
    }
}
