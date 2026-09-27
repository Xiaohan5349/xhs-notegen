package com.xiaohan.xhsnotegen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xiaohan.xhsnotegen.ai.AiProvider
import com.xiaohan.xhsnotegen.ai.AiSettings
import com.xiaohan.xhsnotegen.ai.PromptStore
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.AppTheme
import com.xiaohan.xhsnotegen.ui.theme.Backdrop
import com.xiaohan.xhsnotegen.ui.theme.ThemeGroup
import com.xiaohan.xhsnotegen.ui.theme.AppearanceStore
import com.xiaohan.xhsnotegen.ui.theme.DarkMode

/** Everything is saved as you change it — there is no Save button to forget. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogin: () -> Unit,
    onEditPrompt: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceSection()
            AiSection(onEditPrompt)
            AccountSection(onLogin)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val theme by AppearanceStore.theme.collectAsState()
    val mode by AppearanceStore.darkMode.collectAsState()

    SectionCard(title = "Appearance") {
        ThemeGroup.entries.forEach { group ->
            Text(group.label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppTheme.entries.filter { it.group == group }.forEach { t ->
                    ThemeSwatch(t, selected = t == theme) { AppearanceStore.setTheme(context, t) }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == mode,
                    onClick = { AppearanceStore.setDarkMode(context, m) },
                    shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = m == mode) {
                            Icon(
                                when (m) {
                                    DarkMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    DarkMode.LIGHT -> Icons.Outlined.LightMode
                                    DarkMode.DARK -> Icons.Outlined.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        }
                    },
                ) { Text(m.label) }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val (accent, container) = theme.swatch
    Column(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(swatchBrush(theme, container)),
            contentAlignment = Alignment.Center,
        ) {
            // Half-and-half dot: accent over its container.
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp),
                    tint = if (accent.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
        Text(theme.displayName, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Anime-inspired themes preview their backdrop colors; the rest their accent container. */
private fun swatchBrush(theme: AppTheme, container: Color): Brush = when (theme.backdrop) {
    Backdrop.SKY -> Brush.verticalGradient(listOf(Color(0xFF5AA9F5), Color(0xFFDCEEFF)))
    Backdrop.SUNSET -> Brush.verticalGradient(listOf(Color(0xFFFFB199), Color(0xFFFF7EB3), Color(0xFFC77DFF)))
    Backdrop.SEIGAIHA -> Brush.verticalGradient(listOf(Color(0xFFFFDAD3), Color(0xFFDBE1FA)))
    Backdrop.NONE -> SolidColor(container)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------------------
// AI writing
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AiSection(onEditPrompt: () -> Unit) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(AiSettings.provider(context)) }
    // Re-read per provider so each keeps its own key and model.
    var apiKey by remember(provider) { mutableStateOf(AiSettings.apiKey(context, provider)) }
    var model by remember(provider) { mutableStateOf(AiSettings.model(context, provider)) }
    var baseUrl by remember { mutableStateOf(AiSettings.customBaseUrl(context)) }
    var vision by remember { mutableStateOf(AiSettings.customVision(context)) }
    var showKey by remember { mutableStateOf(false) }

    val builtIn = provider.findModel(model)
    // The free-text field shows only ids that aren't one of the listed options.
    var otherModel by remember(provider) { mutableStateOf(if (provider.findModel(model) == null) model else "") }

    SectionCard(
        title = "AI writing",
        subtitle = "Which model writes your notes. Keys stay on this phone.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                FilterChip(
                    selected = p == provider,
                    onClick = { provider = p; AiSettings.setProvider(context, p) },
                    label = { Text(p.displayName) },
                    shape = CircleShape,
                )
            }
        }

        if (provider.models.isNotEmpty()) {
            Column(Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)) {
                provider.models.forEach { m ->
                    val selected = m.id == model && otherModel.isBlank()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                model = m.id; otherModel = ""
                                AiSettings.setModel(context, provider, m.id)
                            })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.label, style = MaterialTheme.typography.titleSmall)
                                if (m.id == provider.defaultModel) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Recommended", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(m.note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (provider == AiProvider.CUSTOM) {
            TextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; AiSettings.setCustomBaseUrl(context, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API address") },
                placeholder = { Text("https://example.com/v1") },
                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = MaterialTheme.shapes.medium,
                colors = softFieldColors(),
                supportingText = { Text("OpenAI-compatible base URL; /chat/completions is added for you") },
            )
        }

        TextField(
            value = if (provider == AiProvider.CUSTOM) model else otherModel,
            onValueChange = { v ->
                if (provider == AiProvider.CUSTOM) model = v else otherModel = v
                val chosen = v.ifBlank { if (provider == AiProvider.CUSTOM) "" else provider.defaultModel }
                if (provider != AiProvider.CUSTOM && v.isBlank()) model = provider.defaultModel
                AiSettings.setModel(context, provider, chosen)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (provider == AiProvider.CUSTOM) "Model" else "Other model (optional)") },
            placeholder = { Text(if (provider == AiProvider.CUSTOM) "e.g. qwen-vl-max" else "Any ${provider.displayName} model id") },
            leadingIcon = { Icon(Icons.Outlined.Memory, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = softFieldColors(),
            supportingText = if (provider != AiProvider.CUSTOM) {
                { Text("Overrides the list above — for models released after this app version") }
            } else null,
        )

        TextField(
            value = apiKey,
            onValueChange = { apiKey = it; AiSettings.setApiKey(context, provider, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${provider.displayName} API key") },
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
            supportingText = { Text(provider.keyHint) },
        )

        if (provider == AiProvider.CUSTOM) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Model can see photos", style = MaterialTheme.typography.titleSmall)
                    Text("Turn off for text-only models — only your notes are sent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vision, onCheckedChange = { vision = it; AiSettings.setCustomVision(context, it) })
            }
        } else if (builtIn != null && !builtIn.vision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HideImage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text("This model can't see images, so notes are written from your text only.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Entry to the prompt editor; "Customized" reminds you when you've changed it.
        var customized by remember { mutableStateOf(PromptStore.isCustomized(context)) }
        LifecycleResumeEffect(Unit) {
            customized = PromptStore.isCustomized(context) // refresh after returning from the editor
            onPauseOrDispose {}
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onEditPrompt)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Writing prompt", style = MaterialTheme.typography.titleSmall)
                Text(if (customized) "Customized" else "Default — tap to view or edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (customized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Xiaohongshu account
// ---------------------------------------------------------------------------

@Composable
private fun AccountSection(onLogin: () -> Unit) {
    val context = LocalContext.current
    val loggedIn by XhsAuthStore.loggedIn.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    SectionCard(title = "Xiaohongshu") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    if (loggedIn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
            )
            Column(Modifier.weight(1f)) {
                Text(if (loggedIn) "Connected" else "Not connected", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (loggedIn) "Notes post straight to your account" else "Log in to post directly from the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loggedIn) {
                TextButton(onClick = { confirmLogout = true }) { Text("Log out") }
            } else {
                FilledTonalButton(onClick = onLogin) { Text("Log in") }
            }
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
