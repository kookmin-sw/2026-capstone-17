package com.capstone.focus.api.analysis.service

import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisContextResponse
import com.capstone.focus.api.platform.service.ChzzkPlatformService
import com.capstone.focus.common.config.AnalysisSnapshotProperties
import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.BroadcastContentRatio
import com.capstone.focus.domain.entity.BroadcastPlatformSnapshot
import com.capstone.focus.domain.entity.enum.BroadcastStatus
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import com.capstone.focus.domain.repository.BroadcastPlatformSnapshotRepository
import com.capstone.focus.domain.repository.BroadcastRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface BroadcastPlatformSnapshotService {
    fun getAnalysisContext(broadcastId: String): BroadcastAnalysisContextResponse
}

@Service
class BroadcastPlatformSnapshotServiceImpl(
    private val broadcastRepository: BroadcastRepository,
    private val broadcastPlatformSnapshotRepository: BroadcastPlatformSnapshotRepository,
    private val chzzkPlatformService: ChzzkPlatformService,
    private val analysisSnapshotProperties: AnalysisSnapshotProperties
) : BroadcastPlatformSnapshotService {

    private val logger = LoggerFactory.getLogger(BroadcastPlatformSnapshotServiceImpl::class.java)

    @Scheduled(fixedDelayString = "\${focus.analysis.snapshot.fixed-delay-ms:60000}")
    @Transactional
    fun captureOnAirChzzkSnapshots() {
        if (!analysisSnapshotProperties.enabled) {
            return
        }

        val broadcasts = broadcastRepository.findAllByStatusAndPlatformAndDeletedAtIsNull(
            status = BroadcastStatus.ON_AIR,
            platform = StreamingPlatform.CHZZK
        )

        broadcasts
            .filter { !it.platformChannelId.isNullOrBlank() }
            .forEach { broadcast ->
                runCatching { captureSnapshot(broadcast) }
                    .onFailure { exception ->
                        logger.warn("Failed to capture CHZZK snapshot. broadcastId={}", broadcast.id, exception)
                    }
            }
    }

    @Transactional(readOnly = true)
    override fun getAnalysisContext(broadcastId: String): BroadcastAnalysisContextResponse {
        val snapshots = broadcastPlatformSnapshotRepository.findAllByBroadcastIdOrderBySampledAtAsc(broadcastId)
        val peakSnapshot = snapshots
            .filter { it.concurrentUserCount != null }
            .maxWithOrNull(compareBy<BroadcastPlatformSnapshot> { it.concurrentUserCount ?: Long.MIN_VALUE }.thenBy { it.sampledAt })

        val groupedDurations = snapshots
            .groupingBy { snapshotLabel(it) }
            .eachCount()
            .mapValues { (_, count) -> count.toLong() * analysisSnapshotProperties.sampleDurationSec() }

        val totalDurationSec = groupedDurations.values.sum()
        val contentRatios = groupedDurations.entries
            .sortedByDescending { it.value }
            .map { (contentType, durationSec) ->
                BroadcastContentRatio(
                    contentType = contentType,
                    percentage = if (totalDurationSec == 0L) 0.0 else durationSec.toDouble() * 100.0 / totalDurationSec.toDouble(),
                    durationSec = durationSec
                )
            }

        return BroadcastAnalysisContextResponse.of(
            broadcastId = broadcastId,
            peakViewerCount = peakSnapshot?.concurrentUserCount,
            peakOccurredAt = peakSnapshot?.sampledAt,
            peakSceneDescription = null,
            contentRatios = contentRatios,
            sampledSnapshotCount = snapshots.size,
            lastSampledAt = snapshots.lastOrNull()?.sampledAt
        )
    }

    private fun captureSnapshot(broadcast: Broadcast) {
        val channelId = broadcast.platformChannelId ?: return
        val liveSnapshot = chzzkPlatformService.getCurrentLiveSnapshot(channelId)

        broadcastPlatformSnapshotRepository.save(
            BroadcastPlatformSnapshot(
                broadcast = broadcast,
                sampledAt = LocalDateTime.now(),
                concurrentUserCount = liveSnapshot?.concurrentUserCount,
                categoryType = liveSnapshot?.categoryType,
                categoryId = liveSnapshot?.liveCategoryId,
                categoryName = liveSnapshot?.liveCategoryName,
                liveTitle = liveSnapshot?.liveTitle
            )
        )
    }

    private fun snapshotLabel(snapshot: BroadcastPlatformSnapshot): String {
        return snapshot.categoryName
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.categoryType?.takeIf { it.isNotBlank() }
            ?: "미분류"
    }
}
