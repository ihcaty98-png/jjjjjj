package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveVoiceSelection
import com.example.model.CopyrightShieldConfig
import com.example.model.CopyrightShieldPreset
import com.example.model.DubNarrationStyle
import com.example.model.DubSegment
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.ui.theme.MyanmarScriptFamily
import kotlin.math.roundToInt

@Composable
fun VoiceStudioSection(
    uiState: VideoDubUiState,
    isBusy: Boolean,
    onOpenVoiceSelectorDialog: () -> Unit,
    onOpenVoiceCloneDialog: () -> Unit,
    onAuditionVoice: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Float) -> Unit,
    onBoostChanged: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Burmese AI Voice Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = onOpenVoiceCloneDialog,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(44.dp)
                        .testTag("open_voice_clone_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clone My Voice", color = MaterialTheme.colorScheme.secondary)
                }
            }

            // Active Voice Picker Card (opens VoiceProfileSelectionDialog)
            val voiceSubtitle = when (val active = uiState.selectedVoice) {
                is ActiveVoiceSelection.BuiltIn -> active.preset.burmeseLabel
                is ActiveVoiceSelection.Cloned -> active.profile.vocalDescription.ifBlank { active.profile.sourceLabel }
            }
            val voiceBadge = when (val active = uiState.selectedVoice) {
                is ActiveVoiceSelection.BuiltIn -> if (active.preset.isMale) "MALE AI" else "FEMALE AI"
                is ActiveVoiceSelection.Cloned -> "CLONED"
            }

            Surface(
                onClick = { if (!isBusy) onOpenVoiceSelectorDialog() },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_voice_selector_dialog_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.selectedVoice.displayTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = voiceBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = voiceSubtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Choose AI voice profile",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Audition Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tap card above to switch voice or audition sample",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAuditionVoice,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(44.dp)
                        .testTag("audition_voice_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isPreviewingVoice) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (uiState.isPreviewingVoice) "Stop" else "Test Voice")
                }
            }

            // Speed & Pitch Fine-Tuning
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Voice Pace (စကားပြောနှုန်း)",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.2fx", uiState.speakingSpeedMultiplier),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = uiState.speakingSpeedMultiplier,
                    onValueChange = { if (!isBusy) onSpeedChanged(it) },
                    valueRange = 0.84f..1.24f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("voice_speed_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tone / Pitch Depth (အသံအနိမ့်အမြင့်)",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            uiState.pitchFineTuneRatio < 0.96f -> "Deeper (${String.format("%.2fx", uiState.pitchFineTuneRatio)})"
                            uiState.pitchFineTuneRatio > 1.04f -> "Brighter (${String.format("%.2fx", uiState.pitchFineTuneRatio)})"
                            else -> "Natural (1.00x)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = uiState.pitchFineTuneRatio,
                    onValueChange = { if (!isBusy) onPitchChanged(it) },
                    valueRange = 0.86f..1.16f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("voice_pitch_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loud Crystal Voice Boost (+25%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Makes Burmese AI dub loud & crisp over action scenes",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.voiceVolumeBoost,
                    onCheckedChange = { if (!isBusy) onBoostChanged(it) },
                    modifier = Modifier.testTag("voice_boost_switch")
                )
            }
        }
    }
}

