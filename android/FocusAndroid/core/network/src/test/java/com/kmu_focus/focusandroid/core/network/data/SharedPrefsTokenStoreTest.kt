package com.kmu_focus.focusandroid.core.network.data

import android.content.SharedPreferences
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SharedPrefsTokenStoreTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var tokenStore: TokenStore

    @Before
    fun setup() {
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)

        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor

        tokenStore = SharedPrefsTokenStore(sharedPreferences)
    }

    @Test
    fun `토큰 저장 후 조회가 가능하다`() = runTest {
        every { sharedPreferences.getString("access_token", null) } returns "access_123"
        every { sharedPreferences.getString("refresh_token", null) } returns "refresh_456"

        tokenStore.save("access_123", "refresh_456")

        verify {
            editor.putString("access_token", "access_123")
            editor.putString("refresh_token", "refresh_456")
            editor.apply()
        }
        assertEquals("access_123", tokenStore.getAccessToken())
        assertEquals("refresh_456", tokenStore.getRefreshToken())
    }

    @Test
    fun `토큰이 없으면 null을 반환한다`() = runTest {
        every { sharedPreferences.getString("access_token", null) } returns null
        every { sharedPreferences.getString("refresh_token", null) } returns null

        assertNull(tokenStore.getAccessToken())
        assertNull(tokenStore.getRefreshToken())
    }

    @Test
    fun `clear 호출 시 모든 토큰이 삭제된다`() = runTest {
        every { editor.clear() } returns editor

        tokenStore.clear()

        verify {
            editor.clear()
            editor.apply()
        }
    }
}
