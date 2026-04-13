package com.capstone.focus.auth.jwt

import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.common.key}")
    private val jwtCommonKey: String
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtCommonKey))
    }

    fun createJwt(subject: String, customClaims: Map<String, Any>? = null, audience: String? = null): String {
        return Jwts.builder()
            .subject(subject)
            .issuer("focus")
            .apply { audience?.let { audience().add(it) } }
            .signWith(key, Jwts.SIG.HS512)
            .expiration(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 3))
            .claims(customClaims ?: emptyMap<String, Any>())
            .compact()
    }

    fun getClaimsFromJwt(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw ApiException(ErrorTitle.ExpiredToken)
        } catch (e: Exception) {
            throw ApiException(ErrorTitle.InvalidToken)
        }
    }

    fun createRefreshToken(): String {
        return UUID.randomUUID().toString()
    }
}