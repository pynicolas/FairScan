/*
 * Copyright 2025 Pierre-Yves Nicolas
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.ui.Navigation

@Composable
fun MyScaffold(
    navigation: Navigation,
    pageListState: CommonPageListState,
    pageListButton: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Box {
        if (!isLandscape(LocalConfiguration.current)) {
            Scaffold(
                bottomBar = { DocumentBar(pageListState, bottomBar, Modifier, pageListButton) }
            ) { innerPadding ->
                content(Modifier.padding(innerPadding).fillMaxSize())
            }
        } else {
            Scaffold { innerPadding ->
                Row(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    content(Modifier.weight(2f))
                    DocumentBar(pageListState, bottomBar, Modifier.weight(1f), pageListButton)
                }
            }
        }
        if (onBack != null) {
            BackButton(
                onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }
        AppOverflowMenu(
            navigation,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

@Composable
fun DocumentBar(
    pageListState: CommonPageListState,
    buttonBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    pageListButton: (@Composable () -> Unit)? = null,
) {
    val isLandscape = isLandscape(LocalConfiguration.current)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            if (isLandscape)
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            else
                Modifier
        ) {
            CommonPageList(pageListState, modifier = Modifier.fillMaxWidth())

            if (pageListButton != null) {
                val alignment = if (isLandscape) Alignment.BottomEnd else Alignment.CenterEnd
                Box(
                    Modifier
                        .align(alignment)
                        .padding(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    pageListButton()
                }
            }
        }

        val color = MaterialTheme.colorScheme.surfaceContainerHigh
        if (isLandscape) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(8.dp)
            ) {
                buttonBar()
            }
        } else {
            BottomAppBar(
                containerColor = color,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    buttonBar()
                }
            }
        }
    }
}

fun isLandscape(configuration: Configuration): Boolean {
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun AppOverflowMenu(
    navigation: Navigation,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {

            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                text = { Text(stringResource(R.string.settings)) },
                onClick = {
                    expanded = false
                    navigation.toSettingsScreen()
                }
            )

            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                text = { Text(stringResource(R.string.about)) },
                onClick = {
                    expanded = false
                    navigation.toAboutScreen()
                }
            )
        }
    }
}

