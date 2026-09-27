package com.xiaohan.xhsnotegen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.ui.components.Eyebrow
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.generate.GeminiClient
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val loggedIn by XhsAuthStore.loggedIn.collectAsState()

    var apiKey by remember { mutableStateOf(GeminiClient.getApiKey(context).orEmpty()) }
    var model by remember {
        mutableStateOf(GeminiClient.getModel(context).takeIf { it != GeminiClient.DEFAULT_MODEL }.orEmpty())
    }
    var showKey by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun saveAndClose() {
        GeminiClient.saveApiKey(context, apiKey)
        GeminiClient.saveModel(context, model)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            // ---- Gemini ----
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Writing assistant")
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API key") },
                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key")
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                    supportingText = { Text("Free key: aistudio.google.com/apikey") },
                )
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    placeholder = { Text(GeminiClient.DEFAULT_MODEL) },
                    leadingIcon = { Icon(Icons.Outlined.Memory, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                    supportingText = { Text("Leave empty for the default. Any Gemini model id that accepts images works.") },
                )
            }

            // ---- XHS account ----
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Xiaohongshu")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(
                                if (loggedIn) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline
                            )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(if (loggedIn) "Connected" else "Not connected",
                                style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (loggedIn) "Notes post straight to your account"
                                else "Log in to post directly from the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (loggedIn) {
                            TextButton(onClick = { confirmLogout = true }) { Text("Log out") }
                        } else {
                            FilledTonalButton(onClick = { onDismiss(); onLogin() }) { Text("Log in") }
                        }
                    }
                }
            }

            Button(
                onClick = ::saveAndClose,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out of Xiaohongshu?") },
            text = { Text("You can still post by hand — the app copies the text and saves the photos for you.") },
            confirmButton = {
                TextButton(onClick = { XhsAuthStore.clear(context); confirmLogout = false }) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}
