package com.xiaohan.xhsnotegen.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.ai.AiConfig
import com.xiaohan.xhsnotegen.ai.AiException
import com.xiaohan.xhsnotegen.ai.AiProvider
import com.xiaohan.xhsnotegen.ai.AiSettings
import com.xiaohan.xhsnotegen.ai.AiTestResult
import com.xiaohan.xhsnotegen.ai.AiTester
import com.xiaohan.xhsnotegen.ai.AiWriter
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import kotlinx.coroutines.launch

/** One provider's settings as being edited (not saved until you press Save). */
private data class AiDraft(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val vision: Boolean,
) {
    fun toConfig() = AiConfig(
        provider = provider,
        model = model.trim(),
        apiKey = apiKey.trim(),
        baseUrl = if (provider == AiProvider.CUSTOM) baseUrl.trim().trimEnd('/') else provider.defaultBaseUrl,
        vision = if (provider == AiProvider.CUSTOM) vision else provider.findModel(model)?.vision ?: true,
    )
}

private fun loadDraft(context: android.content.Context, p: AiProvider) = AiDraft(
    provider = p,
    apiKey = AiSettings.apiKey(context, p),
    model = AiSettings.model(context, p),
    baseUrl = AiSettings.customBaseUrl(context),
    vision = AiSettings.customVision(context),
)

