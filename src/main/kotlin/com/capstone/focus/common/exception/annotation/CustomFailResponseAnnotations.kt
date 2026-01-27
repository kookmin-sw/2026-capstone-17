package com.capstone.focus.common.exception.annotation

import java.lang.annotation.Inherited

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class CustomFailResponseAnnotations(vararg val value: CustomFailResponseAnnotation = []
)