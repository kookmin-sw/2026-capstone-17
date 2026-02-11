package com.capstone.focus.auth.security.handler

import com.capstone.focus.common.exception.ErrorTitle
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import java.nio.charset.StandardCharsets

class JwtAuthenticationEntryPointHandler : AuthenticationEntryPoint {

    private val objectMapper = ObjectMapper()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val errorTitle = ErrorTitle.LoginRequired

        response.status = errorTitle.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()

        val errorResponse = mapOf(
            "success" to false,
            "message" to errorTitle.message,
            "errorName" to errorTitle.errorName,
            "data" to null
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}