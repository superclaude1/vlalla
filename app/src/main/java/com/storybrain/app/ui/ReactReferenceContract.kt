package com.storybrain.app.ui

import androidx.compose.ui.text.font.FontFamily

/** Product values derived from the supplied React reference and consumed directly by the UI. */
object ReactReferenceContract {
    const val topBarHeightDp = 52
    val bottomTabs = listOf("书架", "我的")
    val shelfCoverDp = 52 to 72
    const val searchRoute = "search"
    val emptyLibraryContent = listOf("暂无小说", "导入 TXT")
    val bookHubCoverDp = 64 to 88
    val bookPortals = listOf("目录", "故事", "配音")
    val readerBottomActions = listOf("上一章", "当前进度", "下一章")
    const val readerBottomContentPaddingDp = 96
    const val bottomNavigationHeightDp = 72
    val readerSheetActions = listOf("本章配音", "保存本章")
    val readerFontFamily: FontFamily = FontFamily.Serif
    const val readerLineHeightSp = 31
    const val readerUsesSerifProse = true
    const val bookHubTopBarTitleIsEmpty = true
    const val primaryButtonRadiusDp = 6
    const val portalRadiusDp = 4
    const val darkSystemBars = true
    const val bookPreviewChapterCount = 14
    const val bookHomeShowsCover = false
    const val bookActionsAreFixedToBottom = true
}
