package com.storybrain.app.data

import androidx.room.withTransaction
import com.storybrain.app.importer.ImportedNovel
import java.nio.charset.StandardCharsets
import java.util.UUID

class MemorySelectionException(message: String) : Exception(message)

data class SelectedMemoryGroups(
    val defaultMemories: List<MemoryItemEntity>,
    val sessionMemories: List<MemoryItemEntity>
)

class StoryRepository(private val database: AppDatabase) {
    private val dao = database.storyDao()

    fun observeBooks() = dao.observeBooks()
    fun observeBook(bookId: String) = dao.observeBook(bookId)
    fun observeChapters(bookId: String) = dao.observeChapters(bookId)
    fun observeChapter(chapterId: String) = dao.observeChapter(chapterId)
    fun observeCharacters(bookId: String) = dao.observeCharacters(bookId)
    fun observeRelations(bookId: String) = dao.observeRelations(bookId)
    fun observePlotNodes(bookId: String) = dao.observePlotNodes(bookId)
    fun observeChatMessages(sessionId: String) = dao.observeChatMessages(sessionId)
    fun observeChatSessions(characterId: String) = dao.observeChatSessions(characterId)
    fun observeMemories(bookId: String) = dao.observeMemories(bookId)
    fun observeMemoryCount(bookId: String) = dao.observeMemoryCount(bookId)
    fun observeTtsProfiles() = dao.observeTtsProfiles()
    fun observeTtsVoicePool(profileId: String) = dao.observeTtsVoicePool(profileId)
    fun observeBookTtsSetting(bookId: String) = dao.observeBookTtsSetting(bookId)
    fun observeActiveCharacterVoiceBindings(bookId: String) = dao.observeActiveCharacterVoiceBindings(bookId)

    suspend fun getBook(bookId: String) = dao.getBook(bookId)
    suspend fun getBooks() = dao.getBooks()
    suspend fun getChapter(chapterId: String) = dao.getChapter(chapterId)
    suspend fun getChapters(bookId: String) = dao.getChapters(bookId)
    suspend fun getAllChapterIds() = dao.getAllChapterIds()
    suspend fun getCharacters(bookId: String) = dao.getCharacters(bookId)
    suspend fun getPlotNodes(bookId: String) = dao.getPlotNodes(bookId)
    suspend fun getRelations(bookId: String) = dao.getRelations(bookId)
    suspend fun getTtsProfiles() = dao.getTtsProfiles()
    suspend fun getTtsProfile(profileId: String) = dao.getTtsProfile(profileId)
    suspend fun getTtsVoicePool(profileId: String) = dao.getTtsVoicePool(profileId)
    suspend fun getBookTtsSetting(bookId: String) = dao.getBookTtsSetting(bookId)
    suspend fun getActiveCharacterVoiceBinding(characterId: String) = dao.getActiveCharacterVoiceBinding(characterId)
    suspend fun getActiveNarratorBinding(bookId: String) = dao.getActiveNarratorBinding(bookId)
    suspend fun getChatSession(sessionId: String) = dao.getChatSession(sessionId)
    suspend fun getRecentChatMessages(sessionId: String, limit: Int = 40) =
        dao.getRecentChatMessages(sessionId, limit).reversed()

