package com.kmu_focus.focusandroid.feature.account.presentation.mypage

import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile
import com.kmu_focus.focusandroid.feature.account.domain.usecase.DisconnectChzzkUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetCurrentUserUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetChzzkConnectUrlUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.GetChzzkConnectionStatusUseCase
import com.kmu_focus.focusandroid.feature.account.domain.usecase.LogoutUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageViewModelTest {

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getChzzkConnectionStatusUseCase: GetChzzkConnectionStatusUseCase
    private lateinit var getChzzkConnectUrlUseCase: GetChzzkConnectUrlUseCase
    private lateinit var disconnectChzzkUseCase: DisconnectChzzkUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var viewModel: MyPageViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUserUseCase = mockk()
        getChzzkConnectionStatusUseCase = mockk()
        getChzzkConnectUrlUseCase = mockk()
        disconnectChzzkUseCase = mockk()
        logoutUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `사용자 정보 조회 성공 시 프로필이 표시된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = "user@example.com",
                profileImageUrl = "https://example.com/profile.png",
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(connected = false),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("홍길동", state.profile?.name)
        assertEquals("user@example.com", state.profile?.email)
        assertFalse(state.isChzzkLoading)
        assertFalse(state.chzzkStatus?.connected ?: true)
        assertNull(state.error)
    }

    @Test
    fun `사용자 정보 조회 실패 시 에러가 설정된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.failure(RuntimeException("내 정보 조회 실패"))
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(connected = false),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.profile)
        assertEquals("내 정보 조회 실패", state.error)
    }

    @Test
    fun `로그아웃 성공 시 loggedOut 상태가 된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(connected = false),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )
        viewModel.logout()

        val state = viewModel.uiState.value
        assertFalse(state.isLoggingOut)
        assertTrue(state.isLoggedOut)
        assertNull(state.error)
    }

    @Test
    fun `로그아웃 실패 시 에러가 설정되고 loggedOut은 false다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(connected = false),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.failure(RuntimeException("로그아웃 실패"))

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )
        viewModel.logout()

        val state = viewModel.uiState.value
        assertFalse(state.isLoggingOut)
        assertFalse(state.isLoggedOut)
        assertEquals("로그아웃 실패", state.error)
    }

    @Test
    fun `치지직 상태 조회 성공 시 연결 상태가 표시된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(
                connected = true,
                channelId = "channel-1",
                channelName = "포커스 채널",
                watchUrl = "https://chzzk.naver.com/live/1",
            ),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )

        val state = viewModel.uiState.value
        assertFalse(state.isChzzkLoading)
        assertTrue(state.chzzkStatus?.connected == true)
        assertEquals("포커스 채널", state.chzzkStatus?.channelName)
    }

    @Test
    fun `치지직 연동 시작 성공 시 브라우저 URL 이벤트가 설정된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returns Result.success(
            ChzzkConnectionStatus(connected = false),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com/chzzk")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )
        viewModel.startChzzkConnect()

        assertEquals("https://auth.example.com/chzzk", viewModel.uiState.value.pendingExternalUrl)
        assertTrue(viewModel.uiState.value.isAwaitingChzzkConnection)

        viewModel.consumePendingExternalUrl()
        assertNull(viewModel.uiState.value.pendingExternalUrl)
    }

    @Test
    fun `치지직 연결 상태가 확인되면 연동 대기 상태가 해제된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returnsMany listOf(
            Result.success(ChzzkConnectionStatus(connected = false)),
            Result.success(
                ChzzkConnectionStatus(
                    connected = true,
                    channelId = "channel-1",
                    channelName = "포커스 채널",
                ),
            ),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com/chzzk")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )

        viewModel.startChzzkConnect()
        assertTrue(viewModel.uiState.value.isAwaitingChzzkConnection)

        viewModel.refreshChzzkStatus()

        val state = viewModel.uiState.value
        assertTrue(state.chzzkStatus?.connected == true)
        assertFalse(state.isAwaitingChzzkConnection)
    }

    @Test
    fun `치지직 연동 해제 성공 시 연결 상태가 해제된다`() = runTest {
        coEvery { getCurrentUserUseCase() } returns Result.success(
            UserProfile(
                id = "member-1",
                name = "홍길동",
                email = null,
                profileImageUrl = null,
            ),
        )
        coEvery { getChzzkConnectionStatusUseCase() } returnsMany listOf(
            Result.success(
                ChzzkConnectionStatus(
                    connected = true,
                    channelId = "channel-1",
                    channelName = "포커스 채널",
                ),
            ),
            Result.success(ChzzkConnectionStatus(connected = false)),
        )
        coEvery { getChzzkConnectUrlUseCase() } returns Result.success("https://auth.example.com/chzzk")
        coEvery { disconnectChzzkUseCase() } returns Result.success(Unit)
        coEvery { logoutUseCase() } returns Result.success(Unit)

        viewModel = MyPageViewModel(
            getCurrentUserUseCase,
            getChzzkConnectionStatusUseCase,
            getChzzkConnectUrlUseCase,
            disconnectChzzkUseCase,
            logoutUseCase,
        )
        viewModel.disconnectChzzk()

        val state = viewModel.uiState.value
        assertFalse(state.isChzzkActionInProgress)
        assertFalse(state.chzzkStatus?.connected ?: true)
    }
}
