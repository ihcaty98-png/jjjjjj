package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.media.VideoExportStorage
import com.example.model.ActiveVoiceSelection
import com.example.model.ClonedVoiceProfile
import com.example.model.CopyrightShieldPreset
import com.example.model.DubNarrationStyle
import com.example.model.DubSegment
import com.example.model.DubVoicePreset
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.model.VideoClipSummary
import com.example.model.VoiceCategory
import com.example.model.WatermarkBadgeStyle
import com.example.model.WatermarkMotionStyle
import com.example.model.WatermarkPositionPreset
import com.example.ui.theme.MyanmarScriptFamily
import java.io.File

@Composable
fun VideoDubScreen(
    uiState: VideoDubUiState,
    onVideoPicked: (Uri) -> Unit,
    onClearVideo: () -> Unit,
    onSelectVoiceCategory: (VoiceCategory) -> Unit,
    onSelectBuiltInVoice: (DubVoicePreset) -> Unit,
    onSelectClonedVoice: (ClonedVoiceProfile) -> Unit,
    onDeleteClonedVoice: (String) -> Unit,
    onCreateVoiceCloneFromUri: (Uri, String, () -> Unit) -> Unit,
    onCreateVoiceCloneFromRecordedFile: (File, String, () -> Unit) -> Unit,
    onClearVoiceCloneError: () -> Unit,
    onAuditionVoice: (String?) -> Unit,
    onAuditionSpecificVoice: (ActiveVoiceSelection) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Float) -> Unit,
    onVoiceBoostChanged: (Boolean) -> Unit,
    onConciseSubtitlesChanged: (Boolean) -> Unit,
    onBurnSubtitlesChanged: (Boolean) -> Unit,
    onSubtitleStyleSelected: (SubtitleThemeStyle) -> Unit,
    onSubtitleSizeSelected: (SubtitleSizeOption) -> Unit,
    onSubtitlePositionSelected: (SubtitlePositionOption) -> Unit,
    onWatermarkEnabledChanged: (Boolean) -> Unit,
    onWatermarkTextChanged: (String) -> Unit,
    onWatermarkMotionSelected: (WatermarkMotionStyle) -> Unit,
    onWatermarkPresetSelected: (WatermarkPositionPreset) -> Unit,
    onWatermarkCustomAnchorChanged: (Float, Float) -> Unit,
    onWatermarkBadgeStyleSelected: (WatermarkBadgeStyle) -> Unit,
    onWatermarkScaleChanged: (Float) -> Unit,
    onWatermarkOpacityChanged: (Float) -> Unit,
    onKeepSoftBgmChanged: (Boolean) -> Unit,
    onCopyrightEnabledChanged: (Boolean) -> Unit = {},
    onCopyrightPresetSelected: (CopyrightShieldPreset) -> Unit = {},
    onCopyrightZoomChanged: (Float) -> Unit = {},
    onCopyrightMirrorChanged: (Boolean) -> Unit = {},
    onCopyrightColorChanged: (Boolean) -> Unit = {},
    onCopyrightBorderChanged: (Boolean) -> Unit = {},
    onContinuousFullVideoChanged: (Boolean) -> Unit = {},
    onNarrationStyleSelected: (DubNarrationStyle) -> Unit = {},
    onSegmentTextEdited: (String, String) -> Unit,
    onNudgeSegment: (String, Long, Long) -> Unit = { _, _, _ -> },
    onAutoFillGaps: () -> Unit = {},
    onDeleteSegment: (String) -> Unit,
    onAddSegment: () -> Unit,
    onStartAutoDub: () -> Unit,
    onMuteOriginalOnly: () -> Unit = {},
    onRerenderClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onNotify: (String) -> Unit,
    onOpenApiKeySettings: () -> Unit = {},
    onOpenBilling: () -> Unit = {},
    showSwarmAnimation: Boolean = true,
    accessBadgeText: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showVoiceSelectorDialog by remember { mutableStateOf(false) }
    var showVoiceCloneDialog by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onVideoPicked(uri)
            }
        }
    )

    val launchVideoPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    if (showVoiceSelectorDialog) {
        VoiceProfileSelectionDialog(
            selectedVoice = uiState.selectedVoice,
            selectedCategory = uiState.voiceCategory,
            clonedVoices = uiState.clonedVoices,
            isPreviewingVoice = uiState.isPreviewingVoice,
            previewingVoiceId = uiState.previewingVoiceId,
            onSelectCategory = onSelectVoiceCategory,
            onSelectBuiltIn = onSelectBuiltInVoice,
            onSelectCloned = onSelectClonedVoice,
            onDeleteCloned = onDeleteClonedVoice,
            onAuditionSpecificVoice = onAuditionSpecificVoice,
            onCloneMyVoiceClick = {
                showVoiceSelectorDialog = false
                showVoiceCloneDialog = true
            },
            onDismiss = { showVoiceSelectorDialog = false }
        )
    }

    if (showVoiceCloneDialog) {
        VoiceCloneDialog(
            isCreating = uiState.isCreatingVoiceClone,
            errorMessage = uiState.voiceCloneError,
            onDismiss = {
                onClearVoiceCloneError()
                showVoiceCloneDialog = false
            },
            onClearError = onClearVoiceCloneError,
            onCreateFromUri = { uri, name ->
                onCreateVoiceCloneFromUri(uri, name) {
                    showVoiceCloneDialog = false
                }
            },
            onCreateFromRecordedFile = { file, name ->
                onCreateVoiceCloneFromRecordedFile(file, name) {
                    showVoiceCloneDialog = false
                }
            }
        )
    }

    val isBusy = uiState.pipelineState is DubPipelineState.Translating ||
        uiState.pipelineState is DubPipelineState.SynthesizingVoice ||
        uiState.pipelineState is DubPipelineState.RenderingVideo

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DubHeaderBanner()

            DubVideoPickerCard(
                video = uiState.selectedVideo,
                isInspecting = uiState.isInspectingVideo,
                enabled = !isBusy,
                onPickVideo = launchVideoPicker,
                onClearVideo = onClearVideo
            )

            // Dedicated Interactive Video Preview & 'FireRecapScript' Watermark Studio Component
            WatermarkVideoPreviewComponent(
                video = uiState.selectedVideo,
                subtitleStyle = uiState.subtitleStyle,
                subtitleSize = uiState.subtitleSize,
                subtitlePosition = uiState.subtitlePosition,
                burnSubtitles = uiState.burnSubtitles,
                watermarkConfig = uiState.watermarkConfig,
                isBusy = isBusy,
                onWatermarkEnabledChanged = onWatermarkEnabledChanged,
                onWatermarkTextChanged = onWatermarkTextChanged,
                onWatermarkMotionSelected = onWatermarkMotionSelected,
                onWatermarkPresetSelected = onWatermarkPresetSelected,
                onWatermarkCustomAnchorChanged = onWatermarkCustomAnchorChanged,
                onWatermarkBadgeStyleSelected = onWatermarkBadgeStyleSelected,
                onWatermarkScaleChanged = onWatermarkScaleChanged,
                onWatermarkOpacityChanged = onWatermarkOpacityChanged
            )

            FullVideoNarrationSection(
                continuousFullVideoDub = uiState.continuousFullVideoDub,
                narrationStyle = uiState.narrationStyle,
                isBusy = isBusy,
                onContinuousChanged = onContinuousFullVideoChanged,
                onNarrationStyleSelected = onNarrationStyleSelected
            )

            VoiceStudioSection(
                uiState = uiState,
                isBusy = isBusy,
                onOpenVoiceSelectorDialog = { showVoiceSelectorDialog = true },
                onOpenVoiceCloneDialog = { showVoiceCloneDialog = true },
                onAuditionVoice = { onAuditionVoice(null) },
                onSpeedChanged = onSpeedChanged,
                onPitchChanged = onPitchChanged,
                onBoostChanged = onVoiceBoostChanged
            )

            SubtitleStudioSection(
                uiState = uiState,
                isBusy = isBusy,
                onConciseSubtitlesChanged = onConciseSubtitlesChanged,
                onBurnSubtitlesChanged = onBurnSubtitlesChanged,
                onSubtitleStyleSelected = onSubtitleStyleSelected,
                onSubtitleSizeSelected = onSubtitleSizeSelected,
                onSubtitlePositionSelected = onSubtitlePositionSelected
            )

            OriginalAudioRemovalSection(
                keepSoftOriginalBgm = uiState.keepSoftOriginalBgm,
                hasSelectedVideo = uiState.selectedVideo != null && !uiState.isInspectingVideo,
                isBusy = isBusy,
                onKeepSoftBgmChanged = onKeepSoftBgmChanged,
                onMuteOriginalOnlyClick = onMuteOriginalOnly
            )

            VideoCopyrightShieldSection(
                config = uiState.copyrightShieldConfig,
                isBusy = isBusy,
                onEnabledChanged = onCopyrightEnabledChanged,
                onPresetSelected = onCopyrightPresetSelected,
                onZoomScaleChanged = onCopyrightZoomChanged,
                onMirrorFlipChanged = onCopyrightMirrorChanged,
                onColorBoostChanged = onCopyrightColorChanged,
                onCinemaBorderChanged = onCopyrightBorderChanged
            )

            if (accessBadgeText.isNotBlank()) {
                Surface(
                    onClick = onOpenBilling,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("dub_access_badge_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = accessBadgeText,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VIP →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Primary Action CTA
            Button(
                onClick = onStartAutoDub,
                enabled = uiState.selectedVideo != null && !isBusy && !uiState.isInspectingVideo,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_dub_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Dubbed Video...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.dub_generate_cta),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            AnimatedVisibility(visible = isBusy) {
                DubProgressCard(
                    pipelineState = uiState.pipelineState,
                    showSwarmAnimation = showSwarmAnimation
                )
            }

            AnimatedVisibility(visible = uiState.pipelineState is DubPipelineState.Error) {
                val err = uiState.pipelineState as? DubPipelineState.Error
                if (err != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Video Dubbing Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = err.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (uiState.segments.isNotEmpty()) {
                                    Button(
                                        onClick = onRerenderClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("retry_voice_render_button")
                                    ) {
                                        Text("Retry Voice & Render")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onStartAutoDub,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("retry_dub_button")
                                ) {
                                    Text(if (uiState.segments.isNotEmpty()) "Re-Translate" else "Try Again")
                                }
                                OutlinedButton(
                                    onClick = onOpenApiKeySettings,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("dub_error_api_key_button")
                                ) {
                                    Text("API Key")
                                }
                            }
                        }
                    }
                }
            }

            val completed = uiState.pipelineState as? DubPipelineState.Completed
            if (completed != null) {
                val output = completed.output
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "EXPORT READY • BURMESE DUB + SUBS + WATERMARK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "မြန်မာအသံထပ် + စာတန်းထိုး ဗီဒီယို အဆင်သင့်ဖြစ်ပါပြီ",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (output.usedFallbackMode) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dub_fallback_mode_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Client-Side Speech + Canvas Fallback Mode",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = output.fallbackReason
                                            ?: "Generated smoothly with on-device speech synthesis + Canvas subtitle/watermark rendering. Edit subtitles below or add a Gemini API key for Cloud Neural Voices.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = onOpenApiKeySettings,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("fallback_open_api_key_button")
                                    ) {
                                        Text("Configure Gemini API Key")
                                    }
                                }
                            }
                        }

                        DubVideoPlayerCard(videoFilePath = output.filePath)

                        val mb = output.sizeBytes / (1024.0 * 1024.0)
                        Text(
                            text = "${output.fileName} • ${String.format("%.1f MB", mb)} • MP4 (H.264 / AAC)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isSaved = output.savedGalleryUri != null
                            Button(
                                onClick = onDownloadClick,
                                enabled = !uiState.isDownloadingToGallery,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(50.dp)
                                    .testTag("download_dub_video_button")
                            ) {
                                if (uiState.isDownloadingToGallery) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saving...")
                                } else {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSaved) "Saved to Gallery" else stringResource(R.string.dub_download_cta),
                                        maxLines = 1
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = VideoExportStorage.buildShareIntent(
                                        context = context,
                                        file = File(output.filePath),
                                        savedGalleryUri = output.savedGalleryUri
                                    )
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(50.dp)
                                    .testTag("share_dub_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.dub_share_cta), maxLines = 1)
                            }
                        }

                        output.savedGalleryPathLabel?.let { savedPath ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Saved to: $savedPath",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.segments.isNotEmpty() && !isBusy) {
                EditableSyncedTimelineSection(
                    detectedLanguage = uiState.detectedLanguage,
                    summaryBurmese = uiState.videoSummaryBurmese,
                    segments = uiState.segments,
                    totalDurationMs = uiState.selectedVideo?.durationMs ?: 30_000L,
                    isPreviewingVoice = uiState.isPreviewingVoice,
                    onPreviewLine = { text -> onAuditionVoice(text) },
                    onSegmentEdited = onSegmentTextEdited,
                    onNudgeSegment = onNudgeSegment,
                    onAutoFillGaps = onAutoFillGaps,
                    onDeleteSegment = onDeleteSegment,
                    onAddSegment = onAddSegment,
                    onCopySrt = {
                        val srt = DubSegment.toSrtString(uiState.segments)
                        clipboardManager.setText(AnnotatedString(srt))
                        onNotify("Synced Burmese .SRT subtitles copied")
                    },
                    onRerenderClick = onRerenderClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DubHeaderBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 148.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_dub_stage_banner),
            contentDescription = "Burmese AI Video Dubbing Studio",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon_recap),
                    contentDescription = "Fire RecapScript icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PRO DUB • START-TO-END SYNC • 100% BURMESE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "တကယ့်လူအသံစစ်စစ်ဖြင့် ဗီဒီယိုအစမှအဆုံး\nမရပ်တမ်း မြန်မာအသံထပ် + စာတန်းဖန်တီးပါ",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MyanmarScriptFamily,
                    lineHeight = 27.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("00:00 → End Sync", "Voice Clone", "Watermark").forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DubVideoPickerCard(
    video: VideoClipSummary?,
    isInspecting: Boolean,
    enabled: Boolean,
    onPickVideo: () -> Unit,
    onClearVideo: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (video != null) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isInspecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Inspecting video...", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (video == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select English Video to Dub",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Translates English speech to tight Burmese subtitles, dubs with human/cloned AI voices, adds moving FireRecapScript protection, and exports MP4.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPickVideo,
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("dub_pick_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieCreation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Video Clip")
                }
            }
        } else {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val thumb = video.thumbnail
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Selected video thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = "Video selected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.86f)
                                    )
                                )
                            )
                    )
                    if (enabled) {
                        IconButton(
                            onClick = onClearVideo,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("dub_clear_video_button")
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove video",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = video.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Text(
                            text = "${video.formattedDuration} • ${video.formattedSize} • ${video.width}x${video.height}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickVideo,
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dub_change_video_button")
                ) {
                    Text(stringResource(R.string.change_video_cta))
                }
            }
        }
    }
}

