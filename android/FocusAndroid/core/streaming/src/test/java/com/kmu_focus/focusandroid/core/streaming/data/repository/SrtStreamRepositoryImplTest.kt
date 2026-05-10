package com.kmu_focus.focusandroid.core.streaming.data.repository

import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.core.streaming.domain.repository.SrtStreamRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SrtStreamRepositoryImplTest {

    private lateinit var repository: SrtStreamRepositoryImpl

    private val config = SrtConnectionConfig(
        host = "13.125.126.120",
        port = 8890,
        streamKey = "test-stream-key",
    )

    @Before
    fun setup() {
        repository = SrtStreamRepositoryImpl()
    }

    @Test
    fun `초기 connectionState는 DISCONNECTED이다`() {
        assertEquals(SrtConnectionState.DISCONNECTED, repository.connectionState.value)
    }

    @Test
    fun `createMuxerFactory는 null이 아닌 Factory를 반환한다`() {
        val factory = repository.createMuxerFactory(config)

        assertNotNull(factory)
    }

    @Test
    fun `SrtConnectionConfig의 streamId는 publish_live_streamKey 형식이다`() {
        assertEquals("publish:live/test-stream-key", config.streamId)
    }

    @Test
    fun `disconnect 호출 시 connectionState가 DISCONNECTED로 변경된다`() = runTest {
        repository.disconnect()

        assertEquals(SrtConnectionState.DISCONNECTED, repository.connectionState.value)
    }
}