@Composable
fun SubtitleStudioSection(
    uiState: VideoDubUiState,
    isBusy: Boolean,
    onConciseSubtitlesChanged: (Boolean) -> Unit,
    onBurnSubtitlesChanged: (Boolean) -> Unit,
    onSubtitleStyleSelected: (SubtitleThemeStyle) -> Unit,
    onSubtitleSizeSelected: (SubtitleSizeOption) -> Unit,
    onSubtitlePositionSelected: (SubtitlePositionOption) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "မြန်မာစာတန်းထိုး ဒီဇိုင်း",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Tightly synced to exact spoken voice duration",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.burnSubtitles,
                    onCheckedChange = { if (!isBusy) onBurnSubtitlesChanged(it) },
                    modifier = Modifier.testTag("burn_subtitles_switch")
                )
            }

            AnimatedVisibility(visible = uiState.burnSubtitles) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "စာတန်းတိုတို ၁-၂ ကြောင်းစာ စနစ်တကျခွဲမည်",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Prevents overly long text blocks on screen",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.conciseSubtitles,
                            onCheckedChange = { if (!isBusy) onConciseSubtitlesChanged(it) },
                            modifier = Modifier.testTag("concise_subtitles_switch")
                        )
                    }

                    Text(
                        text = "SUBTITLE STYLE THEME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SubtitleThemeStyle.entries) { style ->
                            FilterChip(
                                selected = uiState.subtitleStyle == style,
                                onClick = { if (!isBusy) onSubtitleStyleSelected(style) },
                                label = {
                                    Text(
                                        text = style.burmeseLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("subtitle_style_${style.id}")
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SubtitleSizeOption.entries) { size ->
                            FilterChip(
                                selected = uiState.subtitleSize == size,
                                onClick = { if (!isBusy) onSubtitleSizeSelected(size) },
                                label = { Text(size.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("subtitle_size_${size.id}")
                            )
                        }
                        items(SubtitlePositionOption.entries) { pos ->
                            FilterChip(
                                selected = uiState.subtitlePosition == pos,
                                onClick = { if (!isBusy) onSubtitlePositionSelected(pos) },
                                label = { Text(pos.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("subtitle_pos_${pos.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OriginalAudioRemovalSection(
    keepSoftOriginalBgm: Boolean,
    hasSelectedVideo: Boolean,
    isBusy: Boolean,
    onKeepSoftBgmChanged: (Boolean) -> Unit,
    onMuteOriginalOnlyClick: () -> Unit
) {
    val removeOriginalSpeech = !keepSoftOriginalBgm
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (removeOriginalSpeech) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (removeOriginalSpeech) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "မူရင်း အင်္ဂလိပ်အသံ ဖျောက်စနစ်",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (removeOriginalSpeech) {
                                "ON • Removes 100% of original English video speech"
                            } else {
                                "Soft BGM • Keeps 8% soft original background audio"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(
                    checked = removeOriginalSpeech,
                    onCheckedChange = { shouldMuteOriginal ->
                        if (!isBusy) onKeepSoftBgmChanged(!shouldMuteOriginal)
                    },
                    modifier = Modifier.testTag("remove_original_english_audio_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = removeOriginalSpeech,
                    onClick = { if (!isBusy) onKeepSoftBgmChanged(false) },
                    label = {
                        Text(
                            text = "100% မူရင်းအသံဖျောက် (Mute EN)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily),
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("mute_en_mode_chip")
                )
                FilterChip(
                    selected = !removeOriginalSpeech,
                    onClick = { if (!isBusy) onKeepSoftBgmChanged(true) },
                    label = {
                        Text(
                            text = "Soft BGM 8%",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("soft_bgm_mode_chip")
                )
            }

            Button(
                onClick = onMuteOriginalOnlyClick,
                enabled = hasSelectedVideo && !isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("mute_original_audio_export_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "မူရင်း အင်္ဂလိပ်အသံ ဖျောက်ပြီး Video ထုတ်မည် (Mute Only)",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun VideoCopyrightShieldSection(
    config: CopyrightShieldConfig,
    isBusy: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onPresetSelected: (CopyrightShieldPreset) -> Unit,
    onZoomScaleChanged: (Float) -> Unit,
    onMirrorFlipChanged: (Boolean) -> Unit,
    onColorBoostChanged: (Boolean) -> Unit,
    onCinemaBorderChanged: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (config.enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_copyright_shield_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Video Copyright Shield (မူပိုင်ခွင့် ကာကွယ်စနစ်)",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.enabled) {
                                "ON • Anti-Content-ID Zoom (${String.format("%.2fx", config.zoomScale)}), Color & Audio Strip"
                            } else {
                                "OFF • Standard unprocessed video frames"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { if (!isBusy) onEnabledChanged(it) },
                    modifier = Modifier.testTag("copyright_shield_switch")
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CopyrightShieldPreset.entries) { preset ->
                            FilterChip(
                                selected = config.preset == preset,
                                onClick = { if (!isBusy) onPresetSelected(preset) },
                                label = {
                                    Text(
                                        text = "${preset.label} • ${preset.burmeseLabel}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copyright_preset_${preset.id}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Anti-Claim Frame Crop / Zoom",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(config.zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = config.zoomScale,
                        onValueChange = { if (!isBusy) onZoomScaleChanged(it) },
                        valueRange = 1.00f..1.14f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("copyright_zoom_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = config.mirrorFlip,
                            onClick = { if (!isBusy) onMirrorFlipChanged(!config.mirrorFlip) },
                            label = { Text("Mirror Flip", maxLines = 1) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copyright_mirror_chip")
                        )
                        FilterChip(
                            selected = config.colorContrastBoost,
                            onClick = { if (!isBusy) onColorBoostChanged(!config.colorContrastBoost) },
                            label = { Text("Color Shift", maxLines = 1) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copyright_color_chip")
                        )
                        FilterChip(
                            selected = config.cinemaFrameBorder,
                            onClick = { if (!isBusy) onCinemaBorderChanged(!config.cinemaFrameBorder) },
                            label = { Text("Frame Guard", maxLines = 1) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copyright_border_chip")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullVideoNarrationSection(
    continuousFullVideoDub: Boolean,
    narrationStyle: DubNarrationStyle,
    isBusy: Boolean,
    onContinuousChanged: (Boolean) -> Unit,
    onNarrationStyleSelected: (DubNarrationStyle) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (continuousFullVideoDub) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AllInclusive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ဗီဒီယို အစမှအဆုံး မရပ်တမ်း ပြောမည်",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (continuousFullVideoDub) {
                                "ON • 00:00 မှ အဆုံးထိ တဝက်တပြက်မရပ်ဘဲ ၁၀၀% မြန်မာအသံ Sync"
                            } else {
                                "Dialogue Only • စကားပြောသည့်နေရာများကိုသာ ဘာသာပြန်မည်"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(
                    checked = continuousFullVideoDub,
                    onCheckedChange = { if (!isBusy) onContinuousChanged(it) },
                    modifier = Modifier.testTag("continuous_dub_switch")
                )
            }

            Text(
                text = "NARRATION & SYNC STYLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DubNarrationStyle.entries) { style ->
                    FilterChip(
                        selected = narrationStyle == style,
                        onClick = { if (!isBusy) onNarrationStyleSelected(style) },
                        label = {
                            Text(
                                text = style.burmeseLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("narration_style_${style.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun EditableSyncedTimelineSection(
    detectedLanguage: String?,
    summaryBurmese: String?,
    segments: List<DubSegment>,
    totalDurationMs: Long = 30_000L,
    isPreviewingVoice: Boolean,
    onPreviewLine: (String) -> Unit,
    onSegmentEdited: (String, String) -> Unit,
    onNudgeSegment: (String, Long, Long) -> Unit = { _, _, _ -> },
    onAutoFillGaps: () -> Unit = {},
    onDeleteSegment: (String) -> Unit,
    onAddSegment: () -> Unit,
    onCopySrt: () -> Unit,
    onRerenderClick: () -> Unit
) {
    val safeTotalMs = totalDurationMs.coerceAtLeast(
        segments.maxOfOrNull { it.endMs } ?: 1_500L
    )
    val coveragePercent = DubSegment.computeTimelineCoveragePercent(segments, safeTotalMs)
    val hasCutoff = DubSegment.hasTrailingCutoff(segments, safeTotalMs)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synced Burmese Timeline (${segments.size} beats)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!detectedLanguage.isNullOrBlank()) {
                        Text(
                            text = "Audio: $detectedLanguage → Burmese • Sample-Synced",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onCopySrt,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(42.dp)
                        .testTag("copy_srt_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SRT", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!summaryBurmese.isNullOrBlank()) {
                Text(
                    text = summaryBurmese,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Visual Canvas Timeline Coverage Bar + Auto-Fill Gaps action
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (coveragePercent >= 85 && !hasCutoff) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Full-Video Sync Coverage: $coveragePercent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "00:00 → ${DubSegment.formatTimestamp(safeTotalMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val trackBg = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                    val activeBarColor = MaterialTheme.colorScheme.secondary
                    val borderTickColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        drawRoundRect(
                            color = trackBg,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                        )
                        segments.forEach { seg ->
                            val startFrac = (seg.startMs.toFloat() / safeTotalMs.toFloat()).coerceIn(0f, 1f)
                            val endFrac = (seg.endMs.toFloat() / safeTotalMs.toFloat()).coerceIn(startFrac, 1f)
                            val x = startFrac * size.width
                            val w = ((endFrac - startFrac) * size.width).coerceAtLeast(3.dp.toPx())
                            drawRoundRect(
                                color = activeBarColor,
                                topLeft = Offset(x, 2.dp.toPx()),
                                size = Size(w, size.height - 4.dp.toPx()),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawLine(
                                color = borderTickColor,
                                start = Offset(x, 2.dp.toPx()),
                                end = Offset(x, size.height - 2.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }

                    if (coveragePercent < 94 || hasCutoff) {
                        OutlinedButton(
                            onClick = onAutoFillGaps,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auto_fill_gaps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "နေရာလွတ်များကို အဆုံးထိ မြန်မာစကားဖြည့်မည် (Fill Gaps)",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily),
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            segments.forEach { segment ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = segment.formattedTimeRange,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onNudgeSegment(segment.id, -500L, -500L) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("segment_nudge_earlier_${segment.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastRewind,
                                        contentDescription = "Shift 0.5s earlier",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onNudgeSegment(segment.id, 500L, 500L) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("segment_nudge_later_${segment.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastForward,
                                        contentDescription = "Shift 0.5s later",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onPreviewLine(segment.burmeseText) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewingVoice) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        contentDescription = "Hear line",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (segments.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteSegment(segment.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete beat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (segment.originalSpeech.isNotBlank()) {
                            Text(
                                text = "EN: ${segment.originalSpeech}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = segment.burmeseText,
                            onValueChange = { onSegmentEdited(segment.id, it) },
                            label = { Text("Burmese Voice & Subtitle") },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dub_segment_input_${segment.id}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onAddSegment,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp)
                        .testTag("add_dub_line_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Line", maxLines = 1)
                }

                Button(
                    onClick = onRerenderClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("rerender_dub_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-Dub & Render", maxLines = 1)
                }
            }
        }
    }
}
