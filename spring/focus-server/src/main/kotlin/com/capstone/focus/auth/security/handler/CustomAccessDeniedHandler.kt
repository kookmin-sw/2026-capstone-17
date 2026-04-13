package com.capstone.focus.auth.security.handler

import com.capstone.focus.common.exception.ErrorTitle
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import java.nio.charset.StandardCharsets

class CustomAccessDeniedHandler : AccessDeniedHandler {

    private val objectMapper = ObjectMapper()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        val errorTitle = ErrorTitle.Forbidden

        response.status = errorTitle.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()

        val errorResponse = mapOf(
            "success" to false,
            "message" to errorTitle.message,
            "errorTitle" to errorTitle.errorName,
            "errorCode" to errorTitle.status.value()
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
