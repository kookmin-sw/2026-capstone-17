package com.kmu_focus.focusandroid.feature.account.presentation.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus

@Composable
fun MyPageScreen(
    onLoggedOut: () -> Unit = {},
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

    LaunchedEffect(uiState.pendingExternalUrl) {
        val targetUrl = uiState.pendingExternalUrl ?: return@LaunchedEffect
        uriHandler.openUri(targetUrl)
        viewModel.consumePendingExternalUrl()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshChzzkStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "내 정보",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "방송 준비에 필요한 계정 정보와 연동 상태를 여기서 관리합니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfileSummaryCard(
                        name = uiState.profile?.name.orEmpty(),
                        email = uiState.profile?.email,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            ChzzkConnectionCard(
                status = uiState.chzzkStatus,
                isLoading = uiState.isChzzkLoading,
                isActionInProgress = uiState.isChzzkActionInProgress,
                isAwaitingConnection = uiState.isAwaitingChzzkConnection,
                onConnect = viewModel::startChzzkConnect,
                onDisconnect = viewModel::disconnectChzzk,
                onRefresh = viewModel::refreshChzzkStatus,
                onOpenWatchUrl = { watchUrl ->
                    uriHandler.openUri(watchUrl)
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::refreshAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("새로고침")
                }
                Button(
                    onClick = viewModel::logout,
                    enabled = !uiState.isLoggingOut,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.isLoggingOut) "로그아웃 중..." else "로그아웃")
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    name: String,
    email: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9)),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = email ?: "이메일 정보 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                )
            }
        }
    }
}

@Composable
private fun ChzzkConnectionCard(
    status: ChzzkConnectionStatus?,
    isLoading: Boolean,
    isActionInProgress: Boolean,
    isAwaitingConnection: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWatchUrl: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "치지직 채널 연동",
                style = MaterialTheme.typography.titleMedium,
            )
            if (isLoading) {
                Text(
                    text = "연동 상태를 확인하는 중입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (status?.connected == true) {
                Text(
                    text = status.channelName?.let { "$it 채널과 연결되어 있습니다." } ?: "치지직 채널과 연결되어 있습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                status.channelId?.let { value ->
                    InfoLine(label = "채널 ID", value = value)
                }
                status.watchUrl?.let { watchUrl ->
                    Text(
                        text = watchUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                status.connectedAt?.let { connectedAt ->
                    InfoLine(label = "연결 시각", value = connectedAt)
                }
            } else {
                Text(
                    text = if (isAwaitingConnection) {
                        "브라우저에서 치지직 로그인과 권한 승인을 마친 뒤 앱으로 돌아오세요. 복귀하면 연동 상태를 다시 확인합니다."
                    } else {
                        "치지직 연동이 아직 없습니다. 연동하면 방송 채널 상태를 함께 관리할 수 있습니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !isActionInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isAwaitingConnection) "연동 확인" else "상태 새로고침")
                }

                if (status?.connected == true) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !isActionInProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isActionInProgress) "해제 중..." else "연동 해제")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !isActionInProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                isActionInProgress -> "연동 준비 중..."
                                isAwaitingConnection -> "브라우저 다시 열기"
                                else -> "치지직 연동"
                            }
                        )
                    }
                }
            }

            if (status?.connected == true && !status.watchUrl.isNullOrBlank()) {
                Button(
                    onClick = { onOpenWatchUrl(status.watchUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("치지직 채널 열기")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label · $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
