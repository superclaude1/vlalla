package com.storybrain.app.export

import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class Neo4jExporterTest {
    @Test
    fun exportsCharactersProtectionAndLocation() {
        val book = BookEntity("book", "测试'小说", "test.txt", 1L, 15, 1000, analysisCompleted = 15)
        val a = StoryCharacterEntity("a", "book", "甲", "[]", "MALE", "沉稳", null, 0, 14, .9f)
        val b = StoryCharacterEntity("b", "book", "乙", "[]", "FEMALE", "勇敢", null, 0, 14, .9f)
        val relation = StoryRelationEntity("r", "book", "a", "b", "PROTECTS", .9f, 0, evidence = "甲保护乙", confidence = .9f)
        val plot = PlotNodeEntity("p", "book", "事件", "摘要", 0, locationName = "青云山", confidence = .8f)

        val cypher = Neo4jExporter.export(book, listOf(a, b), listOf(relation), listOf(plot))

        assertTrue(cypher.contains("[r:PROTECTS"))
        assertTrue(cypher.contains("Location {name:'青云山'}"))
        assertTrue(cypher.contains("测试\\'小说"))
    }
}
