package com.storybrain.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        StoryCharacterEntity::class,
        StoryRelationEntity::class,
        PlotNodeEntity::class,
        ChatMessageEntity::class,
        MemoryItemEntity::class,
        CharacterMemoryEvidenceEntity::class,
        MemoryFtsEntity::class,
        ChatSessionEntity::class,
        CharacterMemoryDefaultEntity::class,
        SessionMemoryLinkEntity::class,
        TtsProviderProfileEntity::class,
        TtsProfileVoicePoolEntity::class,
        BookTtsSettingEntity::class,
        CharacterVoiceBindingEntity::class,
        BookNarratorBindingEntity::class,
        TtsScriptEntity::class,
        TtsScriptSegmentEntity::class,
        ChapterCharacterMentionEntity::class,
        TaskRunEntity::class,
        TaskEventEntity::class,
        LlmApiProfileEntity::class,
        LlmModelEntity::class,
        ReadingPreferenceEntity::class,
        ReadingPositionEntity::class,
        ReadingMarkEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "story-brain.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .addCallback(ANALYSIS_RECOVERY_CALLBACK)
            .build()

        internal val ANALYSIS_RECOVERY_CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.beginTransaction()
                try {
                    db.execSQL(
                        """UPDATE chapters SET analysisStatus = 'COMPLETED'
                            WHERE analysisStatus = 'RUNNING' AND EXISTS (
                                SELECT 1 FROM books
                                WHERE books.id = chapters.bookId
                                  AND chapters.chapterIndex < books.analysisCompleted
                            )""".trimIndent()
                    )
                    db.execSQL(
                        """UPDATE chapters SET analysisStatus = 'PENDING'
                            WHERE analysisStatus = 'RUNNING'""".trimIndent()
                    )
                    db.execSQL(
                        """UPDATE task_runs SET status = 'FAILED',
                            finishedAt = COALESCE(finishedAt, CAST(strftime('%s', 'now') AS INTEGER) * 1000)
                            WHERE taskType = 'ANALYSIS' AND status = 'RUNNING'""".trimIndent()
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_bookId` ON `chat_messages` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_characterId` ON `chat_messages` (`characterId`)")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plot_nodes` ADD COLUMN `participantIdsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `memory_items` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `chapterStartIndex` INTEGER, `chapterEndIndex` INTEGER, `characterIdsJson` TEXT NOT NULL, `sourceKey` TEXT NOT NULL, `searchTerms` TEXT NOT NULL, `editable` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_bookId` ON `memory_items` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_type` ON `memory_items` (`type`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_items_sourceKey` ON `memory_items` (`sourceKey`)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `memory_fts` USING FTS4(`memoryId`, `title`, `content`, `searchTerms`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_bookId` ON `chat_sessions` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_characterId` ON `chat_sessions` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_updatedAt` ON `chat_sessions` (`updatedAt`)")
                db.execSQL(
                    """INSERT OR IGNORE INTO `chat_sessions` (`id`,`bookId`,`characterId`,`title`,`createdAt`,`updatedAt`,`archived`) SELECT 'legacy-' || `characterId`, `bookId`, `characterId`, '旧对话', MIN(`createdAt`), MAX(`createdAt`), 0 FROM `chat_messages` GROUP BY `bookId`,`characterId`"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_messages_new` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("INSERT INTO `chat_messages_new` (`id`,`bookId`,`characterId`,`sessionId`,`role`,`content`,`createdAt`) SELECT `id`,`bookId`,`characterId`,'legacy-' || `characterId`,`role`,`content`,`createdAt` FROM `chat_messages`")
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_bookId` ON `chat_messages` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_characterId` ON `chat_messages` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `character_memory_defaults` (`characterId` TEXT NOT NULL, `memoryId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`characterId`,`memoryId`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memoryId`) REFERENCES `memory_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_memory_defaults_memoryId` ON `character_memory_defaults` (`memoryId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `session_memory_links` (`sessionId` TEXT NOT NULL, `memoryId` TEXT NOT NULL, `selectedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`,`memoryId`), FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memoryId`) REFERENCES `memory_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_memory_links_memoryId` ON `session_memory_links` (`memoryId`)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `importanceScore` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `importanceReason` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tts_provider_profiles` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `displayName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `model` TEXT NOT NULL, `supportsInstructions` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO `tts_provider_profiles` VALUES ('edge-default','EDGE','Edge TTS','','edge-online',0,1,0)"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO `tts_provider_profiles` VALUES ('fish-default','FISH_AUDIO','Fish Audio','https://api.fish.audio','s2.1-pro-free',0,1,0)"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO `tts_provider_profiles` VALUES ('openai-compatible-default','OPENAI_COMPATIBLE','OpenAI-compatible','https://api.openai.com/v1','tts-1',0,1,0)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tts_profile_voice_pool` (`profileId` TEXT NOT NULL, `voiceId` TEXT NOT NULL, `role` TEXT NOT NULL, `voiceName` TEXT NOT NULL, `gender` TEXT NOT NULL, `ageGroup` TEXT NOT NULL, `language` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `source` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`,`voiceId`,`role`), FOREIGN KEY(`profileId`) REFERENCES `tts_provider_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_profile_voice_pool_profileId` ON `tts_profile_voice_pool` (`profileId`)")
                seedEdgeVoices(db)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `book_tts_settings` (`bookId` TEXT NOT NULL, `primaryProfileId` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tts_settings_bookId` ON `book_tts_settings` (`bookId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `character_voice_bindings` (`characterId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `voiceId` TEXT NOT NULL, `voiceName` TEXT NOT NULL, `active` INTEGER NOT NULL, `userConfirmed` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`characterId`,`profileId`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_voice_bindings_characterId` ON `character_voice_bindings` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_voice_bindings_profileId` ON `character_voice_bindings` (`profileId`)")
                db.execSQL(
                    """INSERT OR IGNORE INTO `character_voice_bindings` (`characterId`,`profileId`,`voiceId`,`voiceName`,`active`,`userConfirmed`,`updatedAt`) SELECT `id`,'edge-default',REPLACE(`voiceId`,'edge:',''),REPLACE(`voiceId`,'edge:',''),1,1,0 FROM `characters` WHERE `voiceId` IS NOT NULL AND `voiceId` != '' AND `voiceId` NOT LIKE 'local:%'"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `book_narrator_bindings` (`bookId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `voiceId` TEXT NOT NULL, `voiceName` TEXT NOT NULL, `active` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`,`profileId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_narrator_bindings_bookId` ON `book_narrator_bindings` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_narrator_bindings_profileId` ON `book_narrator_bindings` (`profileId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tts_scripts` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `sourceHash` TEXT NOT NULL, `llmModel` TEXT NOT NULL, `promptVersion` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_scripts_bookId` ON `tts_scripts` (`bookId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tts_scripts_chapterId` ON `tts_scripts` (`chapterId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tts_script_segments` (`id` TEXT NOT NULL, `scriptId` TEXT NOT NULL, `segmentIndex` INTEGER NOT NULL, `blockIndex` INTEGER NOT NULL, `speaker` TEXT NOT NULL, `rawText` TEXT NOT NULL, `directivesJson` TEXT NOT NULL, `profileId` TEXT NOT NULL, `model` TEXT NOT NULL, `voiceId` TEXT NOT NULL, `renderedText` TEXT NOT NULL, `cacheKey` TEXT NOT NULL, `status` TEXT NOT NULL, `audioPath` TEXT, `error` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`scriptId`) REFERENCES `tts_scripts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tts_script_segments_scriptId` ON `tts_script_segments` (`scriptId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tts_script_segments_scriptId_segmentIndex` ON `tts_script_segments` (`scriptId`,`segmentIndex`)")
            }

            private fun seedEdgeVoices(db: SupportSQLiteDatabase) {
                val rows = listOf(
                    arrayOf("zh-CN-XiaoxiaoNeural", "旁白·晓晓", "NARRATOR", "FEMALE"),
                    arrayOf("zh-CN-XiaoxiaoNeural", "晓晓", "FEMALE", "FEMALE"),
                    arrayOf("zh-CN-XiaoyiNeural", "晓伊", "FEMALE", "FEMALE"),
                    arrayOf("zh-CN-YunxiNeural", "云希", "MALE", "MALE"),
                    arrayOf("zh-CN-YunyangNeural", "云扬", "MALE", "MALE"),
                    arrayOf("zh-CN-YunjianNeural", "云健", "MALE", "MALE"),
                    arrayOf("zh-CN-XiaoxiaoNeural", "晓晓", "UNKNOWN", "FEMALE"),
                    arrayOf("zh-CN-YunxiNeural", "云希", "UNKNOWN", "MALE")
                )
                rows.forEach { row ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO `tts_profile_voice_pool` VALUES (?,?,?,?,?,'UNKNOWN','zh','[]','BUILT_IN',0,0)",
                        arrayOf("edge-default", row[0], row[2], row[1], row[3])
                    )
                }
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chapter_character_mentions` (`id` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `evidence` TEXT NOT NULL, `confidence` REAL NOT NULL, `sourceHash` TEXT NOT NULL, `analysisVersion` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_character_mentions_chapterId` ON `chapter_character_mentions` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_character_mentions_characterId` ON `chapter_character_mentions` (`characterId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_character_mentions_chapterId_characterId_sourceHash_analysisVersion` ON `chapter_character_mentions` (`chapterId`,`characterId`,`sourceHash`,`analysisVersion`)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `task_runs` (`id` TEXT NOT NULL, `taskType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `finishedAt` INTEGER, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_runs_createdAt` ON `task_runs` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_runs_taskType` ON `task_runs` (`taskType`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `task_events` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `taskType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `eventType` TEXT NOT NULL, `stage` TEXT NOT NULL, `retryable` INTEGER NOT NULL, `statusCode` INTEGER, `attempt` INTEGER NOT NULL, `message` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `task_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_runId` ON `task_events` (`runId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_createdAt` ON `task_events` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_eventType` ON `task_events` (`eventType`)")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `character_memory_evidence` (`memoryId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `chapterStartIndex` INTEGER, `chapterEndIndex` INTEGER, `characterIdsJson` TEXT NOT NULL, `source` TEXT NOT NULL, `confidence` REAL NOT NULL, `invalidatedAt` INTEGER, `spoilerBoundaryChapterIndex` INTEGER, PRIMARY KEY(`memoryId`, `characterId`), FOREIGN KEY(`memoryId`) REFERENCES `memory_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_memory_evidence_memoryId` ON `character_memory_evidence` (`memoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_memory_evidence_characterId` ON `character_memory_evidence` (`characterId`)")
                db.query("SELECT `id`,`chapterStartIndex`,`chapterEndIndex`,`characterIdsJson` FROM `memory_items`").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val startIndex = cursor.getColumnIndexOrThrow("chapterStartIndex")
                    val endIndex = cursor.getColumnIndexOrThrow("chapterEndIndex")
                    val idsIndex = cursor.getColumnIndexOrThrow("characterIdsJson")
                    while (cursor.moveToNext()) {
                        val memoryId = cursor.getString(idIndex)
                        val start = if (cursor.isNull(startIndex)) null else cursor.getInt(startIndex)
                        val end = if (cursor.isNull(endIndex)) null else cursor.getInt(endIndex)
                        val characterIdsJson = cursor.getString(idsIndex)
                        runCatching { JSONArray(characterIdsJson) }.getOrNull()?.let { ids ->
                            for (index in 0 until ids.length()) {
                                val characterId = ids.optString(index).trim()
                                if (characterId.isBlank()) continue
                                db.execSQL(
                                    "INSERT OR IGNORE INTO `character_memory_evidence` (`memoryId`,`characterId`,`chapterStartIndex`,`chapterEndIndex`,`characterIdsJson`,`source`,`confidence`,`invalidatedAt`,`spoilerBoundaryChapterIndex`) VALUES (?,?,?,?,?,'LEGACY_MEMORY_ITEM',0.5,NULL,?)",
                                    arrayOf(memoryId, characterId, start, end, characterIdsJson, end ?: start)
                                )
                            }
                        }
                    }
                }
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `llm_api_profiles` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_llm_api_profiles_updatedAt` ON `llm_api_profiles` (`updatedAt`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `llm_models` (`apiProfileId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`apiProfileId`, `modelId`), FOREIGN KEY(`apiProfileId`) REFERENCES `llm_api_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_llm_models_apiProfileId` ON `llm_models` (`apiProfileId`)")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `promptTokens` INTEGER")
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `completionTokens` INTEGER")
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `totalTokens` INTEGER")
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `usageQuality` TEXT")
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `requestId` TEXT")
                db.execSQL("ALTER TABLE `task_events` ADD COLUMN `responseModel` TEXT")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `llm_api_profiles` ADD COLUMN `selectedModel` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v10 → v11：阅读器升级。纯增量：
         * - reading_preferences：单书阅读偏好覆盖
         * - reading_positions：字符偏移进度（迁移时按当前章节初始化）
         * - reading_marks：书签/高亮/批注
         */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reading_preferences` (`bookId` TEXT NOT NULL, `mode` TEXT NOT NULL, `useGlobalStyle` INTEGER NOT NULL, `theme` TEXT NOT NULL, `fontSizeSp` REAL NOT NULL, `lineHeightMultiplier` REAL NOT NULL, `paragraphSpacingDp` REAL NOT NULL, `horizontalPaddingDp` INTEGER NOT NULL, `serifFont` INTEGER NOT NULL, `autoFollowAudio` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reading_positions` (`bookId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `sourceOffset` INTEGER NOT NULL, `scrollOffsetPx` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_positions_chapterId` ON `reading_positions` (`chapterId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reading_marks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `type` TEXT NOT NULL, `startOffset` INTEGER NOT NULL, `endOffset` INTEGER NOT NULL, `excerpt` TEXT NOT NULL, `note` TEXT NOT NULL, `colorKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_marks_bookId` ON `reading_marks` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_marks_chapterId` ON `reading_marks` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_marks_bookId_type` ON `reading_marks` (`bookId`,`type`)")
                // 老数据按当前章节初始化阅读位置（对齐本地 0.6.0 MIGRATION_4_5 语义）
                db.execSQL(
                    """INSERT OR IGNORE INTO `reading_positions` (`bookId`,`chapterId`,`sourceOffset`,`scrollOffsetPx`,`updatedAt`) SELECT b.`id`, c.`id`, 0, 0, b.`importedAt` FROM `books` b JOIN `chapters` c ON c.`bookId` = b.`id` AND c.`chapterIndex` = b.`currentChapterIndex`"""
                )
            }
        }
    }
}