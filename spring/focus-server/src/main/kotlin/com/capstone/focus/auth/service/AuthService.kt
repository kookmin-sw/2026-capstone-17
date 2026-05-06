package com.capstone.focus.auth.service

import com.capstone.focus.auth.dto.request.KakaoLoginRequest
import com.capstone.focus.auth.dto.request.RefreshTokenRequest
import com.capstone.focus.auth.dto.response.TokenResponse
import com.capstone.focus.auth.jwt.JwtService
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.kakao.KakaoOAuthClient
import com.capstone.focus.common.external.redis.RedisService
import com.capstone.focus.domain.MemberService
import com.capstone.focus.domain.entity.Member
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

interface AuthService {
    fun kakaoLogin(request: KakaoLoginRequest): TokenResponse
    fun refresh(request: RefreshTokenRequest): TokenResponse
}

@Service
class AuthServiceImpl(
    private val memberService: MemberService,
    private val jwtService: JwtService,
    private val redisService: RedisService,
    private val kakaoOAuthClient: KakaoOAuthClient
) : AuthService {

    @Transactional
    override fun kakaoLogin(request: KakaoLoginRequest): TokenResponse {
        val kakaoAccessToken = request.accessToken.takeIf { it.isNotBlank() }
            ?: throw ApiException(ErrorTitle.InvalidInputValue, "카카오 로그인에는 access token이 필요합니다.")
            
        val kakaoUserInfo = kakaoOAuthClient.getUserInfo(kakaoAccessToken)

        val kakaoId = kakaoUserInfo.id
        val nickname = kakaoUserInfo.kakaoAccount?.profile?.nickname
            ?: kakaoUserInfo.properties?.nickname
            ?: "FocusUser"
        val email = kakaoUserInfo.kakaoAccount?.email
        val profileImageUrl = kakaoUserInfo.kakaoAccount?.profile?.profileImageUrl
            ?: kakaoUserInfo.properties?.profileImage

        val member = memberService.createOrUpdateKakaoMember(kakaoId, nickname, email, profileImageUrl)
        return handleTokenCreation(member)
    }

    override fun refresh(request: RefreshTokenRequest): TokenResponse {
        val memberId = redisService.getUserIdByRefreshToken(request.refreshToken)
            ?: throw ApiException(ErrorTitle.InvalidToken)

        val member = memberService.getMemberById(memberId)
        redisService.deleteRefreshToken(request.refreshToken)

        return handleTokenCreation(member)
    }

    private fun handleTokenCreation(member: Member): TokenResponse {
        val claims = createClaims(member)
        val refreshToken = createAndSaveRefreshToken(member.id)

        return TokenResponse(
            accessToken = jwtService.createJwt(member.id, claims),
            refreshToken = refreshToken
        )
    }

    private fun createClaims(member: Member): Map<String, Any> {
        return mapOf(
            "nickname" to (member.nickname ?: ""),
            "role" to member.role.name
        )
    }

    private fun createAndSaveRefreshToken(memberId: String): String {
        val refreshToken = jwtService.createRefreshToken()
        redisService.saveRefreshToken(memberId, refreshToken, 14L, TimeUnit.DAYS)
        return refreshToken
    }
}
