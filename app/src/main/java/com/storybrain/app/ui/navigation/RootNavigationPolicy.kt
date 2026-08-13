package com.storybrain.app.ui.navigation

data class RootTabSwitchPolicy(
    val popUpTo: AppDestination,
    val launchSingleTop: Boolean,
    val saveState: Boolean,
    val restoreState: Boolean
)

object RootNavigationPolicy {
    val tabSwitch = RootTabSwitchPolicy(
        popUpTo = AppDestination.Library,
        launchSingleTop = true,
        saveState = true,
        restoreState = true
    )

    fun destinationFor(route: String?): AppDestination? = route?.let(AppDestinations.byRoute::get)

    fun showsRootBar(route: String?): Boolean = destinationFor(route)?.showRootBar == true
}