/**
 * Choose the AI, add its key, and check that it answers. Changes stay on this page until
 * you press Save (which also runs the test); Test checks what is on screen without saving.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var provider by remember { mutableStateOf(AiSettings.provider(context)) }
    val drafts = remember { mutableStateMapOf<AiProvider, AiDraft>().apply { AiProvider.entries.forEach { put(it, loadDraft(context, it)) } } }
    var saved by remember { mutableStateOf(AiProvider.entries.associateWith { loadDraft(context, it) } to AiSettings.provider(context)) }
    val draft = drafts.getValue(provider)
    fun edit(f: (AiDraft) -> AiDraft) { drafts[provider] = f(drafts.getValue(provider)) }

    val dirty = provider != saved.second || AiProvider.entries.any { p ->
        // Only the custom provider has an address and a photo switch.
        val a = drafts.getValue(p); val b = saved.first.getValue(p)
        a.apiKey.trim() != b.apiKey.trim() || a.model.trim() != b.model.trim() ||
            (p == AiProvider.CUSTOM && (a.baseUrl.trim().trimEnd('/') != b.baseUrl.trim().trimEnd('/') || a.vision != b.vision))
    }

    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiTestResult?>(null) }
    var resultFor by remember { mutableStateOf("") }
    var showDetail by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    var loadingModels by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>?>(null) }
    var modelsError by remember { mutableStateOf<AiException?>(null) }

    fun runTest(config: AiConfig) {
        if (testing) return
        testing = true; result = null; showDetail = false
        scope.launch {
            result = try {
                AiTester.test(config)
            } catch (e: Exception) {
                AiTestResult(false, e.message ?: tr("Something went wrong", "出错了"), detail = e.toString())
            }
            resultFor = config.label
            testing = false
        }
    }

    fun save(): Boolean {
        // Refuse to save something that can't work at all, and say why.
        drafts.getValue(provider).toConfig().problem()?.let { scope.launch { snackbar.showSnackbar(it) }; return false }
        AiProvider.entries.forEach { p ->
            val d = drafts.getValue(p)
            AiSettings.setApiKey(context, p, d.apiKey)
            AiSettings.setModel(context, p, d.model.ifBlank { p.defaultModel })
            if (p == AiProvider.CUSTOM) {
                AiSettings.setCustomBaseUrl(context, d.baseUrl)
                AiSettings.setCustomVision(context, d.vision)
            }
        }
        AiSettings.setProvider(context, provider)
        saved = AiProvider.entries.associateWith { loadDraft(context, it) } to provider
        return true
    }

    fun leave() { if (dirty) confirmLeave = true else onNavigateBack() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("AI writing", "AI 写作")) },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "返回")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
            SectionCard(
                title = tr("Model", "模型"),
                subtitle = tr("Which AI writes your notes. Keys stay on this phone.", "选择用哪个 AI 写笔记。密钥只保存在这台手机上。"),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.entries.forEach { p ->
                        FilterChip(selected = p == provider, onClick = { provider = p; result = null }, label = { Text(p.displayName) }, shape = CircleShape)
                    }
                }

                if (provider.models.isNotEmpty()) {
                    Column(Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)) {
                        provider.models.forEach { m ->
                            val selected = m.id == draft.model
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { edit { it.copy(model = m.id) } })
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
                                            Text(tr("Recommended", "推荐"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (provider == AiProvider.CUSTOM) {
                    TextField(
                        value = draft.baseUrl,
                        onValueChange = { v -> edit { it.copy(baseUrl = v) }; modelList = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("API address", "API 地址")) },
                        placeholder = { Text("https://example.com/v1") },
                        leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = MaterialTheme.shapes.medium,
                        colors = softFieldColors(),
                        supportingText = { Text(tr("OpenAI-compatible base URL; /chat/completions is added for you", "兼容 OpenAI 的基础地址，会自动加上 /chat/completions")) },
                    )
                }

                // Built-in providers: a free-text id for models newer than this app; custom: the model itself.
                val isCustom = provider == AiProvider.CUSTOM
                val listed = provider.findModel(draft.model) != null
                TextField(
                    value = if (isCustom || !listed) draft.model else "",
                    onValueChange = { v -> edit { it.copy(model = if (isCustom) v else v.ifBlank { provider.defaultModel }) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isCustom) tr("Model", "模型") else tr("Other model (optional)", "其他模型（选填）")) },
                    placeholder = { Text(if (isCustom) tr("e.g. step-5-preview", "例如 step-5-preview") else tr("Any ${provider.displayName} model id", "任意 ${provider.displayName} 模型 ID")) },
                    leadingIcon = { Icon(Icons.Outlined.Memory, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                    supportingText = {
                        Text(
                            if (isCustom) tr("Type the model id, or load the list this address offers", "填写模型 ID，或读取这个地址提供的模型列表")
                            else tr("Overrides the list above — for models released after this app version", "会覆盖上面的选择，用于本版本之后发布的新模型")
                        )
                    },
                )

                if (isCustom) {
                    OutlinedButton(
                        onClick = {
                            loadingModels = true; modelsError = null
                            scope.launch {
                                try { modelList = AiWriter.listModels(draft.toConfig()) } catch (e: AiException) { modelsError = e } catch (e: Exception) {
                                    modelsError = AiException(e.message ?: e.toString(), detail = e.toString())
                                }
                                loadingModels = false
                            }
                        },
                        enabled = !loadingModels && draft.baseUrl.isNotBlank(),
                    ) {
                        if (loadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Load models from this address", "从这个地址读取模型列表"))
                    }
                    modelsError?.let { e ->
                        Text(e.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        e.detail?.let { d ->
                            SelectionContainer {
                                Text(d, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                TextField(
                    value = draft.apiKey,
                    onValueChange = { v -> edit { it.copy(apiKey = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("${provider.displayName} API key", "${provider.displayName} API 密钥")) },
                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) tr("Hide key", "隐藏密钥") else tr("Show key", "显示密钥"))
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                    supportingText = { Text(provider.keyHint) },
                )

                if (isCustom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Model can see photos", "模型能看图片"), style = MaterialTheme.typography.titleSmall)
                            Text(tr("Turn off for text-only models — only your notes are sent", "纯文本模型请关闭，只会发送你写的文字"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = draft.vision, onCheckedChange = { v -> edit { it.copy(vision = v) } })
                    }
                } else if (provider.findModel(draft.model)?.vision == false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.HideImage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(tr("This model can't see images, so notes are written from your text only.", "这个模型看不了图片，笔记只根据你写的文字生成。"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionCard(
                title = tr("Check it works", "检查是否可用"),
                subtitle = tr("Sends one tiny request to the model. Save also runs this test.", "向模型发送一个很小的请求。点“保存”也会自动测试。"),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { runTest(draft.toConfig()) }, enabled = !testing, modifier = Modifier.weight(1f)) {
                        if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (testing) tr("Testing…", "测试中…") else tr("Test connection", "测试连接"))
                    }
                    Button(
                        onClick = {
                            if (save()) {
                                scope.launch { snackbar.showSnackbar(tr("Saved", "已保存")) }
                                runTest(drafts.getValue(provider).toConfig())
                            }
                        },
                        enabled = dirty && !testing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Save", "保存"))
                    }
                }

                result?.let { r ->
                    val color = if (r.ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = color)
                            Text(r.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        r.reply?.let {
                            Text(tr("The model said: ${it.take(120)}", "模型回复：${it.take(120)}"), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.ok && r.photosOk == true) {
                            Text(tr("Photos work too.", "图片也能正常识别。"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.photosOk == false && provider == AiProvider.CUSTOM && draft.vision) {
                            FilledTonalButton(onClick = { edit { it.copy(vision = false) } }) { Text(tr("Turn off photos", "关闭图片")) }
                        }
                        if (r.detail.isNotBlank()) {
                            TextButton(onClick = { showDetail = !showDetail }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (showDetail) tr("Hide details", "收起详情") else tr("Show details", "查看详情"))
                            }
                            if (showDetail) {
                                SelectionContainer {
                                    Text(r.detail, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    modelList?.let { all ->
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { modelList = null },
            title = { Text(tr("${all.size} models", "${all.size} 个模型")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (all.isEmpty()) {
                        Text(tr("The address answered but listed no models. Type the model id by hand.", "地址有回应，但没有列出模型。请手动填写模型 ID。"))
                    } else {
                        TextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(tr("Search", "搜索")) }, shape = MaterialTheme.shapes.medium, colors = softFieldColors())
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(all.filter { it.contains(query, ignoreCase = true) }, key = { it }) { id ->
                                Text(
                                    id,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(selected = id == draft.model, role = Role.Button, onClick = { edit { it.copy(model = id) }; modelList = null })
                                        .padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (id == draft.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(tr("A list can include models that can't write text (image, voice). Pick a chat model.", "列表里可能有不能写文字的模型（图像、语音），请选对话模型。"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modelList = null }) { Text(tr("Close", "关闭")) } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(tr("Save your changes?", "保存修改？")) },
            text = { Text(tr("You changed the AI settings but haven't saved them.", "你修改了 AI 设置，但还没有保存。")) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; if (save()) onNavigateBack() }) { Text(tr("Save", "保存")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text(tr("Keep editing", "继续编辑")) }
                    TextButton(onClick = { confirmLeave = false; onNavigateBack() }) { Text(tr("Discard", "放弃")) }
                }
            },
        )
    }
}
