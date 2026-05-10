package com.kmu_focus.focusandroid.feature.broadcast.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

@Composable
fun BroadcastListScreen(
    onNavigateToCreate: () -> Unit = {},
    onNavigateToBroadcast: (broadcastId: String) -> Unit = {},
    viewModel: BroadcastListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val totalCount = uiState.broadcasts.size
    val onAirCount = uiState.broadcasts.count { it.status == BroadcastStatus.ON_AIR }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "방송 관리",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "생성된 방송과 현재 라이브 상태를 한 화면에서 관리합니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryChip(
                            label = "전체 $totalCount",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                        SummaryChip(
                            label = "ON AIR $onAirCount",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("목록 새로고침")
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                uiState.error?.let { error ->
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

                if (uiState.broadcasts.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "생성된 방송이 없습니다.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "새 방송을 만들면 이 화면에서 상태와 진입 흐름을 바로 확인할 수 있습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(
                            items = uiState.broadcasts,
                            key = { it.broadcastId },
                        ) { broadcast ->
                            BroadcastItemCard(
                                broadcast = broadcast,
                                onDelete = { viewModel.deleteBroadcast(broadcast.broadcastId) },
                                onNavigateToBroadcast = { onNavigateToBroadcast(broadcast.broadcastId) },
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNavigateToCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("새 방송")
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun BroadcastItemCard(
    broadcast: Broadcast,
    onDelete: () -> Unit,
    onNavigateToBroadcast: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = broadcast.status == BroadcastStatus.ON_AIR,
                onClick = onNavigateToBroadcast,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = broadcast.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "방장 ${broadcast.memberName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SpacerWidth()
                StatusBadge(status = broadcast.status)
            }

            broadcast.hlsUrl?.let { hlsUrl ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = hlsUrl,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onNavigateToBroadcast,
                    modifier = Modifier.weight(1f),
                    enabled = broadcast.status == BroadcastStatus.ON_AIR,
                ) {
                    Text(if (broadcast.status == BroadcastStatus.ON_AIR) "방송 입장" else "대기 중")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("삭제")
                }
            }
        }
    }
}

@Composable
private fun SpacerWidth() {
    Box(modifier = Modifier.width(12.dp))
}

@Composable
private fun StatusBadge(status: BroadcastStatus) {
    val (label, iconText, color) = when (status) {
        BroadcastStatus.READY -> Triple("대기", "R", MaterialTheme.colorScheme.primary)
        BroadcastStatus.ON_AIR -> Triple("방송 중", "O", Color(0xFFD14343))
        BroadcastStatus.ENDED -> Triple("종료", "E", MaterialTheme.colorScheme.onSurfaceVariant)
        BroadcastStatus.ERROR -> Triple("오류", "X", MaterialTheme.colorScheme.tertiary)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(color = color, shape = CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = iconText,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