@Composable
private fun DubProgressCard(
    pipelineState: DubPipelineState,
    showSwarmAnimation: Boolean
) {
    when (pipelineState) {
        is DubPipelineState.Translating -> {
            SwarmVideoProgressCard(
                progressPercent = pipelineState.progressPercent,
                stepHeadline = "Step 1/3 • အင်္ဂလိပ်စကား → မြန်မာစာတန်း ဖန်တီးနေပါတယ်",
                stageMessage = pipelineState.stageMessage,
                activeStepIndex = 1,
                showSwarmAnimation = showSwarmAnimation
            )
        }
        is DubPipelineState.SynthesizingVoice -> {
            val detail = buildString {
                if (pipelineState.currentLine.isNotBlank()) {
                    append("“${pipelineState.currentLine}”")
                } else {
                    append("မြန်မာ AI အသံ & အချိန်ကိုက် Sync (${pipelineState.completed}/${pipelineState.total})")
                }
                pipelineState.statusNote?.takeIf { it.isNotBlank() }?.let { note ->
                    append("\n$note")
                }
            }
            SwarmVideoProgressCard(
                progressPercent = pipelineState.overallProgressPercent,
                stepHeadline = "Step 2/3 • မြန်မာ AI အသံ (${pipelineState.completed}/${pipelineState.total})",
                stageMessage = detail,
                activeStepIndex = 2,
                showSwarmAnimation = showSwarmAnimation
            )
        }
        is DubPipelineState.RenderingVideo -> {
            SwarmVideoProgressCard(
                progressPercent = pipelineState.overallProgressPercent,
                stepHeadline = "Step 3/3 • MP4 + Subtitle + Shield Export",
                stageMessage = "Burning synced Burmese subtitles, copyright guard & dubbed audio track (${pipelineState.progressPercent}%)...",
                activeStepIndex = 3,
                showSwarmAnimation = showSwarmAnimation,
                isCompleted = pipelineState.overallProgressPercent >= 100
            )
        }
        else -> Unit
    }
}

