package com.capstone.focus.auth.swagger

import com.capstone.focus.common.common.annotations.FocusDeleteMapping
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPatchMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.annotations.FocusPutMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.exception.annotation.CustomFailResponseAnnotation
import com.capstone.focus.common.exception.annotation.CustomFailResponseAnnotations
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import io.swagger.v3.oas.models.responses.ApiResponse as SwaggerApiResponse

@Component
class CustomOperationCustomizer : OperationCustomizer {
    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val methodAnnotations = handlerMethod.method.declaredAnnotations
        val responses = operation.responses

        if (methodAnnotations.any { it is Hidden }) {
            return operation
        }

        for (annotation in methodAnnotations) {
            when (annotation) {
                is CustomFailResponseAnnotations -> {
                    annotation.value.forEach { j ->
                        val message = if (j.message.isBlank()) j.exception.message else j.message
                        handleCustomFailResponse(j.exception, message, responses)
                    }
                }
                is CustomFailResponseAnnotation -> {
                    val message = if (annotation.message.isBlank()) annotation.exception.message else annotation.message
                    handleCustomFailResponse(annotation.exception, message, responses)
                }

                is FocusGetMapping -> addSecurityItemIfRequired(operation, annotation.authenticated, annotation.hasRole)
                is FocusDeleteMapping -> addSecurityItemIfRequired(operation, annotation.authenticated, annotation.hasRole)
                is FocusPostMapping -> addSecurityItemIfRequired(operation, annotation.authenticated, annotation.hasRole)
                is FocusPatchMapping -> addSecurityItemIfRequired(operation, annotation.authenticated, annotation.hasRole)
                is FocusPutMapping -> addSecurityItemIfRequired(operation, annotation.authenticated, annotation.hasRole)
            }
        }

        operation.responses(responses)
        return operation
    }

    private fun addSecurityItemIfRequired(operation: Operation, authenticated: Boolean, hasRole: Array<out String>) {
        if (authenticated || hasRole.isNotEmpty()) {
            operation.addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        }
    }

    private fun handleCustomFailResponse(
        exception: ErrorTitle,
        message: String?,
        responses: ApiResponses
    ) {
        val statusCode = exception.status.value().toString()
        val response = responses.computeIfAbsent(statusCode) { SwaggerApiResponse() }
        val content = response.content ?: Content()

        val schema = Schema<Any>().`$ref`("#/components/schemas/ApiFailureResponse")

        val errorResponse = ApiResponse.Failure(
            message = message ?: exception.message,
            errorTitle = exception.toString(),
            errorCode = exception.status.value()
        )

        val mediaType = content.getOrPut("application/json") { MediaType().schema(schema) }
        val example = Example().value(errorResponse)

        mediaType.addExamples(errorResponse.errorTitle, example)
        content["application/json"] = mediaType
        response.content(content)
        responses.addApiResponse(statusCode, response)
    }
}