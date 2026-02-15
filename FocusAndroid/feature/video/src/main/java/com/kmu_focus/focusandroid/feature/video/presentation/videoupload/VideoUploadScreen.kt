package com.kmu_focus.focusandroid.feature.video.presentation.videoupload

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VideoUploadScreen(
    onVideoSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun handleSelectedVideo(uri: Uri) {
        val uriString = uri.toString()
        viewModel.selectVideo(uriString)
        onVideoSelected(uriString)
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        handleSelectedVideo(uri)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                val galleryIntent = Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).apply {
                    type = "video/*"
                }
                galleryPickerLauncher.launch(galleryIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("갤러리에서 동영상 선택")
        }

        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
