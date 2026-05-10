package com.kmu_focus.focusandroid.feature.broadcast.presentation.create

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CreateBroadcastScreen(
    onNavigateToCamera: (broadcastId: String, streamKey: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STREAM CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "새 방송 생성",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "방송 제목을 입력한 뒤 생성하면 송출 카메라 화면으로 바로 이동할 수 있습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onBack) {
                Text("뒤로")
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
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
                    text = "기본 정보",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("방송 제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = viewModel::createBroadcast,
                    enabled = !uiState.isCreating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("방송 생성")
                }
                if (uiState.isCreating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "서버에 방송을 생성하는 중입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        uiState.createdBroadcast?.let { broadcast ->
            Surface(
                shape = RoundedCornerShape(30.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "생성 완료",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    BroadcastMetaRow(label = "방송 ID", value = broadcast.broadcastId)
                    BroadcastMetaRow(label = "상태", value = broadcast.status.name)
                    BroadcastMetaRow(label = "스트림 키", value = broadcast.streamKey)
                    broadcast.hlsUrl?.let { hlsUrl ->
                        BroadcastMetaRow(label = "HLS", value = hlsUrl)
                    }
                    Button(
                        onClick = {
                            onNavigateToCamera(
                                broadcast.broadcastId,
                                broadcast.streamKey,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("카메라 화면으로 이동")
                    }
                }
            }
        }

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
    }
}

@Composable
private fun BroadcastMetaRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
