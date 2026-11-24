package com.example.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ActiveVoiceSelection
import com.example.model.ClonedVoiceProfile
import com.example.model.DubVoicePreset
import com.example.model.VoiceCategory
import com.example.ui.theme.MyanmarScriptFamily

/**
 * Selection dialog in the video editing screen allowing users to browse and audition
 * different AI-generated voice profiles for Burmese dubbing or choose 'Clone My Voice'.
 */
@Composable
fun VoiceProfileSelectionDialog(
    selectedVoice: ActiveVoiceSelection,
    selectedCategory: VoiceCategory,
    clonedVoices: List<ClonedVoiceProfile>,
    isPreviewingVoice: Boolean,
    previewingVoiceId: String?,
    onSelectCategory: (VoiceCategory) -> Unit,
    onSelectBuiltIn: (DubVoicePreset) -> Unit,
    onSelectCloned: (ClonedVoiceProfile) -> Unit,
    onDeleteCloned: (String) -> Unit,
    onAuditionSpecificVoice: (ActiveVoiceSelection) -> Unit,
    onCloneMyVoiceClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val showClones = (selectedCategory == VoiceCategory.ALL || selectedCategory == VoiceCategory.CLONED) &&
        clonedVoices.isNotEmpty()

    val builtInList = when (selectedCategory) {
        VoiceCategory.ALL -> DubVoicePreset.entries
        VoiceCategory.MALE -> DubVoicePreset.entries.filter { it.isMale }
        VoiceCategory.FEMALE -> DubVoicePreset.entries.filter { !it.isMale }
        VoiceCategory.CLONED -> emptyList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .heightIn(max = 640.dp)
                .testTag("voice_profile_selection_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
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
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Choose AI Dubbing Voice",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "မြန်မာအသံထပ်ရန် AI အသံ (သို့) Voice Clone ရွေးချယ်ပါ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = MyanmarScriptFamily,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_voice_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close voice selector"
                        )
                    }
                }

                // Prominent 'Clone My Voice' Option inside Dialog
                Surface(
                    onClick = onCloneMyVoiceClick,
                    color = ColorTransparentFallback(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .testTag("dialog_clone_my_voice_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Clone My Voice",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "ကိုယ်ပိုင်အသံ (သို့) Video/Audio ဖြင့် အသံတုအသစ် ဖန်တီးမည်",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = MyanmarScriptFamily,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Category Filter Tabs
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VoiceCategory.entries) { category ->
                        val label = when (category) {
                            VoiceCategory.ALL -> "All (${DubVoicePreset.entries.size + clonedVoices.size})"
                            VoiceCategory.CLONED -> "Voice Clone (${clonedVoices.size})"
                            else -> category.label
                        }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dialog_voice_cat_${category.name.lowercase()}")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Voice Profiles List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .testTag("voice_profile_list")
                ) {
                    if (showClones) {
                        item {
                            Text(
                                text = "YOUR CLONED VOICES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        items(clonedVoices, key = { "clone_${it.id}" }) { profile ->
                            val isSelected = (selectedVoice as? ActiveVoiceSelection.Cloned)?.profile?.id == profile.id
                            val isPlayingThis = isPreviewingVoice && previewingVoiceId == profile.id
                            VoiceProfileRowCard(
                                title = profile.name,
                                subtitle = "${profile.vocalDescription.ifBlank { profile.sourceLabel }} • Base: ${profile.matchedBaseVoice}",
                                tagText = "CLONED",
                                isSelected = isSelected,
                                isPlaying = isPlayingThis,
                                onSelect = {
                                    onSelectCloned(profile)
                                    onDismiss()
                                },
                                onAudition = {
                                    onAuditionSpecificVoice(ActiveVoiceSelection.Cloned(profile))
                                },
                                onDelete = { onDeleteCloned(profile.id) },
                                modifier = Modifier.testTag("dialog_cloned_voice_${profile.id}")
                            )
                        }
                    } else if (selectedCategory == VoiceCategory.CLONED && clonedVoices.isEmpty()) {
                        item {
                            Surface(
                                onClick = onCloneMyVoiceClick,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No cloned voices saved yet. Tap \"Clone My Voice\" above to record or upload a voice clip.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    if (builtInList.isNotEmpty()) {
                        item {
                            Text(
                                text = "AI HUMAN VOICE PROFILES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(builtInList, key = { "preset_${it.id}" }) { preset ->
                            val isSelected = (selectedVoice as? ActiveVoiceSelection.BuiltIn)?.preset == preset
                            val isPlayingThis = isPreviewingVoice && previewingVoiceId == preset.id
                            VoiceProfileRowCard(
                                title = preset.displayName,
                                subtitle = preset.burmeseLabel,
                                tagText = if (preset.isMale) "MALE" else "FEMALE",
                                isSelected = isSelected,
                                isPlaying = isPlayingThis,
                                onSelect = {
                                    onSelectBuiltIn(preset)
                                    onDismiss()
                                },
                                onAudition = {
                                    onAuditionSpecificVoice(ActiveVoiceSelection.BuiltIn(preset))
                                },
                                onDelete = null,
                                modifier = Modifier.testTag("dialog_preset_voice_${preset.id}")
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_voice_dialog_button")
                ) {
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ColorTransparentFallback() = androidx.compose.ui.graphics.Color.Transparent

@Composable
private fun VoiceProfileRowCard(
    title: String,
    subtitle: String,
    tagText: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onAudition: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 1.6.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audition Play/Stop Circle
            Surface(
                onClick = onAudition,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Stop voice preview" else "Hear Burmese sample",
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (tagText == "CLONED") {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (tagText == "CLONED") {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MyanmarScriptFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete cloned voice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected voice",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(22.dp)
                )
            }
        }
    }
}
