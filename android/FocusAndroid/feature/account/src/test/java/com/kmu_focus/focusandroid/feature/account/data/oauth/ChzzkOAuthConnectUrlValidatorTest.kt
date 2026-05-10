package com.kmu_focus.focusandroid.feature.account.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChzzkOAuthConnectUrlValidatorTest {

    private lateinit var validator: ChzzkOAuthConnectUrlValidator

    @Before
    fun setup() {
        validator = ChzzkOAuthConnectUrlValidator(
            config = ChzzkOAuthConfig(
                clientId = "c1f78a8f-aee0-487d-ae80-36ff9e627092",
                redirectUri = "http://3.35.202.126:8080/api/v1/platforms/chzzk/callback",
                authBaseUrl = "https://chzzk.naver.com/account-interlock",
            ),
        )
    }

    @Test
    fun `유효한 치지직 OAuth URL은 그대로 통과한다`() {
        val result = validator.validate(
            "https://chzzk.naver.com/account-interlock?" +
                "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
                "redirectUri=http%3A%2F%2F3.35.202.126%3A8080%2Fapi%2Fv1%2Fplatforms%2Fchzzk%2Fcallback&" +
                "state=test-state-123",
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "https://chzzk.naver.com/account-interlock?" +
                "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
                "redirectUri=http%3A%2F%2F3.35.202.126%3A8080%2Fapi%2Fv1%2Fplatforms%2Fchzzk%2Fcallback&" +
                "state=test-state-123",
            result.getOrNull(),
        )
    }

    @Test
    fun `state가 없는 OAuth URL은 실패한다`() {
        val result = validator.validate(
            "https://chzzk.naver.com/account-interlock?" +
                "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
                "redirectUri=http%3A%2F%2F3.35.202.126%3A8080%2Fapi%2Fv1%2Fplatforms%2Fchzzk%2Fcallback",
        )

        assertTrue(result.isFailure)
        assertEquals("치지직 OAuth URL에 state 값이 없습니다.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `redirectUri가 local properties 설정과 다르면 실패한다`() {
        val result = validator.validate(
            "https://chzzk.naver.com/account-interlock?" +
                "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
                "redirectUri=https%3A%2F%2Fexample.com%2Fcallback&" +
                "state=test-state-123",
        )

        assertTrue(result.isFailure)
        assertEquals(
            "치지직 OAuth URL의 redirectUri가 local.properties 설정과 다릅니다.",
            result.exceptionOrNull()?.message,
        )
    }
}
