package com.storybrain.app.ui

import com.storybrain.app.ui.navigation.AppDestination
import com.storybrain.app.ui.navigation.AppDestinations

/** Product hierarchy labels; routes are compatibility views of the canonical destination registry. */
object NavigationArchitecture {
    val LibraryRoute = AppDestination.Library.route
    val SearchRoute = AppDestination.Search.route
    val MyRoute = AppDestination.My.route
    val ChatRoute = AppDestination.CharacterChat.route

    val storyHubRows = listOf("分析", "图谱", "记忆")
    val graphTabs = listOf("剧情", "角色", "地点")
    val audioHubRows = listOf("引擎", "角色音色", "章节音频")
    val bookHubRows = listOf("目录", "故事", "配音")
    val myRows = listOf("LLM", "配音服务", "音色库", "运行日志", "关于")
    val routes: Set<String> = AppDestinations.byRoute.keys
}
