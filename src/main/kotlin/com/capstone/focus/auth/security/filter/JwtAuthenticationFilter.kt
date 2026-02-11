package com.capstone.focus.auth.security.filter

import com.capstone.focus.auth.jwt.JwtService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = getTokenFromRequestHeader(request)

        if (token != null) {
            try {
                val authentication: Authentication = getAuthentication(token)
                SecurityContextHolder.getContext().authentication = authentication
            } catch (e: ApiException) {
                when (e.errorTitle) {
                    ErrorTitle.ExpiredToken -> throw ApiException(ErrorTitle.ExpiredToken)
                    else -> throw ApiException(ErrorTitle.InvalidToken)
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun getAuthentication(accessToken: String): Authentication {
        val claims = jwtService.getClaimsFromJwt(accessToken)
        val userName: String = (claims["name"] as? String)
            ?: (claims["nickname"] as? String)
            ?: "Unknown"
        val userId: String = claims.subject

        val userDetails = FocusMemberDetails(userId, userName)
        return UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
    }

    private fun getTokenFromRequestHeader(request: HttpServletRequest): String? {
        val authorization = request.getHeader("Authorization")
        val regex = Regex("^Bearer .*")
        return authorization?.let { if (regex.matches(it)) it.replace("^Bearer( )*".toRegex(), "") else null }
    }
}