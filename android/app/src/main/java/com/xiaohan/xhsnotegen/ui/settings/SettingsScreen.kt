package com.xiaohan.xhsnotegen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.xiaohan.xhsnotegen.i18n.AppLanguage
import com.xiaohan.xhsnotegen.i18n.LanguageStore
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.InsetSegmented
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.theme.NumberStyle
import com.xiaohan.xhsnotegen.ui.theme.app
import com.xiaohan.xhsnotegen.ui.drafts.HomePrefs
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
    onOpenAi: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Settings", "设置")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "返回"))
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
            HomeSection()
            AiSection(onOpenAi, onEditPrompt)
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

    SectionCard(title = tr("Appearance", "外观")) {
        ThemeGroup.entries.forEach { group ->
            Text(group.label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Five to a row, each a little preview: the theme's container color with its accent.
            AppTheme.entries.filter { it.group == group }.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        ThemeSwatch(t, selected = t == theme, modifier = Modifier.weight(1f)) { AppearanceStore.setTheme(context, t) }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        InsetSegmented(
            options = DarkMode.entries,
            selected = mode,
            label = { it.label },
            onSelect = { AppearanceStore.setDarkMode(context, it) },
            icon = { m ->
                when (m) {
                    DarkMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    DarkMode.LIGHT -> Icons.Outlined.LightMode
                    DarkMode.DARK -> Icons.Outlined.DarkMode
                }
            },
        )
        Text(tr("Language", "语言"), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        InsetSegmented(
            options = AppLanguage.entries,
            selected = LanguageStore.choice,
            label = { it.label },
            onSelect = { LanguageStore.set(context, it) },
        )
        Text(
            tr("App language. The language notes are written in is set per writing mode.",
                "界面语言。笔记用什么语言写，在每个写作模式里单独设置。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Home screen
// ---------------------------------------------------------------------------

/** Put the Group buttons on the home screen in the order you like. */
@Composable
private fun HomeSection() {
    val context = LocalContext.current
    val order by HomePrefs.groupOrder.collectAsState()

    SectionCard(
        title = tr("Home screen", "首页"),
        subtitle = tr("Order of the Group buttons above your notes", "笔记上方“分组”按钮的顺序"),
    ) {
        Column(Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.app.inset)) {
            order.forEachIndexed { i, g ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp))
                    Text(g.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { HomePrefs.move(context, g, -1) }, enabled = i > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, tr("Move up", "上移"))
                    }
                    IconButton(onClick = { HomePrefs.move(context, g, +1) }, enabled = i < order.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, tr("Move down", "下移"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (accent, container) = theme.swatch
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small).padding(4.dp)
                    else Modifier.padding(4.dp)
                )
                .clip(RoundedCornerShape(9.dp))
                .background(swatchBrush(theme, container))
                .border(1.dp, MaterialTheme.app.line, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The accent as a dot on its own container color.
            Box(Modifier.fillMaxSize(0.44f).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp),
                    tint = if (accent.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
        Text(theme.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Glint and anime-inspired themes preview their art colors; the rest their accent container. */
private fun swatchBrush(theme: AppTheme, container: Color): Brush = when (theme.backdrop) {
    Backdrop.SKY -> Brush.verticalGradient(listOf(Color(0xFF5AA9F5), Color(0xFFDCEEFF)))
    Backdrop.SUNSET -> Brush.verticalGradient(listOf(Color(0xFFFFB199), Color(0xFFFF7EB3), Color(0xFFC77DFF)))
    Backdrop.SEIGAIHA -> Brush.verticalGradient(listOf(Color(0xFFFFDAD3), Color(0xFFDBE1FA)))
    Backdrop.RIPPLE -> Brush.verticalGradient(listOf(Color(0xFF1E6470), Color(0xFFBFE3E5)))
    Backdrop.NONE -> SolidColor(container)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------------------
// AI writing
// ---------------------------------------------------------------------------

/** Shows which AI is set up and opens its page; the details live on their own screen. */
@Composable
private fun AiSection(onOpenAi: () -> Unit, onEditPrompt: () -> Unit) {
    val context = LocalContext.current
    // Re-read when coming back from the AI page.
    var config by remember { mutableStateOf(AiSettings.current(context)) }
    LifecycleResumeEffect(Unit) {
        config = AiSettings.current(context)
        onPauseOrDispose {}
    }
    val problem = config.problem()
    val modeCount = com.xiaohan.xhsnotegen.ai.ModeStore.modes.collectAsState().value.size

    SectionCard(
        title = tr("AI writing", "AI 写作"),
        subtitle = tr("Which model writes your notes. Keys stay on this phone.", "选择用哪个模型写笔记。密钥只保存在这台手机上。"),
    ) {
        SettingsRow(
            icon = Icons.Outlined.AutoAwesome,
            title = config.label,
            subtitle = problem ?: tr("Ready — tap to change the model or test it", "已就绪，点击可更换模型或测试"),
            subtitleColor = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onOpenAi,
        )
        // Entry to the prompt editor.
        SettingsRow(
            icon = Icons.Outlined.EditNote,
            title = tr("Writing modes & prompts", "写作模式与提示词"),
            subtitle = tr("$modeCount modes — Food, Travel, Outfit… each with its own prompt, language and photo limit", "$modeCount 个模式：美食、旅行、穿搭…… 各有自己的提示词、语言和照片上限"),
            onClick = onEditPrompt,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.app.inset)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

    SectionCard(title = tr("Xiaohongshu", "小红书")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    if (loggedIn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
            )
            Column(Modifier.weight(1f)) {
                Text(if (loggedIn) tr("Connected", "已连接") else tr("Not connected", "未连接"), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (loggedIn) tr("Notes post straight to your account", "笔记会直接发到你的账号") else tr("Log in to post directly from the app", "登录后可在应用内直接发布"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loggedIn) {
                TextButton(onClick = { confirmLogout = true }) { Text(tr("Log out", "退出登录")) }
            } else {
                FilledTonalButton(onClick = onLogin) { Text(tr("Log in", "登录")) }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(tr("Log out of Xiaohongshu?", "退出小红书登录？")) },
            text = { Text(tr("You can still post by hand — the app copies the text and saves the photos for you.", "你仍可手动发布：应用会帮你复制文字并保存图片。")) },
            confirmButton = {
                TextButton(onClick = { XhsAuthStore.clear(context); confirmLogout = false }) {
                    Text(tr("Log out", "退出登录"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
}
