package com.storybrain.app.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.ChatMessageEntity
import com.storybrain.app.data.CharacterMemoryDefaultEntity
import com.storybrain.app.data.CharacterMemoryEvidenceEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemorySearch
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.SessionMemoryLinkEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.settings.LlmSettingsStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterChatServiceIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun requestContainsOnlySelectedInBoundaryMemoriesAndCurrentSessionHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = StoryRepository(database)
        val dao = database.storyDao()
        dao.insertBook(BookEntity("book", "测试小说", "test.txt", 1, 10, 1_000, currentChapterIndex = 4, analysisCompleted = 5))
        dao.insertCharacters(
            listOf(
                StoryCharacterEntity(
                    id = "character",
                    bookId = "book",
                    canonicalName = "林清",
                    gender = "女",
                    personality = "冷静而谨慎",
                    firstChapterIndex = 0,
                    lastChapterIndex = 4
                )
            )
        )
        val current = repository.createSession("book", "character")
        val other = repository.createSession("book", "character")
        val defaultMemory = repository.createMemory(
            "book", MemoryType.PLOT, "渡口相遇", "DEFAULT_SELECTED_CONTENT",
            chapterStartIndex = 1, chapterEndIndex = 2, characterIds = listOf("character")
        )
        val sessionMemory = repository.createMemory(
            "book", MemoryType.NOTE, "本次约定", "SESSION_SELECTED_CONTENT",
            chapterStartIndex = 3, characterIds = listOf("character")
        )
        val unselected = repository.createMemory(
            "book", MemoryType.NOTE, "推荐但未选择", "UNSELECTED_RECOMMENDED_SECRET",
            chapterStartIndex = 2, characterIds = listOf("character")
        )
        val locked = repository.createMemory(
            "book", MemoryType.PLOT, "未来剧情", "LOCKED_SPOILER_CONTENT",
            chapterStartIndex = 5, chapterEndIndex = 5, characterIds = listOf("character")
        )
        repository.setDefaultMemory("character", defaultMemory.id, true)
        repository.setSessionMemory("character", current.id, sessionMemory.id, true)
        assertTrue(
            runCatching { repository.setSessionMemory("character", current.id, locked.id, true) }
                .exceptionOrNull()?.message?.contains("已锁定") == true
        )
        // Simulate a stale/corrupted link and verify the send boundary still excludes it.
        dao.insertSessionMemory(SessionMemoryLinkEntity(current.id, locked.id, 1))
        repository.insertChatMessage(
            ChatMessageEntity(UUID.randomUUID().toString(), "book", "character", current.id, "user", "CURRENT_SESSION_HISTORY", 2)
        )
        repository.insertChatMessage(
            ChatMessageEntity(UUID.randomUUID().toString(), "book", "character", other.id, "user", "OTHER_SESSION_MUST_NOT_LEAK", 3)
        )

        val currentGroups = repository.getSelectedMemoryGroups("character", current.id, 5)
        val otherGroups = repository.getSelectedMemoryGroups("character", other.id, 5)
        assertTrue(defaultMemory.id in currentGroups.defaultMemories.map { it.id })
        assertTrue(sessionMemory.id in currentGroups.sessionMemories.map { it.id })
        assertTrue(defaultMemory.id in otherGroups.defaultMemories.map { it.id })
        assertFalse(sessionMemory.id in otherGroups.sessionMemories.map { it.id })
        assertFalse(locked.id in currentGroups.sessionMemories.map { it.id })
        assertFalse(unselected.id in currentGroups.defaultMemories.map { it.id })

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"我只记得你允许我记得的事。"}}]}"""
                )
        )
        val settings = LlmSettingsStore(context, repository)
        settings.save(server.url("/v1").toString(), "mock-model", "test-key")
        val reply = CharacterChatService(repository, settings).send(
            "book", "character", current.id, "请结合推荐关键词回答"
        )
        assertEquals("我只记得你允许我记得的事。", reply)

        val body = server.takeRequest().body.readUtf8()
        val messages = JSONObject(body).getJSONArray("messages")
        val systemPrompt = messages.getJSONObject(0).getString("content")
        val allRequestText = (0 until messages.length()).joinToString("\n") {
            messages.getJSONObject(it).getString("content")
        }
        assertTrue(systemPrompt.contains("<default-memories>"))
        assertTrue(systemPrompt.contains("DEFAULT_SELECTED_CONTENT"))
        assertTrue(systemPrompt.contains("chapter=\"第2-3章\""))
        assertTrue(systemPrompt.contains("<session-memories>"))
        assertTrue(systemPrompt.contains("SESSION_SELECTED_CONTENT"))
        assertFalse(systemPrompt.contains("UNSELECTED_RECOMMENDED_SECRET"))
        assertFalse(systemPrompt.contains("LOCKED_SPOILER_CONTENT"))
        assertTrue(allRequestText.contains("CURRENT_SESSION_HISTORY"))
        assertFalse(allRequestText.contains("OTHER_SESSION_MUST_NOT_LEAK"))
        assertEquals("请结合推荐关键词回答".take(18), repository.getChatSession(current.id)?.title)
    }

    @Test
    fun analysisMemorySyncIsIdempotentAndChineseIndexTracksEdits() = runBlocking {
        val repository = StoryRepository(database)
        val dao = database.storyDao()
        val character = StoryCharacterEntity(
            id = "character",
            bookId = "book",
            canonicalName = "林清",
            aliasesJson = "[\"清清\"]",
            firstChapterIndex = 0,
            lastChapterIndex = 2
        )
        val relation = StoryRelationEntity(
            id = "relation",
            bookId = "book",
            fromCharacterId = "character",
            toCharacterId = "character",
            relationType = "PROTECTS",
            strength = 1f,
            startChapterIndex = 0,
            evidence = "林清保护自己"
        )
        val plot = PlotNodeEntity(
            id = "plot",
            bookId = "book",
            title = "渡口重逢",
            summary = "清清在渡口找到了旧友。",
            startChapterIndex = 1,
            endChapterIndex = 2
        )
        dao.insertBook(BookEntity("book", "索引测试", "test.txt", 1, 3, 300))
        repeat(2) {
            repository.saveAnalysisDelta("book", 3, emptyList(), listOf(character), listOf(relation), listOf(plot))
        }

        val automaticMemories = dao.getMemories("book")
        assertEquals(2, automaticMemories.size)
        assertTrue("character" in MemorySearch.jsonStrings(repository.getPlotNodes("book").single().participantIdsJson))
        assertTrue("character" in MemorySearch.jsonStrings(automaticMemories.first { it.type == MemoryType.PLOT.name }.characterIdsJson))

        val session = repository.createSession("book", "character")
        val note = repository.createMemory("book", MemoryType.NOTE, "月光线索", "月光照在旧地图上", chapterStartIndex = 1)
        assertTrue(repository.memoriesWithSelection("book", "character", session.id, 3, "月光").any { it.id == note.id })
        repository.updateMemory(note.copy(title = "星火秘密", content = "星火落在新地图上"))
        assertFalse(repository.memoriesWithSelection("book", "character", session.id, 3, "月光").any { it.id == note.id })
        assertTrue(repository.memoriesWithSelection("book", "character", session.id, 3, "星火").any { it.id == note.id })
        repository.deleteMemory(note.id)
        assertFalse(repository.memoriesWithSelection("book", "character", session.id, 3, "星火").any { it.id == note.id })
    }

    @Test
    fun characterChatMemoryFilterUsesReadingProgressAndKeepsUserMemoryTraceable() = runBlocking {
        val repository = StoryRepository(database)
        val dao = database.storyDao()
        dao.insertBook(BookEntity("book", "阅读进度测试", "test.txt", 1, 10, 1_000, currentChapterIndex = 1, analysisCompleted = 5))
        dao.insertCharacters(
            listOf(StoryCharacterEntity("character", "book", "林清", firstChapterIndex = 0, lastChapterIndex = 9))
        )
        val session = repository.createSession("book", "character")
        val visible = repository.createMemory(
            "book", MemoryType.NOTE, "已读记忆", "VISIBLE_MEMORY", chapterStartIndex = 1, characterIds = listOf("character")
        )
        val future = repository.createMemory(
            "book", MemoryType.NOTE, "未来记忆", "FUTURE_MEMORY", chapterStartIndex = 2, characterIds = listOf("character")
        )
        repository.setDefaultMemory("character", visible.id, true)
        // Simulate an already-selected legacy/stale link; the chat boundary must still filter it.
        dao.insertDefaultMemory(CharacterMemoryDefaultEntity("character", future.id, 1))
        repository.saveCharacterMemoryEvidence(
            CharacterMemoryEvidenceEntity(
                memoryId = future.id,
                characterId = "character",
                chapterStartIndex = 2,
                chapterEndIndex = 2,
                characterIdsJson = "[\"character\"]",
                source = "USER",
                confidence = 1f,
                spoilerBoundaryChapterIndex = 2
            )
        )

        val groups = repository.getCharacterMemoriesForChat("book", "character", session.id, 1)

        assertTrue(groups.defaultMemories.any { it.id == visible.id })
        assertFalse(groups.defaultMemories.any { it.id == future.id })
        val evidence = dao.getCharacterMemoryEvidence(visible.id, "character")
        assertEquals("USER", evidence?.source)
        assertEquals(1, evidence?.spoilerBoundaryChapterIndex)
    }
}
