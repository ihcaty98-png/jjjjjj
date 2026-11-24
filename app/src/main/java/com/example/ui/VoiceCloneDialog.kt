package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.media.VoiceSampleRecorder
import com.example.ui.theme.MyanmarScriptFamily
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VoiceCloneDialog(
    isCreating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onCreateFromUri: (Uri, String) -> Unit,
    onCreateFromRecordedFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf("My Burmese Narrator") }
    var isRecording by remember { mutableStateOf(false) }
    var recordedSeconds by remember { mutableIntStateOf(0) }
    var micPermissionError by remember { mutableStateOf<String?>(null) }

    val recorder = remember(context) { VoiceSampleRecorder(context.applicationContext) }

    DisposableEffect(recorder) {
        onDispose {
            if (isRecording) {
                recorder.cancelRecording()
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordedSeconds = 0
            while (isRecording && recordedSeconds < 15) {
                delay(1000L)
                recordedSeconds++
            }
            if (isRecording && recordedSeconds >= 15) {
                isRecording = false
                val recorded = recorder.stopRecording()
                if (recorded != null) {
                    onCreateFromRecordedFile(recorded, profileName)
                }
            }
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onClearError()
                onCreateFromUri(uri, profileName)
            }
        }
    )

    val startRecordingAction = {
        onClearError()
        micPermissionError = null
        runCatching {
            recorder.startRecording()
            isRecording = true
        }.onFailure {
            micPermissionError = it.localizedMessage ?: "Unable to access microphone."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                startRecordingAction()
            } else {
                micPermissionError = "Microphone permission denied. You can still upload any audio or video voice sample below."
            }
        }
    )

    AlertDialog(
        onDismissRequest = { if (!isCreating && !isRecording) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Voice Clone Studio")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "မိမိအသံ (သို့) နှစ်သက်ရာ အသံနမူနာ (5–15 စက္ကန့်) ကို ထည့်သွင်းပြီး AI ဖြင့် Tone, Pitch နှင့် စကားပြောဟန် တူအောင် ဖန်တီးပါ။",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it.take(28) },
                    label = { Text("Voice Clone Name") },
                    singleLine = true,
                    enabled = !isCreating && !isRecording,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_clone_name_input")
                )

                if (isCreating) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Analyzing voice timbre, pitch & cadence...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Option 1: Record Mic Sample
                    Button(
                        onClick = {
                            if (isRecording) {
                                isRecording = false
                                val recorded = recorder.stopRecording()
                                if (recorded != null) {
                                    onCreateFromRecordedFile(recorded, profileName)
                                } else {
                                    micPermissionError = "Recording was too short. Please speak for at least 3 seconds."
                                }
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    startRecordingAction()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("record_voice_clone_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isRecording) {
                                "Stop & Clone Voice (${recordedSeconds}s / 15s)"
                            } else {
                                "Record Mic Voice Sample"
                            }
                        )
                    }

                    // Option 2: Upload Audio or Video Clip (zero permission)
                    OutlinedButton(
                        onClick = {
                            audioFilePicker.launch(arrayOf("audio/*", "video/*"))
                        },
                        enabled = !isRecording,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("upload_voice_clone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Audio / Video Voice Clip")
                    }
                }

                val errToShow = errorMessage ?: micPermissionError
                if (!errToShow.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errToShow,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        recorder.cancelRecording()
                    }
                    onDismiss()
                },
                enabled = !isCreating
            ) {
                Text("Close")
            }
        }
    )
}
