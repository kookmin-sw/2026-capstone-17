package com.kmu_focus.focusandroid.feature.video.presentation.main

import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromUriUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.ClearOwnersUseCase
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val clearOwnersUseCase: ClearOwnersUseCase = mockk(relaxed = true)
    private val addOwnerFromUriUseCase: AddOwnerFromUriUseCase = mockk(relaxed = true)
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(
            addOwnerFromUriUseCase = addOwnerFromUriUseCase,
            clearOwnersUseCase = clearOwnersUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태에서 selectedVideoUri가 null임`() = runTest {
        assertNull(viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `onVideoSelected 호출 시 selectedVideoUri가 업데이트됨`() = runTest {
        viewModel.onVideoSelected("content://media/video/123")
        assertEquals("content://media/video/123", viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `onClearSelection 호출 시 selectedVideoUri가 null로 리셋됨`() = runTest {
        viewModel.onVideoSelected("content://media/video/123")
        assertNotNull(viewModel.uiState.value.selectedVideoUri)

        viewModel.onClearSelection()
        assertNull(viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `여러 번 onVideoSelected 호출 시 최신 URI로 업데이트됨`() = runTest {
        viewModel.onVideoSelected("content://media/video/123")
        viewModel.onVideoSelected("content://media/video/456")
        assertEquals("content://media/video/456", viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `resetAfterSave 호출 시 selectedVideoUri가 null이 된다`() = runTest {
        viewModel.onVideoSelected("content://video/1")
        assertNotNull(viewModel.uiState.value.selectedVideoUri)

        viewModel.resetAfterSave()
        assertNull(viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `resetAfterSave 호출 시 소유자 목록이 비워진다`() = runTest {
        viewModel.onVideoSelected("content://video/1")

        viewModel.resetAfterSave()
        assertTrue(viewModel.uiState.value.addedOwnerUris.isEmpty())
    }

    @Test
    fun `resetAfterSave 호출 시 ClearOwnersUseCase가 실행된다`() = runTest {
        viewModel.onVideoSelected("content://video/1")

        viewModel.resetAfterSave()
        verify(exactly = 1) { clearOwnersUseCase() }
    }

    @Test
    fun `resetAfterSave 호출 시 addOwnerResult도 초기화된다`() = runTest {
        viewModel.setAddOwnerResult(AddOwnerResult.Success)
        assertNotNull(viewModel.uiState.value.addOwnerResult)

        viewModel.resetAfterSave()
        assertNull(viewModel.uiState.value.addOwnerResult)
    }
}
