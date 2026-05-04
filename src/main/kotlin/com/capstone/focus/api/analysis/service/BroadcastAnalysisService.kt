package com.capstone.focus.api.analysis.service

import com.capstone.focus.api.analysis.dto.request.CompleteBroadcastAnalysisJobRequest
import com.capstone.focus.api.analysis.dto.request.CreateBroadcastAnalysisJobRequest
import com.capstone.focus.api.analysis.dto.response.BroadcastAiReportResponse
import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisJobResponse
import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisResultResponse
import com.capstone.focus.api.analysis.dto.response.BroadcastHighlightCandidateResponse
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.domain.entity.BroadcastAiReport
import com.capstone.focus.domain.entity.BroadcastAnalysisJob
import com.capstone.focus.domain.entity.BroadcastContentRatio
import com.capstone.focus.domain.entity.BroadcastMediaAsset
import com.capstone.focus.domain.entity.enum.BroadcastAiReportType
import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobType
import com.capstone.focus.domain.entity.enum.BroadcastMediaAssetType
import com.capstone.focus.domain.repository.BroadcastAiReportRepository
import com.capstone.focus.domain.repository.BroadcastAnalysisJobRepository
import com.capstone.focus.domain.repository.BroadcastHighlightCandidateRepository
import com.capstone.focus.domain.repository.BroadcastMediaAssetRepository
import com.capstone.focus.domain.repository.BroadcastRepository
import com.capstone.focus.domain.repository.TrackingSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

interface BroadcastAnalysisService {
    fun createAnalysisJob(memberId: String, broadcastId: String, request: CreateBroadcastAnalysisJobRequest): BroadcastAnalysisJobResponse
    fun queuePostStreamSummary(memberId: String, broadcastId: String): BroadcastAnalysisJobResponse
    fun completeAnalysisJob(memberId: String, broadcastId: String, analysisJobId: String, request: CompleteBroadcastAnalysisJobRequest): BroadcastAnalysisJobResponse
    fun getLatestFullSummaryJob(broadcastId: String): BroadcastAnalysisJobResponse?
    fun completeAnalysisJobInternal(broadcastId: String, analysisJobId: String, request: CompleteBroadcastAnalysisJobRequest): BroadcastAnalysisJobResponse
    fun getLatestAnalysis(memberId: String, broadcastId: String): BroadcastAnalysisResultResponse
    fun getHighlights(memberId: String, broadcastId: String): List<BroadcastHighlightCandidateResponse>
}

