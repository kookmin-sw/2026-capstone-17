package com.kmu_focus.focusandroid.feature.detection.domain.entity

import org.junit.Assert.*
import org.junit.Test

class Face3DMMCoeffsTest {

    @Test
    fun `equals - 동일 id exp pose면 true`() {
        val a = Face3DMMCoeffs(floatArrayOf(1f, 2f), floatArrayOf(3f), floatArrayOf(4f, 5f))
        val b = Face3DMMCoeffs(floatArrayOf(1f, 2f), floatArrayOf(3f), floatArrayOf(4f, 5f))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals - id 다르면 false`() {
        val a = Face3DMMCoeffs(floatArrayOf(1f), floatArrayOf(3f), floatArrayOf(4f))
        val b = Face3DMMCoeffs(floatArrayOf(9f), floatArrayOf(3f), floatArrayOf(4f))
        assertNotEquals(a, b)
    }
}
