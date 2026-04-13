package com.capstone.focus.common.common.annotations

import org.springframework.core.annotation.AliasFor
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@RequestMapping(
    method = [RequestMethod.POST],
)
annotation class FocusPostMapping(
    @get:AliasFor(annotation = RequestMapping::class)
    vararg val value: String = [],
    @get:AliasFor(annotation = RequestMapping::class)
    val path: Array<String> = [],
    @get:AliasFor(annotation = RequestMapping::class)
    val params: Array<String> = [],
    @get:AliasFor(annotation = RequestMapping::class)
    val headers: Array<String> = [],
    @get:AliasFor(annotation = RequestMapping::class)
    val consumes: Array<String> = [],
    @get:AliasFor(annotation = RequestMapping::class)
    val produces: Array<String> = [],
    val hasRole: Array<String> = [],
    val authenticated: Boolean = false,
    @get:AliasFor(annotation = RequestMapping::class)
    val name: String = ""
)