@Service
class BroadcastAnalysisServiceImpl(
    private val broadcastRepository: BroadcastRepository,
    private val broadcastMediaAssetRepository: BroadcastMediaAssetRepository,
    private val broadcastAnalysisJobRepository: BroadcastAnalysisJobRepository,
    private val broadcastAiReportRepository: BroadcastAiReportRepository,
    private val broadcastHighlightCandidateRepository: BroadcastHighlightCandidateRepository,
    private val trackingSessionRepository: TrackingSessionRepository
) : BroadcastAnalysisService {

    @Transactional
    override fun createAnalysisJob(
        memberId: String,
        broadcastId: String,
        request: CreateBroadcastAnalysisJobRequest
    ): BroadcastAnalysisJobResponse {
        val broadcast = getOwnedBroadcast(memberId, broadcastId)

        val mediaAsset = broadcastMediaAssetRepository.save(
            BroadcastMediaAsset(
                broadcast = broadcast,
                assetType = request.assetType,
                storageProvider = request.storageProvider,
                storageKey = request.storageKey,
                storageUrl = request.storageUrl,
                durationSec = request.durationSec,
                resolutionWidth = request.resolutionWidth,
                resolutionHeight = request.resolutionHeight,
                fileSizeBytes = request.fileSizeBytes
            )
        )

        val job = broadcastAnalysisJobRepository.save(
            BroadcastAnalysisJob(
                broadcast = broadcast,
                mediaAsset = mediaAsset,
                jobType = request.jobType
            )
        )

        if (job.jobType == BroadcastAnalysisJobType.FULL_SUMMARY) {
            job.markRunning()
            broadcastAiReportRepository.save(
                createInitialSummaryReport(
                    broadcastId = broadcast.id,
                    broadcastTitle = broadcast.title,
                    durationSec = mediaAsset.durationSec,
                    job = job,
                    request = request
                )
            )
            job.markSucceeded()
        }

        return BroadcastAnalysisJobResponse.from(job)
    }

    @Transactional
    override fun queuePostStreamSummary(memberId: String, broadcastId: String): BroadcastAnalysisJobResponse {
        val broadcast = getOwnedBroadcast(memberId, broadcastId)

        broadcastAnalysisJobRepository.findTopByBroadcastIdAndJobTypeOrderByCreatedAtDesc(
            broadcastId,
            BroadcastAnalysisJobType.FULL_SUMMARY
        )
            ?.let { return BroadcastAnalysisJobResponse.from(it) }

        val durationSec = if (broadcast.startedAt != null && broadcast.endedAt != null) {
            Duration.between(broadcast.startedAt, broadcast.endedAt).seconds.coerceAtLeast(0)
        } else {
            null
        }

        return createAnalysisJob(
            memberId = memberId,
            broadcastId = broadcastId,
            request = CreateBroadcastAnalysisJobRequest(
                assetType = BroadcastMediaAssetType.ANALYSIS_MP4,
                jobType = BroadcastAnalysisJobType.FULL_SUMMARY,
                storageProvider = "S3",
                storageKey = buildDefaultAnalysisStorageKey(broadcastId),
                durationSec = durationSec
            )
        )
    }

    @Transactional
    override fun completeAnalysisJob(
        memberId: String,
        broadcastId: String,
        analysisJobId: String,
        request: CompleteBroadcastAnalysisJobRequest
    ): BroadcastAnalysisJobResponse {
        getOwnedBroadcast(memberId, broadcastId)
        return completeAnalysisJobInternal(broadcastId, analysisJobId, request)
    }

    @Transactional(readOnly = true)
    override fun getLatestFullSummaryJob(broadcastId: String): BroadcastAnalysisJobResponse? {
        return broadcastAnalysisJobRepository.findTopByBroadcastIdAndJobTypeOrderByCreatedAtDesc(
            broadcastId,
            BroadcastAnalysisJobType.FULL_SUMMARY
        )?.let { BroadcastAnalysisJobResponse.from(it) }
    }

    @Transactional
    override fun completeAnalysisJobInternal(
        broadcastId: String,
        analysisJobId: String,
        request: CompleteBroadcastAnalysisJobRequest
    ): BroadcastAnalysisJobResponse {
        broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        val job = broadcastAnalysisJobRepository.findById(analysisJobId)
            .orElseThrow { ApiException(ErrorTitle.NotFoundBroadcast) }

        if (job.broadcast.id != broadcastId) {
            throw ApiException(ErrorTitle.NotFoundBroadcast)
        }

        job.markRunning()
        job.mediaAsset.updateAnalysisMetadata(
            storageUrl = request.storageUrl,
            durationSec = request.durationSec,
            resolutionWidth = request.resolutionWidth,
            resolutionHeight = request.resolutionHeight,
            fileSizeBytes = request.fileSizeBytes
        )

        val durationSec = request.durationSec ?: job.mediaAsset.durationSec
        val replacedFaceCount = request.faceStatistics?.totalReplacedFaceCount
            ?: trackingSessionRepository.countByBroadcastId(broadcastId)
        val readableDuration = durationSec?.let { formatDuration(it) } ?: "알 수 없는 길이"
        val contentRatios = request.contentRatios.map { it.toDomain() }
        val summaryTitle = if (job.broadcast.title.isNullOrBlank()) "방송 종료 요약" else "'${job.broadcast.title}' 방송 요약"
        val summary = request.summary ?: buildDefaultSummary(
            readableDuration = readableDuration,
            replacedFaceCount = replacedFaceCount,
            peakViewerCount = request.viewerPeakInsight?.peakViewerCount,
            contentRatios = contentRatios
        )

        val report = broadcastAiReportRepository.findByAnalysisJobId(analysisJobId)
        if (report == null) {
            broadcastAiReportRepository.save(
                BroadcastAiReport(
                    broadcast = job.broadcast,
                    analysisJob = job,
                    reportType = BroadcastAiReportType.POST_STREAM_SUMMARY,
                    title = summaryTitle,
                    summary = summary,
                    strengths = if (request.strengths.isNotEmpty()) request.strengths else defaultStrengths(durationSec, readableDuration, contentRatios),
                    weaknesses = if (request.weaknesses.isNotEmpty()) request.weaknesses else defaultWeaknessesFromComplete(request),
                    actionItems = if (request.actionItems.isNotEmpty()) request.actionItems else defaultActionItemsFromComplete(request),
                    peakViewerCount = request.viewerPeakInsight?.peakViewerCount,
                    peakViewerOccurredAt = request.viewerPeakInsight?.occurredAt,
                    peakSceneDescription = request.viewerPeakInsight?.sceneDescription,
                    totalReplacedFaceCount = replacedFaceCount,
                    maxSimultaneousCrowdCount = request.faceStatistics?.maxSimultaneousCrowdCount,
                    contentRatios = contentRatios
                )
            )
        } else {
            report.updateReport(
                title = summaryTitle,
                summary = summary,
                strengths = if (request.strengths.isNotEmpty()) request.strengths else defaultStrengths(durationSec, readableDuration, contentRatios),
                weaknesses = if (request.weaknesses.isNotEmpty()) request.weaknesses else defaultWeaknessesFromComplete(request),
                actionItems = if (request.actionItems.isNotEmpty()) request.actionItems else defaultActionItemsFromComplete(request),
                peakViewerCount = request.viewerPeakInsight?.peakViewerCount,
                peakViewerOccurredAt = request.viewerPeakInsight?.occurredAt,
                peakSceneDescription = request.viewerPeakInsight?.sceneDescription,
                totalReplacedFaceCount = replacedFaceCount,
                maxSimultaneousCrowdCount = request.faceStatistics?.maxSimultaneousCrowdCount,
                contentRatios = contentRatios
            )
        }

        job.markSucceeded()
        return BroadcastAnalysisJobResponse.from(job)
    }

    @Transactional(readOnly = true)
    override fun getLatestAnalysis(memberId: String, broadcastId: String): BroadcastAnalysisResultResponse {
        getOwnedBroadcast(memberId, broadcastId)

        val latestJob = broadcastAnalysisJobRepository.findTopByBroadcastIdOrderByCreatedAtDesc(broadcastId)
        val latestReport = broadcastAiReportRepository.findTopByBroadcastIdOrderByCreatedAtDesc(broadcastId)
        val highlightCount = latestJob?.let {
            broadcastHighlightCandidateRepository.findAllByAnalysisJobIdOrderByScoreDescStartSecAsc(it.id).size
        } ?: 0

        return BroadcastAnalysisResultResponse(
            broadcastId = broadcastId,
            latestJob = latestJob?.let { BroadcastAnalysisJobResponse.from(it) },
            latestReport = latestReport?.let { BroadcastAiReportResponse.from(it) },
            highlightCount = highlightCount
        )
    }

    @Transactional(readOnly = true)
    override fun getHighlights(memberId: String, broadcastId: String): List<BroadcastHighlightCandidateResponse> {
        getOwnedBroadcast(memberId, broadcastId)

        val latestJob = broadcastAnalysisJobRepository.findTopByBroadcastIdOrderByCreatedAtDesc(broadcastId)
            ?: return emptyList()

        return broadcastHighlightCandidateRepository.findAllByAnalysisJobIdOrderByScoreDescStartSecAsc(latestJob.id)
            .map { BroadcastHighlightCandidateResponse.from(it) }
    }

    private fun getOwnedBroadcast(memberId: String, broadcastId: String) =
        broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?.also { broadcast ->
                if (broadcast.member.id != memberId) {
                    throw ApiException(ErrorTitle.Forbidden)
                }
            }
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

    private fun createInitialSummaryReport(
        broadcastId: String,
        broadcastTitle: String?,
        durationSec: Long?,
        job: BroadcastAnalysisJob,
        request: CreateBroadcastAnalysisJobRequest
    ): BroadcastAiReport {
        val replacedFaceCount = request.faceStatistics?.totalReplacedFaceCount
            ?: trackingSessionRepository.countByBroadcastId(broadcastId)
        val readableDuration = durationSec?.let { formatDuration(it) } ?: "알 수 없는 길이"
        val summaryTitle = if (broadcastTitle.isNullOrBlank()) "방송 종료 요약" else "'$broadcastTitle' 방송 요약"
        val contentRatios = request.contentRatios.map { it.toDomain() }
        val summary = request.summary ?: buildDefaultSummary(
            readableDuration = readableDuration,
            replacedFaceCount = replacedFaceCount,
            peakViewerCount = request.viewerPeakInsight?.peakViewerCount,
            contentRatios = contentRatios
        )

        return BroadcastAiReport(
            broadcast = job.broadcast,
            analysisJob = job,
            reportType = BroadcastAiReportType.POST_STREAM_SUMMARY,
            title = summaryTitle,
            summary = summary,
            strengths = if (request.strengths.isNotEmpty()) request.strengths else defaultStrengths(durationSec, readableDuration, contentRatios),
            weaknesses = if (request.weaknesses.isNotEmpty()) request.weaknesses else defaultWeaknesses(request),
            actionItems = if (request.actionItems.isNotEmpty()) request.actionItems else defaultActionItems(request),
            peakViewerCount = request.viewerPeakInsight?.peakViewerCount,
            peakViewerOccurredAt = request.viewerPeakInsight?.occurredAt,
            peakSceneDescription = request.viewerPeakInsight?.sceneDescription,
            totalReplacedFaceCount = replacedFaceCount,
            maxSimultaneousCrowdCount = request.faceStatistics?.maxSimultaneousCrowdCount,
            contentRatios = contentRatios
        )
    }

    private fun mediaAssetSummary(durationSec: Long?): String? {
        return durationSec?.takeIf { it > 0 }?.let {
            "분석용 MP4가 등록되어 후속 Gemini 요약 파이프라인을 바로 연결할 수 있습니다."
        }
    }

    private fun buildDefaultSummary(
        readableDuration: String,
        replacedFaceCount: Long,
        peakViewerCount: Long?,
        contentRatios: List<BroadcastContentRatio>
    ): String {
        val peakClause = peakViewerCount?.let { "최고 시청자 수는 ${it}명이었습니다." }
            ?: "시청자 최고점 데이터는 아직 집계되지 않았습니다."
        val contentClause = contentRatios.takeIf { it.isNotEmpty() }
            ?.sortedByDescending { it.percentage }
            ?.take(2)
            ?.joinToString(", ") { "${it.contentType} ${it.percentage}%" }
            ?.let { "주요 콘텐츠 비율은 $it 입니다." }
            ?: "콘텐츠 비율 분석은 아직 비어 있습니다."

        return "총 $readableDuration 분량의 분석용 영상이 등록되었습니다. 현재 기준으로 확인 가능한 타인 얼굴 아바타 치환 세션은 ${replacedFaceCount}건이며, $peakClause $contentClause"
    }

    private fun defaultStrengths(
        durationSec: Long?,
        readableDuration: String,
        contentRatios: List<BroadcastContentRatio>
    ): List<String> {
        return buildList {
            durationSec?.takeIf { it > 0 }?.let {
                add("분석 가능한 방송 길이($readableDuration)가 확보되었습니다.")
            }
            mediaAssetSummary(durationSec)?.let { add(it) }
            if (contentRatios.isNotEmpty()) {
                add("콘텐츠 비율 데이터가 함께 저장되어 후속 리포트 확장이 쉬워졌습니다.")
            }
        }
    }

    private fun defaultWeaknesses(request: CreateBroadcastAnalysisJobRequest): List<String> {
        return buildList {
            if (request.viewerPeakInsight == null) {
                add("시청자 최고점 데이터 집계는 아직 연결되지 않았습니다.")
            }
            if (request.contentRatios.isEmpty()) {
                add("콘텐츠 비율 분석은 Gemini 분류 로직 연동 전이라 비어 있습니다.")
            }
        }
    }

    private fun defaultActionItems(request: CreateBroadcastAnalysisJobRequest): List<String> {
        return buildList {
            if (request.viewerPeakInsight == null) {
                add("시청자 피크 집계 로직을 연결해 최고 반응 시점을 리포트에 포함하세요.")
            }
            if (request.contentRatios.isEmpty()) {
                add("영상 세그먼트 분류를 붙여 이동, 소통, 식사 비율을 계산하세요.")
            }
        }
    }

    private fun defaultWeaknessesFromComplete(request: CompleteBroadcastAnalysisJobRequest): List<String> {
        return buildList {
            if (request.viewerPeakInsight == null) {
                add("시청자 최고점 데이터 집계는 아직 연결되지 않았습니다.")
            }
            if (request.contentRatios.isEmpty()) {
                add("콘텐츠 비율 분석은 Gemini 분류 로직 연동 전이라 비어 있습니다.")
            }
        }
    }

    private fun defaultActionItemsFromComplete(request: CompleteBroadcastAnalysisJobRequest): List<String> {
        return buildList {
            if (request.viewerPeakInsight == null) {
                add("시청자 피크 집계 로직을 연결해 최고 반응 시점을 리포트에 포함하세요.")
            }
            if (request.contentRatios.isEmpty()) {
                add("영상 세그먼트 분류를 붙여 이동, 소통, 식사 비율을 계산하세요.")
            }
        }
    }

    private fun buildDefaultAnalysisStorageKey(broadcastId: String): String {
        return "broadcasts/$broadcastId/archive/analysis.mp4"
    }

    private fun formatDuration(durationSec: Long): String {
        val duration = Duration.ofSeconds(durationSec)
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        return when {
            hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
            hours > 0 -> "${hours}시간"
            else -> "${duration.toMinutes()}분"
        }
    }
}
