package com.storybrain.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGraphFunctionParityContractTest {
    private val outerScreen = java.io.File("src/main/java/com/storybrain/app/ui/InformationArchitectureScreens.kt").readText()

    @Test
    fun outerGraphTopBarOwnsNeo4jCreateDocumentExportAndFeedback() {
        assertTrue(outerScreen.contains("ActivityResultContracts.CreateDocument"))
        assertTrue(outerScreen.contains("viewModel.prepareNeo4jExport(bookId)"))
        assertTrue(outerScreen.contains("viewModel.writeNeo4jExport(bookId, pending.first, uri, pending.second)"))
        assertTrue(outerScreen.contains("exportLauncher.launch("))
        assertTrue(outerScreen.contains("正在准备 Neo4j 导出…"))
        assertTrue(outerScreen.contains("exportState.message"))
    }

    @Test
    fun graphKeepsOneTopBarAndLockedInternalTabs() {
        assertTrue(outerScreen.contains("hideTopBar = true"))
        assertTrue(outerScreen.contains("lockedTab = true"))
        assertFalse(outerScreen.contains("CompactBackBar(\"图谱\", onBack)"))
    }
}