    suspend fun saveImportedNovel(novel: ImportedNovel, sourceName: String, title: String): String {
        val bookId = UUID.randomUUID().toString()
        val chapterEntities = novel.chapters.mapIndexed { index, chapter ->
            ChapterEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = index,
                title = chapter.title,
                content = chapter.content,
                charCount = chapter.content.length
            )
        }
        database.withTransaction {
            dao.insertBook(
                BookEntity(
                    id = bookId,
                    title = title.trim().ifBlank { novel.suggestedTitle },
                    sourceName = sourceName,
                    importedAt = System.currentTimeMillis(),
                    chapterCount = chapterEntities.size,
                    totalChars = chapterEntities.sumOf { it.charCount.toLong() }
                )
            )
            chapterEntities.chunked(100).forEach { dao.insertChapters(it) }
        }
        return bookId
    }

    suspend fun updateReadingProgress(bookId: String, chapterIndex: Int) =
        dao.updateReadingProgress(bookId, chapterIndex)

    suspend fun updateTtsStatus(chapterId: String, status: TaskStatus) =
        dao.updateTtsStatus(chapterId, status.name)

    suspend fun updateTtsResult(chapterId: String, status: TaskStatus, manifestPath: String?) =
        dao.updateTtsResult(chapterId, status.name, manifestPath)

    suspend fun updateCharacterVoice(characterId: String, voiceId: String) =
        dao.updateCharacterVoice(characterId, voiceId)

    suspend fun ensureDefaultTtsProfiles() = database.withTransaction {
        val defaults = listOf(
            TtsProviderProfileEntity(TtsProfileIds.EDGE, TtsProviderKind.EDGE.name, "Edge TTS", "", "edge-online"),
            TtsProviderProfileEntity(TtsProfileIds.FISH, TtsProviderKind.FISH_AUDIO.name, "Fish Audio", "https://api.fish.audio", "s2.1-pro-free"),
            TtsProviderProfileEntity(TtsProfileIds.OPENAI, TtsProviderKind.OPENAI_COMPATIBLE.name, "OpenAI-compatible", "https://api.openai.com/v1", "tts-1")
        )
        defaults.forEach { if (dao.getTtsProfile(it.id) == null) dao.upsertTtsProfile(it) }
        if (dao.getTtsVoicePool(TtsProfileIds.EDGE).isEmpty()) {
            val now = System.currentTimeMillis()
            dao.upsertTtsVoicePool(
                listOf(
                    edgeVoice("zh-CN-XiaoxiaoNeural", "旁白·晓晓", TtsVoiceRole.NARRATOR, "FEMALE", now),
                    edgeVoice("zh-CN-XiaoxiaoNeural", "晓晓", TtsVoiceRole.FEMALE, "FEMALE", now),
                    edgeVoice("zh-CN-XiaoyiNeural", "晓伊", TtsVoiceRole.FEMALE, "FEMALE", now),
                    edgeVoice("zh-CN-YunxiNeural", "云希", TtsVoiceRole.MALE, "MALE", now),
                    edgeVoice("zh-CN-YunyangNeural", "云扬", TtsVoiceRole.MALE, "MALE", now),
                    edgeVoice("zh-CN-YunjianNeural", "云健", TtsVoiceRole.MALE, "MALE", now),
                    edgeVoice("zh-CN-XiaoxiaoNeural", "晓晓", TtsVoiceRole.UNKNOWN, "FEMALE", now),
                    edgeVoice("zh-CN-YunxiNeural", "云希", TtsVoiceRole.UNKNOWN, "MALE", now)
                )
            )
        }
    }

    suspend fun saveTtsProfile(profile: TtsProviderProfileEntity) = dao.upsertTtsProfile(
        profile.copy(baseUrl = profile.baseUrl.trim().trimEnd('/'), model = profile.model.trim(), updatedAt = System.currentTimeMillis())
    )

    suspend fun replaceRemoteVoicePool(profileId: String, voices: List<TtsProfileVoicePoolEntity>) = database.withTransaction {
        dao.clearRemoteTtsVoicePool(profileId)
        if (voices.isNotEmpty()) dao.upsertTtsVoicePool(voices)
    }

    suspend fun addVoicePoolItem(item: TtsProfileVoicePoolEntity) = dao.upsertTtsVoicePool(
        listOf(item.copy(updatedAt = System.currentTimeMillis()))
    )

    suspend fun setBookPrimaryProfile(bookId: String, profileId: String?) = dao.upsertBookTtsSetting(
        BookTtsSettingEntity(bookId, profileId, System.currentTimeMillis())
    )

    suspend fun setCharacterVoiceBinding(binding: CharacterVoiceBindingEntity) = database.withTransaction {
        dao.deactivateCharacterVoiceBindings(binding.characterId)
        dao.upsertCharacterVoiceBinding(binding.copy(active = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearCharacterVoiceBinding(characterId: String) = dao.clearCharacterVoiceBindings(characterId)

    suspend fun setNarratorBinding(binding: BookNarratorBindingEntity) = database.withTransaction {
        dao.deactivateNarratorBindings(binding.bookId)
        dao.upsertNarratorBinding(binding.copy(active = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getTtsScript(chapterId: String) = dao.getTtsScript(chapterId)
    suspend fun getTtsScriptSegments(scriptId: String) = dao.getTtsScriptSegments(scriptId)
    suspend fun saveTtsScript(script: TtsScriptEntity, segments: List<TtsScriptSegmentEntity>) = database.withTransaction {
        dao.upsertTtsScript(script)
        dao.deleteTtsScriptSegments(script.id)
        if (segments.isNotEmpty()) dao.upsertTtsScriptSegments(segments)
    }
    suspend fun updateTtsScriptSegments(segments: List<TtsScriptSegmentEntity>) = dao.upsertTtsScriptSegments(segments)

    private fun edgeVoice(
        voiceId: String,
        name: String,
        role: TtsVoiceRole,
        gender: String,
        now: Long
    ) = TtsProfileVoicePoolEntity(
        profileId = TtsProfileIds.EDGE,
        voiceId = voiceId,
        role = role.name,
        voiceName = name,
        gender = gender,
        tagsJson = "[]",
        source = "BUILT_IN",
        updatedAt = now
    )

    suspend fun insertChatMessage(message: ChatMessageEntity) {
        database.withTransaction {
            dao.insertChatMessage(message)
            dao.touchChatSession(message.sessionId, message.createdAt)
        }
    }

    suspend fun clearChatMessages(sessionId: String) = dao.clearChatMessages(sessionId)

    suspend fun getOrCreateSession(bookId: String, characterId: String): ChatSessionEntity {
        dao.getChatSessions(characterId).firstOrNull()?.let { return it }
        return createSession(bookId, characterId)
    }

    suspend fun createSession(bookId: String, characterId: String): ChatSessionEntity {
        val now = System.currentTimeMillis()
        return ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            characterId = characterId,
            title = "新对话",
            createdAt = now,
            updatedAt = now
        ).also { dao.upsertChatSession(it) }
    }

    suspend fun renameSession(sessionId: String, title: String) =
        dao.renameChatSession(sessionId, title.trim().take(40).ifBlank { "新对话" }, System.currentTimeMillis())

    suspend fun deleteSession(sessionId: String) = dao.deleteChatSession(sessionId)

    suspend fun titleSessionFromFirstMessage(sessionId: String, text: String) {
        val session = dao.getChatSession(sessionId) ?: return
        if (session.title == "新对话") renameSession(sessionId, text.trim().replace('\n', ' ').take(18))
    }

    suspend fun updateAnalysisStatus(chapterIds: List<String>, status: TaskStatus) =
        dao.updateAnalysisStatus(chapterIds, status.name)

    suspend fun saveAnalysisDelta(
        bookId: String,
        completed: Int,
        characters: List<StoryCharacterEntity>,
        relations: List<StoryRelationEntity>,
        nodes: List<PlotNodeEntity>
    ) = database.withTransaction {
        dao.insertCharacters(characters)
        dao.insertRelations(relations)
        dao.insertPlotNodes(nodes)
        dao.updateAnalysisCompleted(bookId, completed)
        syncMemories(bookId, relations, nodes, dao.getCharacters(bookId))
    }

    suspend fun backfillAnalysisMemories(bookId: String) = database.withTransaction {
        syncMemories(bookId, dao.getRelations(bookId), dao.getPlotNodes(bookId), dao.getCharacters(bookId))
    }

    private suspend fun syncMemories(
        bookId: String,
        relations: List<StoryRelationEntity>,
        nodes: List<PlotNodeEntity>,
        characters: List<StoryCharacterEntity>
    ) {
        val names = characters.associate { it.id to it.canonicalName }
        val now = System.currentTimeMillis()
        relations.forEach { relation ->
            val from = names[relation.fromCharacterId] ?: "角色"
            val to = names[relation.toCharacterId] ?: "角色"
            val title = "$from · ${relation.relationType} · $to"
            val content = buildString {
                append("$from 与 $to 的关系为 ${relation.relationType}。")
                if (relation.evidence.isNotBlank()) append("证据：${relation.evidence}")
            }
            upsertIndexedMemory(
                MemoryItemEntity(
                    id = stableMemoryId("relation:${relation.id}"),
                    bookId = bookId,
                    type = MemoryType.RELATION.name,
                    title = title,
                    content = content,
                    chapterStartIndex = relation.startChapterIndex,
                    chapterEndIndex = relation.endChapterIndex ?: relation.startChapterIndex,
                    characterIdsJson = MemorySearch.json(listOf(relation.fromCharacterId, relation.toCharacterId)),
                    sourceKey = "relation:${relation.id}",
                    searchTerms = MemorySearch.terms(title, content, from, to),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        nodes.forEach { node ->
            val existingParticipants = MemorySearch.jsonStrings(node.participantIdsJson)
            val participantIds = if (existingParticipants.isNotEmpty()) {
                existingParticipants
            } else {
                val searchable = "${node.title}\n${node.summary}"
                characters.filter { character ->
                    searchable.contains(character.canonicalName, ignoreCase = true) ||
                        MemorySearch.jsonStrings(character.aliasesJson).any { alias ->
                            alias.isNotBlank() && searchable.contains(alias, ignoreCase = true)
                        }
                }.map { it.id }
            }
            if (existingParticipants.isEmpty() && participantIds.isNotEmpty()) {
                dao.insertPlotNodes(listOf(node.copy(participantIdsJson = MemorySearch.json(participantIds))))
            }
            upsertIndexedMemory(
                MemoryItemEntity(
                    id = stableMemoryId("plot:${node.id}"),
                    bookId = bookId,
                    type = MemoryType.PLOT.name,
                    title = node.title,
                    content = node.summary,
                    chapterStartIndex = node.startChapterIndex,
                    chapterEndIndex = node.endChapterIndex ?: node.startChapterIndex,
                    characterIdsJson = MemorySearch.json(participantIds),
                    sourceKey = "plot:${node.id}",
                    searchTerms = MemorySearch.terms(node.title, node.summary, node.locationName.orEmpty()),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun saveMemory(memory: MemoryItemEntity) = database.withTransaction { upsertIndexedMemory(memory) }

    suspend fun createMemory(
        bookId: String,
        type: MemoryType,
        title: String,
        content: String,
        chapterStartIndex: Int? = null,
        chapterEndIndex: Int? = chapterStartIndex,
        characterIds: List<String> = emptyList()
    ): MemoryItemEntity {
        val cleanContent = content.trim().take(2_000)
        require(cleanContent.isNotBlank()) { "记忆内容不能为空" }
        val cleanTitle = title.trim().ifBlank { cleanContent.take(20) }.take(60)
        val now = System.currentTimeMillis()
        return MemoryItemEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            type = type.name,
            title = cleanTitle,
            content = cleanContent,
            chapterStartIndex = chapterStartIndex,
            chapterEndIndex = chapterEndIndex,
            characterIdsJson = MemorySearch.json(characterIds),
            sourceKey = "manual:${UUID.randomUUID()}",
            searchTerms = MemorySearch.terms(cleanTitle, cleanContent),
            editable = true,
            createdAt = now,
            updatedAt = now
        ).also { saveMemory(it) }
    }

    suspend fun updateMemory(memory: MemoryItemEntity) {
        require(memory.editable) { "自动分析记忆不能直接修改" }
        val updated = memory.copy(
            title = memory.title.trim().take(60),
            content = memory.content.trim().take(2_000),
            searchTerms = MemorySearch.terms(memory.title, memory.content),
            updatedAt = System.currentTimeMillis()
        )
        saveMemory(updated)
    }

    suspend fun deleteMemory(memoryId: String) = database.withTransaction {
        val memory = dao.getMemory(memoryId) ?: return@withTransaction
        require(memory.editable) { "自动分析记忆不能删除" }
        dao.deleteMemoryFts(memoryId)
        dao.deleteMemory(memoryId)
    }

    private suspend fun upsertIndexedMemory(memory: MemoryItemEntity) {
        val previous = dao.getMemory(memory.id)
        val stable = memory.copy(createdAt = previous?.createdAt ?: memory.createdAt)
        dao.upsertMemory(stable)
        dao.deleteMemoryFts(stable.id)
        dao.insertMemoryFts(MemoryFtsEntity(stable.id, stable.title, stable.content, stable.searchTerms))
    }

    suspend fun memoriesWithSelection(
        bookId: String,
        characterId: String,
        sessionId: String,
        analysisCompleted: Int,
        query: String = ""
    ): List<MemoryWithSelection> {
        val match = MemorySearch.matchQuery(query)
        return if (match.isBlank()) {
            dao.getMemoriesWithSelection(bookId, characterId, sessionId, analysisCompleted)
        } else runCatching {
            dao.searchMemoriesWithSelection(bookId, characterId, sessionId, analysisCompleted, match)
        }.getOrElse {
            dao.getMemoriesWithSelection(bookId, characterId, sessionId, analysisCompleted).filter { memory ->
                memory.title.contains(query, true) || memory.content.contains(query, true)
            }
        }
    }

    suspend fun suggestMemories(
        bookId: String,
        characterId: String,
        sessionId: String,
        analysisCompleted: Int,
        userText: String
    ): List<MemoryWithSelection> {
        val tokens = MemorySearch.tokenize(userText).toSet()
        return memoriesWithSelection(bookId, characterId, sessionId, analysisCompleted)
            .filterNot { it.isDefault || it.isSession || it.isLocked }
            .map { memory ->
                val memoryTokens = MemorySearch.tokenize("${memory.title} ${memory.content}").toSet()
                val tagged = characterId in MemorySearch.jsonStrings(memory.characterIdsJson)
                val score = tokens.intersect(memoryTokens).size * 10 + if (tagged) 8 else 0
                memory to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(8)
            .map { it.first }
    }

    suspend fun setDefaultMemory(characterId: String, memoryId: String, selected: Boolean) {
        if (!selected) return dao.deleteDefaultMemory(characterId, memoryId)
        enforceMemoryBudget(characterId, null, memoryId)
        database.withTransaction {
            dao.insertDefaultMemory(CharacterMemoryDefaultEntity(characterId, memoryId, System.currentTimeMillis()))
        }
    }

    suspend fun setSessionMemory(characterId: String, sessionId: String, memoryId: String, selected: Boolean) {
        if (!selected) return dao.deleteSessionMemory(sessionId, memoryId)
        enforceMemoryBudget(characterId, sessionId, memoryId)
        dao.insertSessionMemory(SessionMemoryLinkEntity(sessionId, memoryId, System.currentTimeMillis()))
    }

    private suspend fun enforceMemoryBudget(characterId: String, sessionId: String?, addingMemoryId: String) {
        val memory = dao.getMemory(addingMemoryId) ?: error("找不到这条记忆")
        val analysisCompleted = dao.getBook(memory.bookId)?.analysisCompleted ?: error("找不到所属小说")
        require(memory.chapterEndIndex == null || memory.chapterEndIndex < analysisCompleted) {
            "这条记忆来自尚未分析的章节，当前已锁定"
        }
        val defaults = dao.getDefaultMemories(characterId, Int.MAX_VALUE)
        val session = sessionId?.let { dao.getSessionMemories(it, Int.MAX_VALUE) }.orEmpty()
        val selected = (defaults + session).distinctBy { it.id }
        validateMemoryBudget(selected, memory)
    }

    suspend fun getSelectedMemoryGroups(
        characterId: String,
        sessionId: String,
        analysisCompleted: Int
    ): SelectedMemoryGroups {
        val defaults = dao.getDefaultMemories(characterId, analysisCompleted)
        val defaultIds = defaults.mapTo(mutableSetOf()) { it.id }
        val session = dao.getSessionMemories(sessionId, analysisCompleted).filterNot { it.id in defaultIds }
        val allowedIds = (defaults + session)
            .distinctBy { it.id }
            .take(MAX_SELECTED_MEMORIES)
            .runningFold(0 to emptyList<MemoryItemEntity>()) { (used, kept), memory ->
                if (used + memory.content.length <= MAX_MEMORY_CHARS) {
                    used + memory.content.length to (kept + memory)
                } else {
                    used to kept
                }
            }
            .last().second
            .mapTo(mutableSetOf()) { it.id }
        return SelectedMemoryGroups(
            defaultMemories = defaults.filter { it.id in allowedIds },
            sessionMemories = session.filter { it.id in allowedIds }
        )
    }

    suspend fun seedCharacterDefaults(bookId: String, characterId: String) {
        if (dao.getDefaultMemoryCount(characterId) > 0) return
        val memories = dao.getMemories(bookId)
        val direct = memories.filter { characterId in MemorySearch.jsonStrings(it.characterIdsJson) }
        val latestPlots = memories.filter { it.type == MemoryType.PLOT.name }.sortedByDescending { it.chapterEndIndex }.take(5)
        (direct + latestPlots).distinctBy { it.id }.take(10).forEach {
            dao.insertDefaultMemory(CharacterMemoryDefaultEntity(characterId, it.id, System.currentTimeMillis()))
        }
    }

    suspend fun deleteBook(bookId: String) = database.withTransaction {
        dao.deleteMemoryFtsForBook(bookId)
        dao.deleteRelationsForBook(bookId)
        dao.deletePlotNodesForBook(bookId)
        dao.deleteBook(bookId)
    }

    private fun stableMemoryId(sourceKey: String): String = UUID.nameUUIDFromBytes(
        sourceKey.toByteArray(StandardCharsets.UTF_8)
    ).toString()

    companion object {
        const val MAX_SELECTED_MEMORIES = 30
        const val MAX_MEMORY_CHARS = 12_000
        const val MAX_HISTORY_CHARS = 12_000
    }
}
