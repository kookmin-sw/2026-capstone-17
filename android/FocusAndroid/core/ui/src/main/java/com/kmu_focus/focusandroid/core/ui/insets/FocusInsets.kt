package com.kmu_focus.focusandroid.core.ui.insets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FocusContentInsetMode {
    ScaffoldPadding,
    EdgeToEdge,
}

fun Modifier.focusContentPadding(
    scaffoldPadding: PaddingValues,
    mode: FocusContentInsetMode,
): Modifier = then(
    when (mode) {
        FocusContentInsetMode.ScaffoldPadding -> Modifier.padding(scaffoldPadding)
        FocusContentInsetMode.EdgeToEdge -> Modifier
    },
)

fun Modifier.focusSafeDrawingPadding(
    sides: WindowInsetsSides,
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = composed {
    windowInsetsPadding(WindowInsets.safeDrawing.only(sides))
        .padding(
            start = start,
            top = top,
            end = end,
            bottom = bottom,
        )
}
