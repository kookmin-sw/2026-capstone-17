package com.kmu_focus.focusandroid.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthScreen
import com.kmu_focus.focusandroid.feature.auth.presentation.AuthSessionViewModel
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
) {
    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val isLoggedIn by authSessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    var isVideoGuestMode by rememberSaveable { mutableStateOf(false) }

    if (!isLoggedIn && !isVideoGuestMode) {
        AuthScreen(
            onLoginSuccess = { },
            onContinueWithoutLogin = { isVideoGuestMode = true },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (!isLoggedIn && isVideoGuestMode) {
        MainScreen(
            onBackToModeSelection = { isVideoGuestMode = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    MainShellScreen(modifier = modifier)
}
