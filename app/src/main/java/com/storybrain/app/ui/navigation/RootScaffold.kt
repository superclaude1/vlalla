package com.storybrain.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storybrain.app.ui.ReactReferenceContract

@Composable
fun RootScaffold(
    currentDestination: AppDestination?,
    onSelectRoot: (AppDestination) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentDestination?.showRootBar == true) {
                NavigationBar(
                    modifier = Modifier.heightIn(min = 72.dp)
                ) {
                    AppDestinations.rootTabs.forEachIndexed { index, destination ->
                        NavigationBarItem(

                            selected = currentDestination == destination,
                            onClick = { onSelectRoot(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (destination == AppDestination.Library) {
                                        Icons.Rounded.AutoStories
                                    } else {
                                        Icons.Rounded.Settings
                                    },
                                    contentDescription = ReactReferenceContract.bottomTabs[index]
                                )
                            },
                            label = {
                                Text(
                                    ReactReferenceContract.bottomTabs[index],
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            content()
        }
    }
}
