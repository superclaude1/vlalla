package com.storybrain.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("""
        SELECT b.*,
            COALESCE(SUM(CASE WHEN c.ttsStatus = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS ttsCompleted,
            COALESCE(SUM(CASE WHEN c.analysisStatus IN ('QUEUED', 'RUNNING') THEN 1 ELSE 0 END), 0) AS activeAnalysisTasks,
            COALESCE(SUM(CASE WHEN c.ttsStatus IN ('QUEUED', 'RUNNING') THEN 1 ELSE 0 END), 0) AS activeTtsTasks
        FROM books b
        LEFT JOIN chapters c ON c.bookId = b.id
        GROUP BY b.id
        ORDER BY b.importedAt DESC
    """)
    fun observeLibraryItems(): Flow<List<BookLibraryItem>>

    @Query("SELECT * FROM books ORDER BY importedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeBook(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    fun observeChapters(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    fun observeChapter(chapterId: String): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapter(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY importedAt DESC")
    suspend fun getBooks(): List<BookEntity>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query("SELECT id FROM chapters")
    suspend fun getAllChapterIds(): List<String>

    @Query("SELECT id, bookId FROM chapters WHERE analysisStatus IN ('QUEUED', 'RUNNING')")
    suspend fun getActiveAnalysisTasks(): List<ChapterTaskRef>

    @Query("SELECT id, bookId FROM chapters WHERE ttsStatus IN ('QUEUED', 'RUNNING')")
    suspend fun getActiveTtsTasks(): List<ChapterTaskRef>

    @Query("SELECT * FROM characters WHERE bookId = :bookId ORDER BY firstChapterIndex")
    suspend fun getCharacters(bookId: String): List<StoryCharacterEntity>

    @Query("SELECT * FROM plot_nodes WHERE bookId = :bookId ORDER BY startChapterIndex")
    suspend fun getPlotNodes(bookId: String): List<PlotNodeEntity>

    @Query("SELECT * FROM characters WHERE bookId = :bookId ORDER BY firstChapterIndex")
    fun observeCharacters(bookId: String): Flow<List<StoryCharacterEntity>>

    @Query("SELECT * FROM relations WHERE bookId = :bookId")
    fun observeRelations(bookId: String): Flow<List<StoryRelationEntity>>

    @Query("SELECT * FROM plot_nodes WHERE bookId = :bookId ORDER BY startChapterIndex")
    fun observePlotNodes(bookId: String): Flow<List<PlotNodeEntity>>

    @Query("SELECT * FROM tts_provider_profiles WHERE enabled = 1 ORDER BY id")
    fun observeTtsProfiles(): Flow<List<TtsProviderProfileEntity>>

    @Query("SELECT * FROM tts_provider_profiles WHERE enabled = 1 ORDER BY id")
    suspend fun getTtsProfiles(): List<TtsProviderProfileEntity>

    @Query("SELECT * FROM tts_provider_profiles WHERE id = :profileId")
    suspend fun getTtsProfile(profileId: String): TtsProviderProfileEntity?

    @Upsert
    suspend fun upsertTtsProfile(profile: TtsProviderProfileEntity)

    @Query("SELECT * FROM tts_profile_voice_pool WHERE profileId = :profileId ORDER BY role, voiceName")
    fun observeTtsVoicePool(profileId: String): Flow<List<TtsProfileVoicePoolEntity>>

    @Query("SELECT * FROM tts_profile_voice_pool WHERE profileId = :profileId ORDER BY role, voiceName")
    suspend fun getTtsVoicePool(profileId: String): List<TtsProfileVoicePoolEntity>

    @Upsert
    suspend fun upsertTtsVoicePool(items: List<TtsProfileVoicePoolEntity>)

    @Query("DELETE FROM tts_profile_voice_pool WHERE profileId = :profileId AND source != 'BUILT_IN'")
    suspend fun clearRemoteTtsVoicePool(profileId: String)

    @Query("SELECT * FROM book_tts_settings WHERE bookId = :bookId")
    fun observeBookTtsSetting(bookId: String): Flow<BookTtsSettingEntity?>

    @Query("SELECT * FROM book_tts_settings WHERE bookId = :bookId")
    suspend fun getBookTtsSetting(bookId: String): BookTtsSettingEntity?

    @Upsert
    suspend fun upsertBookTtsSetting(setting: BookTtsSettingEntity)

    @Query("SELECT b.* FROM character_voice_bindings b JOIN characters c ON c.id = b.characterId WHERE c.bookId = :bookId AND b.active = 1")
    fun observeActiveCharacterVoiceBindings(bookId: String): Flow<List<CharacterVoiceBindingEntity>>

    @Query("SELECT * FROM character_voice_bindings WHERE characterId = :characterId AND active = 1 LIMIT 1")
    suspend fun getActiveCharacterVoiceBinding(characterId: String): CharacterVoiceBindingEntity?

    @Query("UPDATE character_voice_bindings SET active = 0 WHERE characterId = :characterId")
    suspend fun deactivateCharacterVoiceBindings(characterId: String)

    @Upsert
    suspend fun upsertCharacterVoiceBinding(binding: CharacterVoiceBindingEntity)

    @Query("DELETE FROM character_voice_bindings WHERE characterId = :characterId")
    suspend fun clearCharacterVoiceBindings(characterId: String)

    @Query("SELECT * FROM book_narrator_bindings WHERE bookId = :bookId AND active = 1 LIMIT 1")
    suspend fun getActiveNarratorBinding(bookId: String): BookNarratorBindingEntity?

    @Query("UPDATE book_narrator_bindings SET active = 0 WHERE bookId = :bookId")
    suspend fun deactivateNarratorBindings(bookId: String)

    @Upsert
    suspend fun upsertNarratorBinding(binding: BookNarratorBindingEntity)

    @Query("SELECT * FROM tts_scripts WHERE chapterId = :chapterId")
    suspend fun getTtsScript(chapterId: String): TtsScriptEntity?

    @Query("SELECT * FROM tts_script_segments WHERE scriptId = :scriptId ORDER BY segmentIndex")
    suspend fun getTtsScriptSegments(scriptId: String): List<TtsScriptSegmentEntity>

    @Upsert
    suspend fun upsertTtsScript(script: TtsScriptEntity)

    @Upsert
    suspend fun upsertTtsScriptSegments(segments: List<TtsScriptSegmentEntity>)

    @Query("DELETE FROM tts_script_segments WHERE scriptId = :scriptId")
    suspend fun deleteTtsScriptSegments(scriptId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt, id")
    fun observeChatMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentChatMessages(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_sessions WHERE characterId = :characterId AND archived = 0 ORDER BY updatedAt DESC")
    fun observeChatSessions(characterId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getChatSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE characterId = :characterId AND archived = 0 ORDER BY updatedAt DESC")
    suspend fun getChatSessions(characterId: String): List<ChatSessionEntity>

    @Upsert
    suspend fun upsertChatSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameChatSession(sessionId: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchChatSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteChatSession(sessionId: String)

    @Query("SELECT * FROM relations WHERE bookId = :bookId")
    suspend fun getRelations(bookId: String): List<StoryRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<StoryCharacterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<StoryRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlotNodes(nodes: List<PlotNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearChatMessages(sessionId: String)

    @Query("SELECT COUNT(*) FROM memory_items WHERE bookId = :bookId")
    fun observeMemoryCount(bookId: String): Flow<Int>

    @Query("""
        SELECT m.*,
        EXISTS(SELECT 1 FROM character_memory_defaults d WHERE d.characterId = :characterId AND d.memoryId = m.id) AS isDefault,
        EXISTS(SELECT 1 FROM session_memory_links s WHERE s.sessionId = :sessionId AND s.memoryId = m.id) AS isSession,
        (m.chapterEndIndex IS NOT NULL AND m.chapterEndIndex >= :analysisCompleted) AS isLocked
        FROM memory_items m
        WHERE m.bookId = :bookId
        ORDER BY m.updatedAt DESC
    """)
    suspend fun getMemoriesWithSelection(
        bookId: String,
        characterId: String,
        sessionId: String,
        analysisCompleted: Int
    ): List<MemoryWithSelection>

    @Query("""
        SELECT m.*,
        EXISTS(SELECT 1 FROM character_memory_defaults d WHERE d.characterId = :characterId AND d.memoryId = m.id) AS isDefault,
        EXISTS(SELECT 1 FROM session_memory_links s WHERE s.sessionId = :sessionId AND s.memoryId = m.id) AS isSession,
        (m.chapterEndIndex IS NOT NULL AND m.chapterEndIndex >= :analysisCompleted) AS isLocked
        FROM memory_items m JOIN memory_fts f ON f.memoryId = m.id
        WHERE memory_fts MATCH :matchQuery AND m.bookId = :bookId
        ORDER BY m.updatedAt DESC
    """)
    suspend fun searchMemoriesWithSelection(
        bookId: String,
        characterId: String,
        sessionId: String,
        analysisCompleted: Int,
        matchQuery: String
    ): List<MemoryWithSelection>

    @Query("SELECT m.* FROM memory_items m JOIN character_memory_defaults d ON d.memoryId = m.id WHERE d.characterId = :characterId AND (m.chapterEndIndex IS NULL OR m.chapterEndIndex < :analysisCompleted) ORDER BY d.createdAt")
    suspend fun getDefaultMemories(characterId: String, analysisCompleted: Int): List<MemoryItemEntity>

    @Query("SELECT m.* FROM memory_items m JOIN session_memory_links s ON s.memoryId = m.id WHERE s.sessionId = :sessionId AND (m.chapterEndIndex IS NULL OR m.chapterEndIndex < :analysisCompleted) ORDER BY s.selectedAt")
    suspend fun getSessionMemories(sessionId: String, analysisCompleted: Int): List<MemoryItemEntity>

    @Query("SELECT * FROM memory_items WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeMemories(bookId: String): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE bookId = :bookId")
    suspend fun getMemories(bookId: String): List<MemoryItemEntity>

    @Query("SELECT * FROM memory_items WHERE id = :memoryId")
    suspend fun getMemory(memoryId: String): MemoryItemEntity?

    @Upsert
    suspend fun upsertMemory(memory: MemoryItemEntity)

    @Insert
    suspend fun insertMemoryFts(memory: MemoryFtsEntity)

    @Query("DELETE FROM memory_fts WHERE memoryId = :memoryId")
    suspend fun deleteMemoryFts(memoryId: String)

    @Query("DELETE FROM memory_fts WHERE memoryId IN (SELECT id FROM memory_items WHERE bookId = :bookId)")
    suspend fun deleteMemoryFtsForBook(bookId: String)

    @Query("DELETE FROM memory_items WHERE id = :memoryId")
    suspend fun deleteMemory(memoryId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultMemory(link: CharacterMemoryDefaultEntity)

    @Query("DELETE FROM character_memory_defaults WHERE characterId = :characterId AND memoryId = :memoryId")
    suspend fun deleteDefaultMemory(characterId: String, memoryId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionMemory(link: SessionMemoryLinkEntity)

    @Query("DELETE FROM session_memory_links WHERE sessionId = :sessionId AND memoryId = :memoryId")
    suspend fun deleteSessionMemory(sessionId: String, memoryId: String)

    @Query("SELECT COUNT(*) FROM character_memory_defaults WHERE characterId = :characterId")
    suspend fun getDefaultMemoryCount(characterId: String): Int

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: String, chapterIndex: Int)

    @Query("UPDATE chapters SET ttsStatus = :status WHERE id = :chapterId")
    suspend fun updateTtsStatus(chapterId: String, status: String)

    @Query("UPDATE chapters SET ttsStatus = :cancelled WHERE id = :chapterId AND ttsStatus IN ('QUEUED', 'RUNNING')")
    suspend fun cancelTtsTask(chapterId: String, cancelled: String)

    @Query("UPDATE chapters SET ttsStatus = :status, ttsManifestPath = :manifestPath WHERE id = :chapterId")
    suspend fun updateTtsResult(chapterId: String, status: String, manifestPath: String?)

    @Query("UPDATE characters SET voiceId = :voiceId WHERE id = :characterId")
    suspend fun updateCharacterVoice(characterId: String, voiceId: String)

    @Query("UPDATE chapters SET analysisStatus = :status WHERE id IN (:chapterIds)")
    suspend fun updateAnalysisStatus(chapterIds: List<String>, status: String)

    @Query("UPDATE chapters SET analysisStatus = :cancelled WHERE bookId = :bookId AND analysisStatus IN ('QUEUED', 'RUNNING')")
    suspend fun cancelAnalysisTasks(bookId: String, cancelled: String)

    @Query("UPDATE chapters SET analysisStatus = :failed WHERE bookId = :bookId AND analysisStatus IN ('QUEUED', 'RUNNING')")
    suspend fun failAnalysisTasks(bookId: String, failed: String)

    @Query("UPDATE books SET analysisCompleted = :completed WHERE id = :bookId")
    suspend fun updateAnalysisCompleted(bookId: String, completed: Int)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query("DELETE FROM relations WHERE bookId = :bookId")
    suspend fun deleteRelationsForBook(bookId: String)

    @Query("DELETE FROM plot_nodes WHERE bookId = :bookId")
    suspend fun deletePlotNodesForBook(bookId: String)
}
