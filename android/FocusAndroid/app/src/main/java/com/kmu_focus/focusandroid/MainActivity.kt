package com.kmu_focus.focusandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kmu_focus.focusandroid.presentation.AppSelectionScreen
import com.kmu_focus.focusandroid.presentation.theme.FocusAndroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusAndroidTheme {
                AppSelectionScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
