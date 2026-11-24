package com.example.ui

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.model.RecapPlatform
import com.example.model.VideoClipSummary
import com.example.ui.theme.MyanmarScriptFamily

@Composable
fun StudioScreen(
    uiState: StudioUiState,
    onVideoPicked: (android.net.Uri) -> Unit,
    onClearVideo: () -> Unit,
    onPlatformSelected: (RecapPlatform) -> Unit,
    onGenreSelected: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNotifyCopied: (String) -> Unit,
    onOpenApiKeySettings: () -> Unit = {},
    onGenerateOfflineFallback: () -> Unit = {},
    onSendToVideoDub: (android.net.Uri?) -> Unit = {},
    onOpenBilling: () -> Unit = {},
    showSwarmAnimation: Boolean = true,
    accessBadgeText: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showTeleprompter by remember { mutableStateOf(false) }

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

    val generatedScript = (uiState.generationState as? StudioGenerationState.Generated)?.script
    if (showTeleprompter && generatedScript != null) {
        TeleprompterDialog(
            script = generatedScript,
            onDismiss = { showTeleprompter = false }
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Studio Header Banner
            StudioHeaderCard()

            // Video Picker / Preview Card
            VideoClipSelectorCard(
                video = uiState.selectedVideo,
                isInspecting = uiState.isInspectingVideo,
                onPickVideo = launchVideoPicker,
                onClearVideo = onClearVideo
            )

            // Platform Selection (YouTube vs TikTok)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target Format",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecapPlatform.entries.forEach { platform ->
                        val selected = uiState.selectedPlatform == platform
                        Surface(
                            onClick = { onPlatformSelected(platform) },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("platform_${platform.name.lowercase()}_chip")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = platform.displayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = platform.burmeseHint,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = MyanmarScriptFamily,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Genre Tone Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Story Tone / Genre",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(RecapViewModel.GENRE_OPTIONS) { genre ->
                        val selected = uiState.selectedGenre == genre
                        FilterChip(
                            selected = selected,
                            onClick = { onGenreSelected(genre) },
                            label = { Text(genre) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("genre_${genre.lowercase()}_chip")
                        )
                    }
                }
            }

            // Optional Story / Character Context
            OutlinedTextField(
                value = uiState.directorNotes,
                onValueChange = onNotesChanged,
                label = { Text("Optional Character / Plot Context") },
                placeholder = {
                    Text(
                        "ဥပမာ - ဇာတ်လိုက်က သူဌေးသားဟန်ဆောင်နေတဲ့ စုံထောက်တစ်ယောက်...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily)
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("director_notes_input")
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
                        .testTag("studio_access_badge_card")
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
                            text = "Account • VIP →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Generate CTA
            val isGenerating = uiState.generationState is StudioGenerationState.Processing
            Button(
                onClick = onGenerateClick,
                enabled = uiState.selectedVideo != null && !isGenerating && !uiState.isInspectingVideo,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("generate_script_button")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Analyzing Video...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.generate_script_cta),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Quick Jump to AI Video Dubbing Studio
            Surface(
                onClick = { onSendToVideoDub(uiState.selectedVideo?.uri) },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("studio_go_to_dub_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Video Dub & Burmese Voiceover Studio",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (uiState.selectedVideo != null) {
                                "ဤဗီဒီယိုကို မြန်မာအသံထပ် + စာတန်းထိုး စတူဒီယိုသို့ တိုက်ရိုက်ပို့မည်"
                            } else {
                                "ဗီဒီယို အစမှအဆုံး မြန်မာအသံထပ် & Watermark စတူဒီယိုသို့ သွားမည်"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MyanmarScriptFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Open Dub →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Processing Progress Banner (0% -> 100% Ring + AI Swarm Timeline)
            AnimatedVisibility(visible = uiState.generationState is StudioGenerationState.Processing) {
                val processing = uiState.generationState as? StudioGenerationState.Processing
                if (processing != null) {
                    val activeStep = when {
                        processing.progressPercent < 40 -> 1
                        processing.progressPercent < 75 -> 2
                        else -> 3
                    }
                    SwarmVideoProgressCard(
                        progressPercent = processing.progressPercent,
                        stepHeadline = stringResource(R.string.generating_script_title),
                        stageMessage = processing.stageMessage,
                        activeStepIndex = activeStep,
                        showSwarmAnimation = showSwarmAnimation
                    )
                }
            }

            // Error Card
            AnimatedVisibility(visible = uiState.generationState is StudioGenerationState.Error) {
                val errorState = uiState.generationState as? StudioGenerationState.Error
                if (errorState != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Generation Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenApiKeySettings,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("error_open_api_key_button")
                                ) {
                                    Text("API Key", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onGenerateClick,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("retry_generate_button")
                                ) {
                                    Text("Try Again", maxLines = 1)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onGenerateOfflineFallback,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("studio_offline_template_button")
                            ) {
                                Text("Use Editable Burmese Template (Offline)")
                            }
                        }
                    }
                }
            }

            // Generated Script Output
            AnimatedVisibility(visible = uiState.generationState is StudioGenerationState.Generated) {
                val generated = uiState.generationState as? StudioGenerationState.Generated
                if (generated != null) {
                    val script = generated.script
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${script.genre.uppercase()} • ${script.platformEnum().displayLabel} • ${script.estimatedDuration}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = script.sourceTitle,
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MyanmarScriptFamily),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Row: Save, Copy All, Teleprompter
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isSaved = generated.savedDocId != null
                                    Button(
                                        onClick = onSaveClick,
                                        enabled = !isSaved && !uiState.isSaving,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("save_to_vault_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSaved) "Saved" else if (uiState.isSaving) "Saving..." else "Save",
                                            maxLines = 1
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(script.formatFullScriptForExport()))
                                            onNotifyCopied("Full Burmese script copied to clipboard")
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("copy_full_script_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy All", maxLines = 1)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showTeleprompter = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_teleprompter_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Open Voiceover Teleprompter",
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        RecapScriptSections(
                            script = script,
                            onCopyText = { label, text ->
                                clipboardManager.setText(AnnotatedString(text))
                                onNotifyCopied("$label copied")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StudioHeaderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 136.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = "Myanmar Movie Recap Studio header",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.62f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = "RECAP STORYTELLER STUDIO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ဗီဒီယိုထည့်ပြီး မြန်မာစကားပြောဟန်\nMovie Recap Script ထုတ်ပါ",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MyanmarScriptFamily,
                    lineHeight = 28.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun VideoClipSelectorCard(
    video: VideoClipSummary?,
    isInspecting: Boolean,
    onPickVideo: () -> Unit,
    onClearVideo: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
                Text("Inspecting video metadata...", style = MaterialTheme.typography.bodyMedium)
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
                    text = "Pick a Movie Clip from Gallery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uses Android Photo Picker (no storage permission needed). Clips under 10MB analyze full video; larger clips analyze 10 timestamped keyframes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPickVideo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("pick_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieCreation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_video_cta))
                }
            }
        } else {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
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
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                    IconButton(
                        onClick = onClearVideo,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("clear_video_button")
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove selected video",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Text(
                                text = "${video.formattedDuration} • ${video.formattedSize} • " +
                                    if (video.isInlineEligible) "Full Video AI Mode" else "Keyframe Timeline AI Mode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickVideo,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("change_video_button")
                ) {
                    Text(stringResource(R.string.change_video_cta))
                }
            }
        }
    }
}
