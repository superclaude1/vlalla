package com.storybrain.app.export

import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity

object Neo4jExporter {
    fun export(
        book: BookEntity,
        characters: List<StoryCharacterEntity>,
        relations: List<StoryRelationEntity>,
        plots: List<PlotNodeEntity>
    ): String = buildString {
        appendLine("// 章境 Neo4j Cypher 导出")
        appendLine("CREATE CONSTRAINT story_book_id IF NOT EXISTS FOR (n:Book) REQUIRE n.id IS UNIQUE;")
        appendLine("CREATE CONSTRAINT story_character_id IF NOT EXISTS FOR (n:Character) REQUIRE n.id IS UNIQUE;")
        appendLine("CREATE CONSTRAINT story_plot_id IF NOT EXISTS FOR (n:Plot) REQUIRE n.id IS UNIQUE;")
        appendLine("CREATE CONSTRAINT story_location_name IF NOT EXISTS FOR (n:Location) REQUIRE n.name IS UNIQUE;")
        appendLine("MERGE (b:Book {id:${q(book.id)}}) SET b.title=${q(book.title)}, b.chapterCount=${book.chapterCount}, b.analysisCompleted=${book.analysisCompleted};")
        characters.forEach { character ->
            appendLine(
                "MERGE (c:Character {id:${q(character.id)}}) SET c.name=${q(character.canonicalName)}, c.aliases=${q(character.aliasesJson)}, c.gender=${q(character.gender)}, c.personality=${q(character.personality)}, c.firstChapter=${character.firstChapterIndex + 1}, c.lastChapter=${character.lastChapterIndex + 1} WITH c MATCH (b:Book {id:${q(book.id)}}) MERGE (b)-[:HAS_CHARACTER]->(c);"
            )
        }
        relations.forEach { relation ->
            val type = relation.relationType.uppercase().replace(Regex("[^A-Z0-9_]"), "_").ifBlank { "RELATED_TO" }
            appendLine(
                "MATCH (a:Character {id:${q(relation.fromCharacterId)}}), (z:Character {id:${q(relation.toCharacterId)}}) MERGE (a)-[r:$type {id:${q(relation.id)}}]->(z) SET r.strength=${relation.strength}, r.startChapter=${relation.startChapterIndex + 1}, r.evidence=${q(relation.evidence)}, r.confidence=${relation.confidence};"
            )
        }
        plots.forEach { plot ->
            appendLine(
                "MERGE (p:Plot {id:${q(plot.id)}}) SET p.title=${q(plot.title)}, p.summary=${q(plot.summary)}, p.startChapter=${plot.startChapterIndex + 1}, p.confidence=${plot.confidence} WITH p MATCH (b:Book {id:${q(book.id)}}) MERGE (b)-[:HAS_PLOT]->(p);"
            )
            plot.locationName?.takeIf(String::isNotBlank)?.let { location ->
                appendLine("MERGE (l:Location {name:${q(location)}}) WITH l MATCH (p:Plot {id:${q(plot.id)}}) MERGE (p)-[:OCCURS_AT]->(l);")
            }
            parseStringArray(plot.parentIdsJson).forEach { parentId ->
                appendLine("MATCH (a:Plot {id:${q(parentId)}}), (z:Plot {id:${q(plot.id)}}) MERGE (a)-[:LEADS_TO]->(z);")
            }
        }
    }

    // Plot parent IDs are JSON string arrays. Keeping this parser platform-neutral
    // makes the exporter testable on the JVM without Android's org.json runtime.
    private fun parseStringArray(json: String): List<String> =
        Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(json).map { match ->
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }.toList()

    private fun q(value: String): String = "'" + value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "'"
}
