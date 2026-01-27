package com.capstone.focus.common.exception.annotation

import com.capstone.focus.common.exception.ErrorTitle

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(CustomFailResponseAnnotations::class)
annotation class CustomFailResponseAnnotation(val exception: ErrorTitle, val message: String = "")

