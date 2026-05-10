package com.kmu_focus.focusandroid.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kmu_focus.focusandroid.core.ui.insets.focusContentPadding
import com.kmu_focus.focusandroid.feature.account.presentation.mypage.MyPageScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.create.CreateBroadcastScreen
import com.kmu_focus.focusandroid.feature.broadcast.presentation.list.BroadcastListScreen
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen

@Composable
fun MainShellScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { MainShellViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (uiState.isBottomBarVisible) {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.selectedTab == MainTab.BROADCAST,
                        onClick = { viewModel.selectTab(MainTab.BROADCAST) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "방송",
                            )
                        },
                        label = { Text("방송") },
                        alwaysShowLabel = false,
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == MainTab.VIDEO,
                        onClick = { viewModel.selectTab(MainTab.VIDEO) },
                        icon = { Text("V") },
                        label = { Text("동영상") },
                        alwaysShowLabel = false,
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == MainTab.PROFILE,
                        onClick = { viewModel.selectTab(MainTab.PROFILE) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "내 정보",
                            )
                        },
                        label = { Text("내 정보") },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusContentPadding(
                    scaffoldPadding = innerPadding,
                    mode = uiState.currentDestination.contentInsetMode,
                ),
        ) {
            when (val destination = uiState.currentDestination) {
                is MainShellDestination.Tab -> {
                    when (destination.tab) {
                        MainTab.BROADCAST -> {
                            BroadcastListScreen(
                                onNavigateToCreate = viewModel::openBroadcastCreate,
                                onNavigateToBroadcast = { broadcastId ->
                                    Toast.makeText(
                                        context,
                                        "시청자용 방송 입장은 아직 연결되지 않았습니다. broadcastId=$broadcastId",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }

                        MainTab.VIDEO -> {
                            MainScreen()
                        }

                        MainTab.PROFILE -> {
                            MyPageScreen()
                        }
                    }
                }

                MainShellDestination.BroadcastCreate -> {
                    CreateBroadcastScreen(
                        onBack = viewModel::closeOverlay,
                        onNavigateToCamera = { broadcastId, streamKey ->
                            viewModel.openBroadcastCamera(
                                broadcastId = broadcastId,
                                streamKey = streamKey,
                            )
                        },
                    )
                }

                is MainShellDestination.BroadcastCamera -> {
                    BroadcastCameraScreen(
                        broadcastId = destination.broadcastId,
                        streamKey = destination.streamKey,
                        hlsUrl = destination.hlsUrl,
                        onBack = viewModel::closeOverlay,
                    )
                }
            }
        }
    }
}
