package com.example.ui

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.DirectGeminiClient
import com.example.data.ApiKeyConfigState
import com.example.data.ApiKeySource
import com.example.data.ApiKeyStore
import kotlinx.coroutines.launch

@Composable
fun ApiKeySettingsDialog(
    apiKeyState: ApiKeyConfigState,
    onSaved: (String) -> Unit,
    onCleared: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var keyInput by remember(apiKeyState) {
        mutableStateOf(ApiKeyStore.getUserCustomKey().orEmpty())
    }
    var showSecret by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatusOk by remember { mutableStateOf<Boolean?>(null) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.api_key_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.api_key_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val (statusTitle, statusAccent) = when (apiKeyState.source) {
                    ApiKeySource.USER_CUSTOM ->
                        "Active: Custom Saved Key (${apiKeyState.maskedPreview})" to MaterialTheme.colorScheme.secondary
                    ApiKeySource.ENVIRONMENT ->
                        "Active: Default App Key (${apiKeyState.maskedPreview})" to MaterialTheme.colorScheme.primary
                    ApiKeySource.NONE ->
                        "No API Key Saved (Video Dub runs Client-Side Fallback)" to MaterialTheme.colorScheme.error
                }

                Surface(
                    color = statusAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusAccent
                        )
                    }
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        testStatusOk = null
                        testStatusMessage = null
                    },
                    label = { Text("Gemini API Key (AIza...)") },
                    placeholder = { Text("Paste your Gemini API key here") },
                    singleLine = true,
                    visualTransformation = if (showSecret) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clip.isNotBlank()) {
                                        keyInput = clip
                                        testStatusOk = null
                                        testStatusMessage = null
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("paste_api_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste API key from clipboard"
                                )
                            }
                            IconButton(
                                onClick = { showSecret = !showSecret },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_api_key_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showSecret) "Hide API key" else "Show API key"
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val candidate = keyInput.trim().trim('"', '\'').ifBlank { ApiKeyStore.getEffectiveApiKey().orEmpty() }
                            if (candidate.isBlank()) {
                                testStatusOk = false
                                testStatusMessage = "Please paste or enter a Gemini API key first."
                                return@OutlinedButton
                            }
                            isTesting = true
                            testStatusMessage = null
                            scope.launch {
                                val res = DirectGeminiClient.testApiKey(candidate)
                                isTesting = false
                                res.onSuccess { okMsg ->
                                    testStatusOk = true
                                    testStatusMessage = okMsg
                                }.onFailure { err ->
                                    testStatusOk = false
                                    testStatusMessage = (err.message ?: "API key check failed.") +
                                        "\n(Video Dub တွင် Clear နှိပ်၍ Client-Side Fallback ဖြင့် ဆက်သုံးနိုင်ပါသည်)"
                                }
                            }
                        },
                        enabled = !isTesting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_api_key_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Key")
                        }
                    }

                    if (apiKeyState.hasCustomKey || keyInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                keyInput = ""
                                testStatusOk = null
                                testStatusMessage = null
                                ApiKeyStore.clearUserApiKey()
                                onCleared()
                            },
                            enabled = !isTesting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear saved API key",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }

                testStatusMessage?.let { msg ->
                    val feedbackColor = if (testStatusOk == true) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Surface(
                        color = feedbackColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = feedbackColor,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = keyInput.trim().trim('"', '\'').replace("\n", "").replace("\r", "")
                    if (trimmed.isNotEmpty()) {
                        ApiKeyStore.saveUserApiKey(trimmed)
                        onSaved("Gemini API Key saved locally")
                    } else {
                        ApiKeyStore.clearUserApiKey()
                        onCleared()
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_api_key_button")
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("cancel_api_key_button")
            ) {
                Text("Close")
            }
        },
        modifier = Modifier.testTag("api_key_settings_dialog")
    )
}
