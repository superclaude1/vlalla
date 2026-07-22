package com.storybrain.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.storybrain.app.data.ChatMessageEntity
import com.storybrain.app.data.ChatSessionEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemoryWithSelection
import com.storybrain.app.ui.theme.StoryBrainTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun memorySelectionUsesExplicitDefaultAndSessionActions() {
        var sessionSelected = false
        val defaultMode = mutableStateOf(false)
        compose.setContent {
            StoryBrainTheme {
                MemorySelectionCard(
                    memory = memory(isDefault = defaultMode.value),
                    onDefaultChange = {},
                    onSessionChange = { sessionSelected = it }
                )
            }
        }
        compose.onNodeWithTag("memory-session-memory").performClick()
        compose.runOnIdle { assertTrue(sessionSelected) }
        compose.runOnIdle { defaultMode.value = true }
        compose.onNodeWithTag("memory-session-memory").assertIsNotEnabled()
    }

    @Test
    fun memoryTypeFilterReportsTheChosenType() {
        var chosen: String? = null
        compose.setContent {
            StoryBrainTheme { MemoryTypeFilters(chosen) { chosen = it } }
        }
        compose.onNodeWithTag("memory-filter-${MemoryType.NOTE.name}").performClick()
        compose.runOnIdle { assertEquals(MemoryType.NOTE.name, chosen) }
    }

    @Test
    fun sessionMenuSwitchesOnlyToChosenSession() {
        var selected = ""
        val sessions = listOf(session("first", "第一次"), session("second", "第二次"))
        compose.setContent {
            StoryBrainTheme {
                Column {
                    ChatSessionMenuContent(
                        sessions,
                        currentSessionId = "first",
                        onSelect = { selected = it },
                        onCreate = {}, onRename = {}, onClear = {}, onDelete = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("chat-session-second").performClick()
        compose.runOnIdle { assertEquals("second", selected) }
    }

    @Test
    fun longPressMessageOffersTheConfirmedMemoryFlow() {
        var opened = false
        val message = ChatMessageEntity("message", "book", "character", "session", "assistant", "记住这句话", 1)
        compose.setContent {
            StoryBrainTheme { CharacterChatBubble(message, "林清") { opened = true } }
        }
        compose.onNodeWithTag("chat-message-message").performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun memoryEditorRequiresContentAndErrorIsVisible() {
        var savedContent = ""
        val showError = mutableStateOf(false)
        compose.setContent {
            StoryBrainTheme {
                if (showError.value) {
                    ChatErrorMessage("记忆内容已达到上限")
                } else {
                    MemoryEditorDialog(
                        title = "保存对话记忆",
                        initialTitle = "标题",
                        initialContent = "",
                        onDismiss = {},
                        onSave = { _, content -> savedContent = content }
                    )
                }
            }
        }
        compose.onNodeWithTag("memory-editor-save").assertIsNotEnabled()
        compose.onNodeWithTag("memory-editor-content").performTextInput("已确认的对话记忆")
        compose.onNodeWithTag("memory-editor-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("已确认的对话记忆", savedContent)
            showError.value = true
        }
        compose.onNodeWithTag("chat-error").assertIsDisplayed()
    }

    private fun memory(isDefault: Boolean = false) = MemoryWithSelection(
        id = "memory",
        bookId = "book",
        type = MemoryType.PLOT.name,
        title = "初次相遇",
        content = "林清在渡口遇到主角。",
        chapterStartIndex = 1,
        chapterEndIndex = 1,
        characterIdsJson = "[\"character\"]",
        sourceKey = "plot:one",
        searchTerms = "初次 相遇",
        editable = false,
        createdAt = 1,
        updatedAt = 1,
        isDefault = isDefault,
        isSession = false,
        isLocked = false
    )

    private fun session(id: String, title: String) = ChatSessionEntity(
        id = id,
        bookId = "book",
        characterId = "character",
        title = title,
        createdAt = 1,
        updatedAt = 1
    )
}
