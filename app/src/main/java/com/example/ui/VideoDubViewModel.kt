package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.BurmeseTtsService
import com.example.ai.DubAiRepository
import com.example.ai.LocalAndroidTtsSynthesizer
import com.example.ai.VoiceCloneRepository
import com.example.data.VoiceCloneStore
import com.example.firebase.GeminiResilienceHelper
import com.example.media.VideoDubExporter
import com.example.media.VideoExportStorage
import com.example.media.VideoProcessor
import com.example.media.WavPreviewPlayer
import java.io.IOException
import com.example.model.ActiveVoiceSelection
import com.example.model.ClonedVoiceProfile
import com.example.model.CopyrightShieldConfig
import com.example.model.CopyrightShieldPreset
import com.example.model.DubNarrationStyle
import com.example.model.DubSegment
import com.example.model.DubTranslationPlan
import com.example.model.DubVoicePreset
import com.example.model.RenderedDubVideo
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.model.VideoClipSummary
import com.example.model.VoiceCategory
import com.example.model.WatermarkBadgeStyle
import com.example.model.WatermarkConfig
import com.example.model.WatermarkMotionStyle
import com.example.model.WatermarkPositionPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface DubPipelineState {
    data object Idle : DubPipelineState
    data class Translating(
        val stageMessage: String,
        val progressPercent: Int = 12
    ) : DubPipelineState
    data class SynthesizingVoice(
        val completed: Int,
        val total: Int,
        val currentLine: String,
        val statusNote: String? = null
    ) : DubPipelineState {
        val overallProgressPercent: Int
            get() {
                val safeTotal = total.coerceAtLeast(1)
                val frac = (completed.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
                return (34 + (frac * 36f).toInt()).coerceIn(34, 70)
            }
    }
    data class RenderingVideo(val progressPercent: Int) : DubPipelineState {
        val overallProgressPercent: Int
            get() = (70 + (progressPercent.coerceIn(0, 100) * 0.30f).toInt()).coerceIn(70, 100)
    }
    data class Completed(val output: RenderedDubVideo) : DubPipelineState
    data class Error(val message: String) : DubPipelineState
}

data class VideoDubUiState(
    val selectedVideo: VideoClipSummary? = null,
    val isInspectingVideo: Boolean = false,
    val voiceCategory: VoiceCategory = VoiceCategory.ALL,
    val selectedVoice: ActiveVoiceSelection = ActiveVoiceSelection.BuiltIn(DubVoicePreset.PUCK),
    val clonedVoices: List<ClonedVoiceProfile> = emptyList(),
    val isCreatingVoiceClone: Boolean = false,
    val voiceCloneError: String? = null,
    val isPreviewingVoice: Boolean = false,
    val previewingVoiceId: String? = null,
    val speakingSpeedMultiplier: Float = 1.0f,
    val pitchFineTuneRatio: Float = 1.0f,
    val voiceVolumeBoost: Boolean = true,
    val conciseSubtitles: Boolean = true,
    val burnSubtitles: Boolean = true,
    val subtitleStyle: SubtitleThemeStyle = SubtitleThemeStyle.CINEMA_PILL,
    val subtitleSize: SubtitleSizeOption = SubtitleSizeOption.MEDIUM,
    val subtitlePosition: SubtitlePositionOption = SubtitlePositionOption.BOTTOM,
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val copyrightShieldConfig: CopyrightShieldConfig = CopyrightShieldConfig(),
    val keepSoftOriginalBgm: Boolean = false,
    val continuousFullVideoDub: Boolean = true,
    val narrationStyle: DubNarrationStyle = DubNarrationStyle.STORYTELLER,
    val segments: List<DubSegment> = emptyList(),
    val detectedLanguage: String? = null,
    val videoSummaryBurmese: String? = null,
    val pipelineState: DubPipelineState = DubPipelineState.Idle,
    val isDownloadingToGallery: Boolean = false
)

class VideoDubViewModel(
    private val dubAiRepository: DubAiRepository = DubAiRepository(),
    private val ttsService: BurmeseTtsService = BurmeseTtsService(),
    private val voiceCloneRepository: VoiceCloneRepository = VoiceCloneRepository(),
    private val voiceCloneStore: VoiceCloneStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VideoDubUiState(clonedVoices = voiceCloneStore?.loadClonedVoices().orEmpty())
    )
    val uiState: StateFlow<VideoDubUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val previewPlayer = WavPreviewPlayer()

    override fun onCleared() {
        previewPlayer.stop()
        super.onCleared()
    }

    fun onVideoSelected(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingVideo = true) }
            try {
                val summary = VideoProcessor.inspectVideo(appContext, uri)
                _uiState.update {
                    it.copy(
                        selectedVideo = summary,
                        isInspectingVideo = false,
                        segments = emptyList(),
                        detectedLanguage = null,
                        videoSummaryBurmese = null,
                        pipelineState = DubPipelineState.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect video for dubbing", e)
                _uiState.update {
                    it.copy(
                        isInspectingVideo = false,
                        pipelineState = DubPipelineState.Error(
                            e.localizedMessage ?: "Could not read the selected video."
                        )
                    )
                }
            }
        }
    }

    fun clearVideo() {
        previewPlayer.stop()
        _uiState.update {
            it.copy(
                selectedVideo = null,
                isInspectingVideo = false,
                segments = emptyList(),
                detectedLanguage = null,
                videoSummaryBurmese = null,
                pipelineState = DubPipelineState.Idle
            )
        }
    }

    fun selectVoiceCategory(category: VoiceCategory) {
        _uiState.update { it.copy(voiceCategory = category) }
    }

    fun selectBuiltInVoice(preset: DubVoicePreset) {
        _uiState.update { it.copy(selectedVoice = ActiveVoiceSelection.BuiltIn(preset)) }
    }

    fun selectClonedVoice(profile: ClonedVoiceProfile) {
        _uiState.update { it.copy(selectedVoice = ActiveVoiceSelection.Cloned(profile)) }
    }

    fun deleteClonedVoice(profileId: String) {
        val remaining = voiceCloneStore?.deleteClonedVoice(profileId)
            ?: _uiState.value.clonedVoices.filterNot { it.id == profileId }
        _uiState.update { state ->
            val currentClonedId = (state.selectedVoice as? ActiveVoiceSelection.Cloned)?.profile?.id
            state.copy(
                clonedVoices = remaining,
                selectedVoice = if (currentClonedId == profileId) {
                    ActiveVoiceSelection.BuiltIn(DubVoicePreset.PUCK)
                } else {
                    state.selectedVoice
                }
            )
        }
        _snackbarMessage.value = "Voice clone deleted"
    }

    fun setSpeakingSpeed(speed: Float) {
        _uiState.update { it.copy(speakingSpeedMultiplier = speed.coerceIn(0.84f, 1.24f)) }
    }

    fun setPitchFineTune(ratio: Float) {
        _uiState.update { it.copy(pitchFineTuneRatio = ratio.coerceIn(0.86f, 1.16f)) }
    }

    fun setVoiceVolumeBoost(enabled: Boolean) {
        _uiState.update { it.copy(voiceVolumeBoost = enabled) }
    }

    fun setConciseSubtitles(enabled: Boolean) {
        _uiState.update { it.copy(conciseSubtitles = enabled) }
    }

    fun setBurnSubtitles(enabled: Boolean) {
        _uiState.update { it.copy(burnSubtitles = enabled) }
    }

    fun selectSubtitleStyle(style: SubtitleThemeStyle) {
        _uiState.update { it.copy(subtitleStyle = style) }
    }

    fun selectSubtitleSize(size: SubtitleSizeOption) {
        _uiState.update { it.copy(subtitleSize = size) }
    }

    fun selectSubtitlePosition(position: SubtitlePositionOption) {
        _uiState.update { it.copy(subtitlePosition = position) }
    }

    fun setWatermarkEnabled(enabled: Boolean) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(enabled = enabled)) }
    }

    fun setWatermarkText(text: String) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(text = text.take(28))) }
    }

    fun setWatermarkMotionStyle(motion: WatermarkMotionStyle) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(motionStyle = motion)) }
    }

    fun setWatermarkPositionPreset(preset: WatermarkPositionPreset) {
        _uiState.update {
            it.copy(
                watermarkConfig = it.watermarkConfig.copy(
                    motionStyle = WatermarkMotionStyle.FIXED_POSITION,
                    customAnchorX = preset.anchorX,
                    customAnchorY = preset.anchorY
                )
            )
        }
    }

    fun setWatermarkCustomAnchor(anchorX: Float, anchorY: Float) {
        _uiState.update {
            it.copy(
                watermarkConfig = it.watermarkConfig.copy(
                    motionStyle = WatermarkMotionStyle.FIXED_POSITION,
                    customAnchorX = anchorX.coerceIn(-0.76f, 0.76f),
                    customAnchorY = anchorY.coerceIn(-0.50f, 0.82f)
                )
            )
        }
    }

    fun setWatermarkBadgeStyle(style: WatermarkBadgeStyle) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(badgeStyle = style)) }
    }

    fun setWatermarkScale(scale: Float) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(scale = scale.coerceIn(0.75f, 1.40f))) }
    }

    fun setWatermarkOpacity(opacity: Float) {
        _uiState.update { it.copy(watermarkConfig = it.watermarkConfig.copy(opacity = opacity.coerceIn(0.30f, 0.95f))) }
    }

    fun setKeepSoftOriginalBgm(enabled: Boolean) {
        _uiState.update { it.copy(keepSoftOriginalBgm = enabled) }
    }

    fun setCopyrightShieldEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(copyrightShieldConfig = it.copyrightShieldConfig.copy(enabled = enabled))
        }
    }

    fun selectCopyrightShieldPreset(preset: CopyrightShieldPreset) {
        _uiState.update { state ->
            val updated = when (preset) {
                CopyrightShieldPreset.BALANCED_SAFE -> state.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = preset,
                    zoomScale = 1.07f,
                    mirrorFlip = false,
                    colorContrastBoost = true,
                    cinemaFrameBorder = true
                )
                CopyrightShieldPreset.PRO_ANTI_CLAIM -> state.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = preset,
                    zoomScale = 1.10f,
                    mirrorFlip = true,
                    colorContrastBoost = true,
                    cinemaFrameBorder = true
                )
                CopyrightShieldPreset.CUSTOM -> state.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = preset
                )
            }
            state.copy(copyrightShieldConfig = updated, keepSoftOriginalBgm = false)
        }
    }

    fun setCopyrightZoomScale(scale: Float) {
        _uiState.update {
            it.copy(
                copyrightShieldConfig = it.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = CopyrightShieldPreset.CUSTOM,
                    zoomScale = scale.coerceIn(1.0f, 1.14f)
                )
            )
        }
    }

    fun setCopyrightMirrorFlip(enabled: Boolean) {
        _uiState.update {
            it.copy(
                copyrightShieldConfig = it.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = CopyrightShieldPreset.CUSTOM,
                    mirrorFlip = enabled
                )
            )
        }
    }

    fun setCopyrightColorBoost(enabled: Boolean) {
        _uiState.update {
            it.copy(
                copyrightShieldConfig = it.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = CopyrightShieldPreset.CUSTOM,
                    colorContrastBoost = enabled
                )
            )
        }
    }

    fun setCopyrightCinemaBorder(enabled: Boolean) {
        _uiState.update {
            it.copy(
                copyrightShieldConfig = it.copyrightShieldConfig.copy(
                    enabled = true,
                    preset = CopyrightShieldPreset.CUSTOM,
                    cinemaFrameBorder = enabled
                )
            )
        }
    }

    fun setContinuousFullVideoDub(enabled: Boolean) {
        _uiState.update { it.copy(continuousFullVideoDub = enabled) }
    }

    fun selectNarrationStyle(style: DubNarrationStyle) {
        _uiState.update { it.copy(narrationStyle = style) }
    }

    fun updateSegmentBurmeseText(segmentId: String, updatedText: String) {
        _uiState.update { state ->
            state.copy(
                segments = state.segments.map { seg ->
                    if (seg.id == segmentId) seg.copy(burmeseText = updatedText.take(220)) else seg
                }
            )
        }
    }

    fun nudgeSegmentTiming(segmentId: String, startDeltaMs: Long, endDeltaMs: Long) {
        val maxMs = _uiState.value.selectedVideo?.durationMs?.coerceAtLeast(1500L) ?: 30_000L
        _uiState.update { state ->
            val sorted = state.segments.sortedBy { it.startMs }
            val idx = sorted.indexOfFirst { it.id == segmentId }
            if (idx < 0) return@update state
            val current = sorted[idx]
            val prevEnd = if (idx > 0) sorted[idx - 1].endMs else 0L
            val nextStart = if (idx < sorted.lastIndex) sorted[idx + 1].startMs else maxMs
            val newStart = (current.startMs + startDeltaMs).coerceIn(prevEnd, (nextStart - 500L).coerceAtLeast(prevEnd))
            val newEnd = (current.endMs + endDeltaMs).coerceIn(newStart + 500L, nextStart.coerceAtLeast(newStart + 500L))
            val updated = sorted.toMutableList()
            updated[idx] = current.copy(startMs = newStart, endMs = newEnd.coerceAtMost(maxMs))
            state.copy(segments = updated)
        }
    }

    fun autoFillTimelineGaps() {
        val current = _uiState.value
        val video = current.selectedVideo ?: return
        val filled = DubAiRepository.ensureContinuousCoverage(
            segments = current.segments,
            totalDurationMs = video.durationMs,
            videoSummaryBurmese = current.videoSummaryBurmese.orEmpty(),
            clipTitleHint = video.displayName.substringBeforeLast('.')
        )
        _uiState.update { it.copy(segments = filled) }
        _snackbarMessage.value = "ဗီဒီယို အစမှအဆုံး မြန်မာစာတန်းနှင့် အသံနေရာလွတ်များကို အပြည့်ဖြည့်ပြီးပါပြီ (${filled.size} beats)"
    }

    fun deleteSegment(segmentId: String) {
        _uiState.update { state ->
            state.copy(segments = state.segments.filterNot { it.id == segmentId })
        }
    }

    fun addSegmentAtEnd() {
        val current = _uiState.value
        val maxMs = current.selectedVideo?.durationMs?.coerceAtLeast(2000L) ?: 30_000L
        if (current.segments.isEmpty()) {
            val newSeg = DubSegment(
                id = UUID.randomUUID().toString(),
                startMs = 0L,
                endMs = 2500L.coerceAtMost(maxMs),
                originalSpeech = "",
                burmeseText = "ဇာတ်လမ်းအဖွင့် စကားတစ်ခွန်း"
            )
            _uiState.update { it.copy(segments = listOf(newSeg)) }
            _snackbarMessage.value = "New subtitle line added"
            return
        }

        val sorted = current.segments.sortedBy { it.startMs }
        val last = sorted.last()
        val lastEnd = last.endMs.coerceAtMost(maxMs)

        if (lastEnd < maxMs - 550L) {
            val newStart = (lastEnd + 80L).coerceAtMost(maxMs - 450L)
            val newEnd = (newStart + 2500L).coerceAtMost(maxMs)
            val newSeg = DubSegment(
                id = UUID.randomUUID().toString(),
                startMs = newStart,
                endMs = newEnd,
                originalSpeech = "",
                burmeseText = "ဇာတ်လမ်းစကားတစ်ခွန်း ထပ်ဖြည့်ပါ"
            )
            _uiState.update { it.copy(segments = sorted + newSeg) }
            _snackbarMessage.value = "Added subtitle line at end"
            return
        }

        // Timeline already reaches clip end: cleanly split/trim the tail of the last segment so Add Line NEVER fails
        val lastDur = (lastEnd - last.startMs).coerceAtLeast(800L)
        val newSegDur = (lastDur / 2L).coerceIn(600L, 1800L)
        val adjustedLastEnd = (lastEnd - newSegDur - 50L).coerceAtLeast(last.startMs + 450L)
        val newStart = (adjustedLastEnd + 50L).coerceAtMost(maxMs - 400L)
        val newEnd = maxMs.coerceAtLeast(newStart + 400L)

        val updatedTail = last.copy(endMs = adjustedLastEnd)
        val newSeg = DubSegment(
            id = UUID.randomUUID().toString(),
            startMs = newStart,
            endMs = newEnd,
            originalSpeech = "",
            burmeseText = "ဇာတ်လမ်းအပိတ် စကားတစ်ခွန်း"
        )
        _uiState.update { it.copy(segments = sorted.dropLast(1) + updatedTail + newSeg) }
        _snackbarMessage.value = "Added new line at timeline end"
    }

    fun previewVoice(
        context: Context,
        customText: String? = null,
        overrideVoice: ActiveVoiceSelection? = null
    ) {
        val snapshot = _uiState.value
        val targetVoice = overrideVoice ?: snapshot.selectedVoice
        val targetId = when (targetVoice) {
            is ActiveVoiceSelection.BuiltIn -> targetVoice.preset.id
            is ActiveVoiceSelection.Cloned -> targetVoice.profile.id
        }
        if (snapshot.isPreviewingVoice && (overrideVoice == null || snapshot.previewingVoiceId == targetId)) {
            previewPlayer.stop()
            _uiState.update { it.copy(isPreviewingVoice = false, previewingVoiceId = null) }
            return
        }
        previewPlayer.stop()
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewingVoice = true, previewingVoiceId = targetId) }
            try {
                val previewWav = VideoExportStorage.createTempAudioFile(appContext, "voice_audition.wav")
                val line = customText?.takeIf { it.isNotBlank() } ?: BurmeseTtsService.DEFAULT_PREVIEW_LINE
                try {
                    ttsService.synthesizeVoicePreviewWav(
                        voiceSelection = targetVoice,
                        userSpeedMultiplier = snapshot.speakingSpeedMultiplier,
                        pitchFineTuneRatio = snapshot.pitchFineTuneRatio,
                        text = line,
                        outputWavFile = previewWav,
                        onStatusUpdate = { statusMsg ->
                            _snackbarMessage.value = statusMsg
                        }
                    )
                } catch (err: Exception) {
                    if (err is CancellationException) throw err
                    if (!shouldFallbackToClientDub(err)) throw err
                    _snackbarMessage.value = "Using client-side speech preview"
                    LocalAndroidTtsSynthesizer.synthesizePreviewWav(
                        context = appContext,
                        text = line,
                        voiceSelection = targetVoice,
                        userSpeedMultiplier = snapshot.speakingSpeedMultiplier,
                        pitchFineTuneRatio = snapshot.pitchFineTuneRatio,
                        outputWavFile = previewWav
                    )
                }
                previewPlayer.playFile(previewWav) {
                    _uiState.update { it.copy(isPreviewingVoice = false, previewingVoiceId = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice preview failed", e)
                _uiState.update { it.copy(isPreviewingVoice = false, previewingVoiceId = null) }
                _snackbarMessage.value = e.localizedMessage ?: "Could not audition voice."
            }
        }
    }

    fun createVoiceCloneFromUri(
        context: Context,
        sampleUri: Uri,
        profileName: String,
        onDone: () -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingVoiceClone = true, voiceCloneError = null) }
            try {
                val extracted = VideoProcessor.extractAudioSampleBytes(appContext, sampleUri)
                    ?: throw IllegalStateException("Could not read audio track from that file (max 8 MB). Please pick a shorter clip.")
                val profile = voiceCloneRepository.createVoiceCloneFromSample(
                    audioBytes = extracted.first,
                    mimeType = extracted.second,
                    profileName = profileName,
                    sourceLabel = "Uploaded Audio/Video"
                )
                val updatedList = voiceCloneStore?.saveClonedVoice(profile)
                    ?: (listOf(profile) + _uiState.value.clonedVoices)
                _uiState.update {
                    it.copy(
                        isCreatingVoiceClone = false,
                        clonedVoices = updatedList,
                        selectedVoice = ActiveVoiceSelection.Cloned(profile),
                        voiceCategory = VoiceCategory.CLONED
                    )
                }
                _snackbarMessage.value = "Voice clone \"${profile.name}\" created (${profile.matchedBaseVoice})"
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice clone from URI failed", e)
                _uiState.update {
                    it.copy(
                        isCreatingVoiceClone = false,
                        voiceCloneError = e.localizedMessage ?: "Voice cloning failed."
                    )
                }
            }
        }
    }

    fun createVoiceCloneFromRecordedFile(
        recordedFile: File,
        profileName: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingVoiceClone = true, voiceCloneError = null) }
            try {
                val bytes = recordedFile.readBytes()
                val profile = voiceCloneRepository.createVoiceCloneFromSample(
                    audioBytes = bytes,
                    mimeType = "audio/mp4",
                    profileName = profileName,
                    sourceLabel = "Mic Recording"
                )
                runCatching { recordedFile.delete() }
                val updatedList = voiceCloneStore?.saveClonedVoice(profile)
                    ?: (listOf(profile) + _uiState.value.clonedVoices)
                _uiState.update {
                    it.copy(
                        isCreatingVoiceClone = false,
                        clonedVoices = updatedList,
                        selectedVoice = ActiveVoiceSelection.Cloned(profile),
                        voiceCategory = VoiceCategory.CLONED
                    )
                }
                _snackbarMessage.value = "Voice clone \"${profile.name}\" created (${profile.matchedBaseVoice})"
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice clone from mic failed", e)
                _uiState.update {
                    it.copy(
                        isCreatingVoiceClone = false,
                        voiceCloneError = e.localizedMessage ?: "Voice cloning failed."
                    )
                }
            }
        }
    }

    fun clearVoiceCloneError() {
        _uiState.update { it.copy(voiceCloneError = null) }
    }

    fun muteOriginalAudioAndExport(
        context: Context,
        onGenerationSucceeded: suspend () -> Unit = {}
    ) {
        previewPlayer.stop()
        val current = _uiState.value
        val video = current.selectedVideo ?: run {
            _snackbarMessage.value = "Please pick a video clip first."
            return
        }
        val appContext = context.applicationContext

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isPreviewingVoice = false,
                        keepSoftOriginalBgm = false,
                        pipelineState = DubPipelineState.RenderingVideo(0)
                    )
                }
                val outMp4 = VideoExportStorage.createCacheExportFile(appContext, prefix = "FireRecapScript_Muted")
                val hasSubtitles = current.burnSubtitles && current.segments.isNotEmpty()
                VideoDubExporter.exportDubbedVideo(
                    context = appContext,
                    sourceVideoUri = video.uri,
                    videoWidth = video.width,
                    videoHeight = video.height,
                    hasOriginalAudio = video.hasAudio,
                    dubWavFile = null,
                    segments = if (hasSubtitles) current.segments else emptyList(),
                    burnSubtitles = hasSubtitles,
                    subtitleStyle = current.subtitleStyle,
                    subtitleSize = current.subtitleSize,
                    subtitlePosition = current.subtitlePosition,
                    watermarkConfig = current.watermarkConfig,
                    copyrightShieldConfig = current.copyrightShieldConfig,
                    keepSoftOriginalBgm = false,
                    outputMp4File = outMp4
                ) { progress ->
                    _uiState.update {
                        if (it.pipelineState is DubPipelineState.RenderingVideo) {
                            it.copy(pipelineState = DubPipelineState.RenderingVideo(progress))
                        } else {
                            it
                        }
                    }
                }
                onGenerationSucceeded()
                val rendered = RenderedDubVideo(
                    filePath = outMp4.absolutePath,
                    fileName = outMp4.name,
                    durationMs = video.durationMs,
                    sizeBytes = outMp4.length(),
                    usedFallbackMode = false,
                    fallbackReason = null
                )
                _uiState.update {
                    it.copy(pipelineState = DubPipelineState.Completed(rendered))
                }
                _snackbarMessage.value = "မူရင်း အင်္ဂလိပ်အသံကို အောင်မြင်စွာ ဖျောက်ပြီးပါပြီ (Audio Muted MP4)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mute original audio export failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = DubPipelineState.Error(
                            e.localizedMessage ?: "Could not remove original audio from video."
                        )
                    )
                }
            }
        }
    }

    fun startFullAutoDub(
        context: Context,
        onGenerationSucceeded: suspend () -> Unit = {}
    ) {
        previewPlayer.stop()
        val current = _uiState.value
        val video = current.selectedVideo ?: run {
            _snackbarMessage.value = "Please pick a video clip first."
            return
        }
        val appContext = context.applicationContext

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isPreviewingVoice = false,
                        pipelineState = DubPipelineState.Translating(
                            "ဗီဒီယိုထဲက အင်္ဂလိပ်စကားသံများကို နားထောင်ခွဲခြမ်းနေပါတယ်..."
                        )
                    )
                }

                val dubInput = VideoProcessor.prepareDubInput(appContext, video)
                val updateTranslateStage: (String) -> Unit = { msg ->
                    _uiState.update { state ->
                        val prev = (state.pipelineState as? DubPipelineState.Translating)?.progressPercent ?: 12
                        val next = (prev + 7).coerceIn(14, 33)
                        state.copy(pipelineState = DubPipelineState.Translating(msg, next))
                    }
                }

                updateTranslateStage("အင်္ဂလိပ်စကားကို မြန်မာစကားပြောဟန် တိုတိုကျစ်ကျစ် ဘာသာပြန်နေပါတယ်...")

                var usedFallbackTranslation = false
                var fallbackReason: String? = null
                val clipTitle = video.displayName.substringBeforeLast('.')

                val plan = try {
                    try {
                        dubAiRepository.translateVideoForDubbing(
                            input = dubInput,
                            clipTitleHint = clipTitle,
                            conciseSubtitles = current.conciseSubtitles,
                            continuousFullVideo = current.continuousFullVideoDub,
                            narrationStyle = current.narrationStyle,
                            onStatusUpdate = updateTranslateStage
                        )
                    } catch (err: Exception) {
                        if (err is CancellationException) throw err
                        val msg = err.message.orEmpty().lowercase()
                        val shouldRetrySplit = video.isInlineEligible &&
                            (msg.contains("timed out") || msg.contains("413") || msg.contains("too large"))
                        if (!shouldRetrySplit) throw err
                        updateTranslateStage("အသံဖိုင်ခွဲထုတ်ပြီး မြန်မာဘာသာပြန် စစ်ဆေးနေပါတယ်...")
                        val splitInput = VideoProcessor.prepareDubInput(
                            context = appContext,
                            summary = video,
                            forceSplitTracks = true
                        )
                        dubAiRepository.translateVideoForDubbing(
                            input = splitInput,
                            clipTitleHint = clipTitle,
                            conciseSubtitles = current.conciseSubtitles,
                            continuousFullVideo = current.continuousFullVideoDub,
                            narrationStyle = current.narrationStyle,
                            onStatusUpdate = updateTranslateStage
                        )
                    }
                } catch (err: Exception) {
                    if (err is CancellationException) throw err
                    if (!shouldFallbackToClientDub(err)) throw err
                    usedFallbackTranslation = true
                    fallbackReason = "Cloud AI returned ${summarizeCloudReason(err)}. Generated dubbed video using Client-Side Speech Synthesis + Canvas Subtitles & Watermark."
                    updateTranslateStage("Cloud 400/403 / API Key မရှိသေးသဖြင့် Client-Side Speech + Canvas Subtitle Fallback ဖြင့် ဆက်လက်ဖန်တီးနေပါတယ်...")
                    val existing = _uiState.value.segments
                    if (existing.isNotEmpty()) {
                        val ensured = if (current.continuousFullVideoDub) {
                            DubAiRepository.ensureContinuousCoverage(
                                segments = existing,
                                totalDurationMs = video.durationMs,
                                videoSummaryBurmese = _uiState.value.videoSummaryBurmese.orEmpty(),
                                clipTitleHint = clipTitle
                            )
                        } else {
                            existing
                        }
                        DubTranslationPlan(
                            detectedLanguage = _uiState.value.detectedLanguage ?: "Client Fallback",
                            videoSummaryBurmese = _uiState.value.videoSummaryBurmese
                                ?: "Client-Side Subtitle & Speech Fallback Mode ဖြင့် ဖန်တီးပေးထားပါသည်။",
                            segments = ensured
                        )
                    } else {
                        DubAiRepository.buildClientFallbackPlan(
                            totalDurationMs = video.durationMs,
                            clipTitleHint = clipTitle
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        segments = plan.segments,
                        detectedLanguage = plan.detectedLanguage,
                        videoSummaryBurmese = plan.videoSummaryBurmese
                    )
                }

                synthesizeAndRender(
                    appContext = appContext,
                    video = video,
                    segments = plan.segments,
                    initialFallbackUsed = usedFallbackTranslation,
                    initialFallbackReason = fallbackReason,
                    onGenerationSucceeded = onGenerationSucceeded
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auto dub pipeline failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = DubPipelineState.Error(
                            e.localizedMessage ?: "Video dubbing failed. Please try again."
                        )
                    )
                }
            }
        }
    }

    fun rerenderWithEditedSegments(context: Context) {
        previewPlayer.stop()
        val current = _uiState.value
        val video = current.selectedVideo ?: return
        val editedSegments = current.segments.filter { it.burmeseText.isNotBlank() }
        if (editedSegments.isEmpty()) {
            _snackbarMessage.value = "Please keep at least one Burmese subtitle line."
            return
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                synthesizeAndRender(appContext, video, editedSegments)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Re-render failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = DubPipelineState.Error(
                            e.localizedMessage ?: "Could not render dubbed video."
                        )
                    )
                }
            }
        }
    }

    private suspend fun synthesizeAndRender(
        appContext: Context,
        video: VideoClipSummary,
        segments: List<DubSegment>,
        initialFallbackUsed: Boolean = false,
        initialFallbackReason: String? = null,
        onGenerationSucceeded: suspend () -> Unit = {}
    ) {
        val snapshot = _uiState.value
        val wavFile = VideoExportStorage.createTempAudioFile(appContext)
        _uiState.update {
            it.copy(
                pipelineState = DubPipelineState.SynthesizingVoice(
                    completed = 0,
                    total = segments.size,
                    currentLine = segments.firstOrNull()?.burmeseText.orEmpty()
                )
            )
        }

        var usedFallback = initialFallbackUsed
        var fallbackReason = initialFallbackReason
        val voiceGain = if (snapshot.voiceVolumeBoost) 1.25f else 1.0f

        val syncedAudio = try {
            ttsService.synthesizeTimelineWav(
                segments = segments,
                totalDurationMs = video.durationMs.coerceAtLeast(1500L),
                voiceSelection = snapshot.selectedVoice,
                userSpeedMultiplier = snapshot.speakingSpeedMultiplier,
                pitchFineTuneRatio = snapshot.pitchFineTuneRatio,
                voiceVolumeGain = voiceGain,
                outputWavFile = wavFile,
                onStatusUpdate = { statusMsg ->
                    _uiState.update { state ->
                        val curr = state.pipelineState as? DubPipelineState.SynthesizingVoice
                        state.copy(
                            pipelineState = (curr ?: DubPipelineState.SynthesizingVoice(
                                completed = 0,
                                total = segments.size,
                                currentLine = ""
                            )).copy(statusNote = statusMsg)
                        )
                    }
                },
                onProgress = { completed, total, line ->
                    _uiState.update { state ->
                        val existingNote = (state.pipelineState as? DubPipelineState.SynthesizingVoice)?.statusNote
                        state.copy(
                            pipelineState = DubPipelineState.SynthesizingVoice(
                                completed = completed,
                                total = total,
                                currentLine = line,
                                statusNote = existingNote
                            )
                        )
                    }
                }
            )
        } catch (err: Exception) {
            if (err is CancellationException) throw err
            if (!shouldFallbackToClientDub(err)) throw err
            usedFallback = true
            if (fallbackReason == null) {
                fallbackReason = "Cloud voice endpoint unavailable (${summarizeCloudReason(err)}). Completed with Client-Side Speech Synthesis + Canvas Subtitles & Watermark."
            }
            _uiState.update { state ->
                val curr = state.pipelineState as? DubPipelineState.SynthesizingVoice
                state.copy(
                    pipelineState = (curr ?: DubPipelineState.SynthesizingVoice(
                        completed = 0,
                        total = segments.size,
                        currentLine = ""
                    )).copy(statusNote = "Client-Side Speech Fallback ဖြင့် အသံနှင့် စာတန်းဖန်တီးနေပါတယ်...")
                )
            }
            LocalAndroidTtsSynthesizer.synthesizeTimelineWav(
                context = appContext,
                segments = segments,
                totalDurationMs = video.durationMs.coerceAtLeast(1500L),
                voiceSelection = snapshot.selectedVoice,
                userSpeedMultiplier = snapshot.speakingSpeedMultiplier,
                pitchFineTuneRatio = snapshot.pitchFineTuneRatio,
                voiceVolumeGain = voiceGain,
                outputWavFile = wavFile,
                onProgress = { completed, total, line ->
                    _uiState.update { state ->
                        val existingNote = (state.pipelineState as? DubPipelineState.SynthesizingVoice)?.statusNote
                        state.copy(
                            pipelineState = DubPipelineState.SynthesizingVoice(
                                completed = completed,
                                total = total,
                                currentLine = line,
                                statusNote = existingNote
                            )
                        )
                    }
                }
            )
        }

        // Update state with exact audio-aligned timestamps!
        val syncedSegments = syncedAudio.syncedSegments
        _uiState.update {
            it.copy(
                segments = syncedSegments,
                pipelineState = DubPipelineState.RenderingVideo(0)
            )
        }

        val outMp4 = VideoExportStorage.createCacheExportFile(appContext, prefix = "FireRecapScript_Dub")
        VideoDubExporter.exportDubbedVideo(
            context = appContext,
            sourceVideoUri = video.uri,
            videoWidth = video.width,
            videoHeight = video.height,
            hasOriginalAudio = video.hasAudio,
            dubWavFile = syncedAudio.wavFile,
            segments = syncedSegments,
            burnSubtitles = snapshot.burnSubtitles,
            subtitleStyle = snapshot.subtitleStyle,
            subtitleSize = snapshot.subtitleSize,
            subtitlePosition = snapshot.subtitlePosition,
            watermarkConfig = snapshot.watermarkConfig,
            copyrightShieldConfig = snapshot.copyrightShieldConfig,
            keepSoftOriginalBgm = snapshot.keepSoftOriginalBgm,
            outputMp4File = outMp4
        ) { progress ->
            _uiState.update {
                if (it.pipelineState is DubPipelineState.RenderingVideo) {
                    it.copy(pipelineState = DubPipelineState.RenderingVideo(progress))
                } else {
                    it
                }
            }
        }

        onGenerationSucceeded()
        val rendered = RenderedDubVideo(
            filePath = outMp4.absolutePath,
            fileName = outMp4.name,
            durationMs = video.durationMs,
            sizeBytes = outMp4.length(),
            usedFallbackMode = usedFallback,
            fallbackReason = fallbackReason
        )
        _uiState.update {
            it.copy(pipelineState = DubPipelineState.Completed(rendered))
        }
    }

    private fun shouldFallbackToClientDub(err: Exception): Boolean {
        if (GeminiResilienceHelper.isFallbackEligibleError(err)) return true
        return generateSequence<Throwable>(err) { it.cause }.any { it is IOException }
    }

    private fun summarizeCloudReason(err: Exception): String {
        val raw = err.message.orEmpty()
        return when {
            raw.contains("400") || raw.contains("not valid", ignoreCase = true) -> "HTTP 400 (Invalid Key)"
            raw.contains("403") || raw.contains("attestation", ignoreCase = true) -> "HTTP 403"
            raw.contains("401") -> "HTTP 401"
            raw.contains("429") || raw.contains("quota", ignoreCase = true) -> "Quota 429"
            raw.contains("API Key", ignoreCase = true) -> "No API Key"
            else -> raw.take(45).ifBlank { "Cloud Unavailable" }
        }
    }

    fun downloadToGallery(context: Context) {
        val completed = _uiState.value.pipelineState as? DubPipelineState.Completed ?: return
        if (_uiState.value.isDownloadingToGallery) return
        val appContext = context.applicationContext

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingToGallery = true) }
            try {
                val result = VideoExportStorage.saveVideoToDeviceGallery(
                    context = appContext,
                    sourceFile = File(completed.output.filePath)
                )
                _uiState.update { state ->
                    val currCompleted = state.pipelineState as? DubPipelineState.Completed
                    state.copy(
                        isDownloadingToGallery = false,
                        pipelineState = if (currCompleted != null) {
                            currCompleted.copy(
                                output = currCompleted.output.copy(
                                    savedGalleryUri = result.uri,
                                    savedGalleryPathLabel = result.userFacingLocation
                                )
                            )
                        } else {
                            state.pipelineState
                        }
                    )
                }
                _snackbarMessage.value = "ဗီဒီယိုကို Gallery ထဲသို့ Download သိမ်းဆည်းပြီးပါပြီ"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving dubbed video to gallery", e)
                _uiState.update { it.copy(isDownloadingToGallery = false) }
                _snackbarMessage.value = e.localizedMessage ?: "Could not save video to Gallery."
            }
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    companion object {
        private const val TAG = "VideoDubViewModel"
    }
}
