package com.kmu_focus.focusandroid.feature.video.presentation.main

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.video.presentation.videoplayer.VideoPlayerScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videoupload.VideoUploadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_OWNER_PICK = 20

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_OWNER_PICK)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val list = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { it to uri.toString() }
            }
        }
        if (list.isNotEmpty()) viewModel.addOwnersFromBitmaps(list)
        else viewModel.setAddOwnerResult(AddOwnerResult.Fail)
    }

    LaunchedEffect(uiState.addOwnerResult) {
        when (val r = uiState.addOwnerResult) {
            is AddOwnerResult.Success -> {
                Toast.makeText(context, "소유자가 등록되었습니다", Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            is AddOwnerResult.NoFace, is AddOwnerResult.Fail -> {
                Toast.makeText(context, "얼굴을 찾을 수 없습니다. 다른 사진을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            is AddOwnerResult.Multi -> {
                val msg = when {
                    r.failCount == 0 -> "${r.successCount}명 등록되었습니다"
                    r.successCount == 0 -> "${r.failCount}명 실패 (얼굴 미검출)"
                    else -> "${r.successCount}명 등록, ${r.failCount}명 실패"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            null -> { }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    multiPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("소유자 추가 (여러 명 선택)")
            }
            if (uiState.addedOwnerUris.isNotEmpty()) {
                OutlinedButton(onClick = { viewModel.clearOwners() }) {
                    Text("전체 삭제")
                }
            }
        }

        if (uiState.addedOwnerUris.isNotEmpty()) {
            Text(
                text = "등록된 소유자 (${uiState.addedOwnerUris.size}명)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.addedOwnerUris) { uri ->
                    OwnerThumbnail(uri = uri)
                }
            }
        }

        VideoUploadScreen(
            onVideoSelected = { uri -> viewModel.onVideoSelected(uri) }
        )

        uiState.selectedVideoUri?.let { uri ->
            VideoSaveScreen(
                videoUri = uri,
                modifier = Modifier.fillMaxWidth()
            )

            VideoPlayerScreen(
                videoUri = uri,
                onClearSelection = { viewModel.onClearSelection() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OwnerThumbnail(
    uri: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }
    Card(
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
