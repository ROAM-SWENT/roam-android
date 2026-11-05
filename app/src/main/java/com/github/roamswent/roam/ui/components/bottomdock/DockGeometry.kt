/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DockBarContentHeight = 80.dp

@Composable
internal fun dockContentClearance(): Dp =
    DockBarContentHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
