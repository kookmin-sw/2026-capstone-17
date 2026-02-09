package com.kmu_focus.focusandroid.feature.video.presentation.main

import android.content.Context
import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromBitmapUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.ClearOwnersUseCase
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MainViewModelTest {

    private val context = mockk<Context>(relaxed = true)
    private val addOwnerFromBitmapUseCase = mockk<AddOwnerFromBitmapUseCase>(relaxed = true)
    private val clearOwnersUseCase = mockk<ClearOwnersUseCase>(relaxed = true)

    private fun createViewModel() = MainViewModel(context, addOwnerFromBitmapUseCase, clearOwnersUseCase)

    @Test
    fun `초기 상태에서 selectedVideoUri가 null임`() = runTest {
        val viewModel = createViewModel()
        assertNull(viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `onVideoSelected 호출 시 selectedVideoUri가 업데이트됨`() = runTest {
        val viewModel = createViewModel()
        viewModel.onVideoSelected("content://media/video/123")
        assertEquals("content://media/video/123", viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `onClearSelection 호출 시 selectedVideoUri가 null로 리셋됨`() = runTest {
        val viewModel = createViewModel()
        viewModel.onVideoSelected("content://media/video/123")
        assertNotNull(viewModel.uiState.value.selectedVideoUri)

        viewModel.onClearSelection()
        assertNull(viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `여러 번 onVideoSelected 호출 시 최신 URI로 업데이트됨`() = runTest {
        val viewModel = createViewModel()
        viewModel.onVideoSelected("content://media/video/123")
        viewModel.onVideoSelected("content://media/video/456")
        assertEquals("content://media/video/456", viewModel.uiState.value.selectedVideoUri)
    }
}
