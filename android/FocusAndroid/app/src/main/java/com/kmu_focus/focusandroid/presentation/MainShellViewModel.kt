package com.kmu_focus.focusandroid.presentation

import com.kmu_focus.focusandroid.core.ui.insets.FocusContentInsetMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MainTab {
    BROADCAST,
    VIDEO,
    PROFILE,
}

sealed interface MainShellDestination {
    data class Tab(val tab: MainTab) : MainShellDestination
    data object BroadcastCreate : MainShellDestination
    data class BroadcastCamera(
        val broadcastId: String,
        val streamKey: String,
        val hlsUrl: String,
    ) : MainShellDestination
}

internal val MainShellDestination.contentInsetMode: FocusContentInsetMode
    get() = when (this) {
        is MainShellDestination.BroadcastCamera -> FocusContentInsetMode.EdgeToEdge
        MainShellDestination.BroadcastCreate -> FocusContentInsetMode.ScaffoldPadding
        is MainShellDestination.Tab -> FocusContentInsetMode.ScaffoldPadding
    }

data class MainShellUiState(
    val selectedTab: MainTab = MainTab.BROADCAST,
    val currentDestination: MainShellDestination = MainShellDestination.Tab(MainTab.BROADCAST),
) {
    val isBottomBarVisible: Boolean
        get() = currentDestination is MainShellDestination.Tab
}

class MainShellViewModel {

    private val _uiState = MutableStateFlow(MainShellUiState())
    val uiState: StateFlow<MainShellUiState> = _uiState.asStateFlow()

    fun selectTab(tab: MainTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                currentDestination = MainShellDestination.Tab(tab),
            )
        }
    }

    fun openBroadcastCreate() {
        _uiState.update {
            it.copy(
                selectedTab = MainTab.BROADCAST,
                currentDestination = MainShellDestination.BroadcastCreate,
            )
        }
    }

    fun openBroadcastCamera(
        broadcastId: String,
        streamKey: String,
        hlsUrl: String = "",
    ) {
        _uiState.update {
            it.copy(
                selectedTab = MainTab.BROADCAST,
                currentDestination = MainShellDestination.BroadcastCamera(
                    broadcastId = broadcastId,
                    streamKey = streamKey,
                    hlsUrl = hlsUrl,
                ),
            )
        }
    }

    fun closeOverlay() {
        _uiState.update {
            it.copy(
                selectedTab = MainTab.BROADCAST,
                currentDestination = MainShellDestination.Tab(MainTab.BROADCAST),
            )
        }
    }
}
