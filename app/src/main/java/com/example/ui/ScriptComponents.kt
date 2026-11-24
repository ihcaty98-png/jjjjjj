package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.RecapScript
import com.example.model.SceneBeat
import com.example.ui.theme.MyanmarScriptFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecapScriptSections(
    script: RecapScript,
    onCopyText: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: 3 Viral Titles
        SectionHeader(
            numberBadge = "၁",
            title = "Viral Titles (ဆွဲဆောင်မှုရှိသော ခေါင်းစဉ် ၃ ခု)",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        script.viralTitles.forEachIndexed { index, title ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = MyanmarScriptFamily,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopyText("Title #${index + 1}", title) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_title_${index}_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy title ${index + 1}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Section 2: 0:00 - 0:15 Opening Hook
        SectionHeader(
            numberBadge = "၂",
            title = "Opening Hook (0:00 - 0:15 အဖွင့်စကားလုံး)",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "00:00 - 00:15 HOOK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCopyText("Opening Hook", script.openingHook) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_hook_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy opening hook",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = script.openingHook,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MyanmarScriptFamily,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Section 3: Scene-by-Scene Script [MM:SS]
        SectionHeader(
            numberBadge = "၃",
            title = "Scene-by-Scene Script (${script.scenes.size} Scenes)",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        script.scenes.forEachIndexed { idx, beat ->
            SceneBeatCard(
                beat = beat,
                index = idx,
                onCopy = {
                    val ts = if (beat.timestamp.startsWith("[")) beat.timestamp else "[${beat.timestamp}]"
                    onCopyText("Scene $ts", "$ts ${beat.voiceover}")
                }
            )
        }

        // Section 4: Comment-bait Outro
        SectionHeader(
            numberBadge = "၄",
            title = "Outro (Comment မေးခွန်းအပိတ်စကား)",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENGAGEMENT OUTRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onCopyText("Outro", script.outro) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_outro_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy outro",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = script.outro,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MyanmarScriptFamily,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    numberBadge: String,
    title: String,
    icon: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = numberBadge,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SceneBeatCard(
    beat: SceneBeat,
    index: Int,
    onCopy: () -> Unit
) {
    val cleanTs = if (beat.timestamp.startsWith("[")) beat.timestamp else "[${beat.timestamp}]"
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = cleanTs,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("copy_scene_${index}_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy scene $cleanTs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (beat.visualCue.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scene: ${beat.visualCue}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = beat.voiceover,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MyanmarScriptFamily,
                    lineHeight = 28.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TeleprompterDialog(
    script: RecapScript,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    var fontSizeSp by remember { mutableFloatStateOf(24f) }

    LaunchedEffect(isPlaying, scrollSpeed) {
        while (isPlaying) {
            if (scrollState.value < scrollState.maxValue) {
                val delta = (3f * scrollSpeed).toInt().coerceAtLeast(1)
                scrollState.scrollTo((scrollState.value + delta).coerceAtMost(scrollState.maxValue))
            } else {
                isPlaying = false
            }
            delay(32L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VOICEOVER TELEPROMPTER",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = script.sourceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_teleprompter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close teleprompter",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Scrolling Script Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "[00:00 - 00:15] HOOK",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = script.openingHook,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = MyanmarScriptFamily,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    script.scenes.forEach { beat ->
                        val ts = if (beat.timestamp.startsWith("[")) beat.timestamp else "[${beat.timestamp}]"
                        Text(
                            text = ts,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = beat.voiceover,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.55f).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "[OUTRO]",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = script.outro,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = MyanmarScriptFamily,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(140.dp))
                }

                // Teleprompter Controls
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Scroll Speed: ${String.format("%.1fx", scrollSpeed)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Size: ${fontSizeSp.toInt()}sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = scrollSpeed,
                                onValueChange = { scrollSpeed = it },
                                valueRange = 0.5f..2.5f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextButton(
                                onClick = { fontSizeSp = if (fontSizeSp >= 30f) 20f else fontSizeSp + 4f },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Aa")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    isPlaying = false
                                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("teleprompter_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Reset scroll to top"
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Button(
                                onClick = { isPlaying = !isPlaying },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("teleprompter_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Start auto-scroll"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isPlaying) "Pause" else "Start Reading")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditScriptDialog(
    script: RecapScript,
    onDismiss: () -> Unit,
    onSave: (title: String, hook: String, outro: String, notes: String?) -> Unit
) {
    var title by remember { mutableStateOf(script.sourceTitle) }
    var hook by remember { mutableStateOf(script.openingHook) }
    var outro by remember { mutableStateOf(script.outro) }
    var notes by remember { mutableStateOf(script.directorNotes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Script", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(160) },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_title_input")
                )
                OutlinedTextField(
                    value = hook,
                    onValueChange = { hook = it.take(2500) },
                    label = { Text("0:00 - 0:15 Opening Hook") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_hook_input")
                )
                OutlinedTextField(
                    value = outro,
                    onValueChange = { outro = it.take(2000) },
                    label = { Text("Outro") },
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_outro_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = { Text("Director Notes (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, hook, outro, notes.takeIf { it.isNotBlank() }) },
                enabled = title.isNotBlank() && hook.isNotBlank() && outro.isNotBlank(),
                modifier = Modifier.testTag("save_edit_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
