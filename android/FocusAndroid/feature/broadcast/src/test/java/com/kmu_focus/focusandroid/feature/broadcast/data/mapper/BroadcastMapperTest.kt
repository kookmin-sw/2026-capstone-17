package com.kmu_focus.focusandroid.feature.broadcast.data.mapper

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BroadcastMapperTest {

    @Test
    fun `READY 상태 DTO를 Entity로 변환한다`() {
        val dto = BroadcastResponseDto(
            broadcastId = "broadcast-1",
            title = "테스트 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = "READY",
            streamKey = "stream-key-abc",
            hlsUrl = null,
            startedAt = null,
            endedAt = null,
        )

        val entity = dto.toEntity()

        assertEquals("broadcast-1", entity.broadcastId)
        assertEquals("테스트 방송", entity.title)
        assertEquals("홍길동", entity.memberName)
        assertEquals("member-1", entity.memberId)
        assertEquals(BroadcastStatus.READY, entity.status)
        assertEquals("stream-key-abc", entity.streamKey)
        assertNull(entity.hlsUrl)
        assertNull(entity.startedAt)
        assertNull(entity.endedAt)
    }

    @Test
    fun `ON_AIR 상태 DTO 변환 시 hlsUrl과 startedAt이 매핑된다`() {
        val dto = BroadcastResponseDto(
            broadcastId = "broadcast-1",
            title = "라이브 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = "ON_AIR",
            streamKey = "stream-key-abc",
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
            startedAt = "2026-04-09T12:00:00",
            endedAt = null,
        )

        val entity = dto.toEntity()

        assertEquals(BroadcastStatus.ON_AIR, entity.status)
        assertEquals("https://cdn.example.com/live/broadcast-1.m3u8", entity.hlsUrl)
        assertEquals("2026-04-09T12:00:00", entity.startedAt)
    }

    @Test
    fun `ENDED 상태 DTO 변환 시 endedAt이 매핑된다`() {
        val dto = BroadcastResponseDto(
            broadcastId = "broadcast-1",
            title = "종료된 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = "ENDED",
            streamKey = "stream-key-abc",
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
            startedAt = "2026-04-09T12:00:00",
            endedAt = "2026-04-09T13:00:00",
        )

        val entity = dto.toEntity()

        assertEquals(BroadcastStatus.ENDED, entity.status)
        assertEquals("2026-04-09T13:00:00", entity.endedAt)
    }

    @Test
    fun `ERROR 상태 DTO를 올바르게 변환한다`() {
        val dto = BroadcastResponseDto(
            broadcastId = "broadcast-1",
            title = "에러 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = "ERROR",
            streamKey = "stream-key-abc",
            hlsUrl = null,
            startedAt = null,
            endedAt = null,
        )

        val entity = dto.toEntity()

        assertEquals(BroadcastStatus.ERROR, entity.status)
    }

    @Test
    fun `알 수 없는 status 문자열은 ERROR로 매핑된다`() {
        val dto = BroadcastResponseDto(
            broadcastId = "broadcast-1",
            title = "알 수 없는 상태",
            memberName = "홍길동",
            memberId = "member-1",
            status = "UNKNOWN_STATUS",
            streamKey = "stream-key-abc",
            hlsUrl = null,
            startedAt = null,
            endedAt = null,
        )

        val entity = dto.toEntity()

        assertEquals(BroadcastStatus.ERROR, entity.status)
    }
}
