package com.xiaohan.xhsnotegen.ui.publish

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XhsLoginScreen(
    onLoginComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var looksLoggedIn by remember { mutableStateOf(false) }
    var confirmUnverified by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun currentCookies(): String? = CookieManager.getInstance().getCookie("https://creator.xiaohongshu.com")

    fun finish(cookies: String) {
        CookieManager.getInstance().flush()
        XhsAuthStore.saveCookies(context, cookies)
        onLoginComplete()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Log in to Xiaohongshu") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        // XHS may ask to scan a QR code with the XHS app — impossible on the
                        // same phone. Saving the screen lets the app scan it from the album.
                        IconButton(onClick = {
                            val view = webView ?: return@IconButton
                            scope.launch {
                                val saved = saveScreenshot(context, view.drawToBitmap())
                                Toast.makeText(
                                    context,
                                    if (saved) "已保存到相册。打开小红书 → 扫一扫 → 相册，选这张图"
                                    else "Couldn't save the screenshot",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = "Save QR code to gallery")
                        }
                        Button(
                            onClick = {
                                val cookies = currentCookies()
                                when {
                                    cookies.isNullOrBlank() -> confirmUnverified = ""
                                    XhsAuthStore.hasSessionCookie(cookies) -> finish(cookies)
                                    // Anonymous cookies exist before login too — don't claim success on those.
                                    else -> confirmUnverified = cookies
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = if (looksLoggedIn) ButtonDefaults.buttonColors()
                                     else ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("Done") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = if (looksLoggedIn) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
                    Text(
                        if (looksLoggedIn) "You're logged in — tap Done."
                        // Verified on a real phone: SMS login from an in-app browser is answered
                        // with XHS risk control (HTTP 471 → a "scan to verify" QR that keeps
                        // waiting). QR login goes straight through.
                        else "Tip: QR login works best. Tap the QR corner of the login card, tap the QR icon " +
                            "above to save it, then in the XHS app: 扫一扫 → 相册 → pick it. Tap Done once logged in. " +
                            "SMS login may get stuck on an extra verification step.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            AndroidView(
                factory = { ctx ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // Login steps may open a window (captcha, verification).
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        // The phone's REAL browser identity, minus the "; wv" marker that
                        // announces an embedded WebView. The old hard-coded "Chrome/149"
                        // contradicted the engine's actual version, which XHS's security
                        // scripts can detect — a likely cause of extra QR verification.
                        settings.userAgentString = browserLikeUserAgent(ctx)

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                looksLoggedIn = XhsAuthStore.hasSessionCookie(currentCookies())
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            // Having a WebChromeClient also enables JS alert/confirm dialogs,
                            // which a WebView without one silently swallows.
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress == 100) looksLoggedIn = XhsAuthStore.hasSessionCookie(currentCookies())
                            }

                            override fun onCreateWindow(
                                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?,
                            ): Boolean {
                                // Open popups in this same view instead of dropping them.
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val popup = WebView(view.context)
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                        view.loadUrl(request.url.toString())
                                        popup.destroy()
                                        return true
                                    }
                                }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl("https://creator.xiaohongshu.com")
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    confirmUnverified?.let { cookies ->
        AlertDialog(
            onDismissRequest = { confirmUnverified = null },
            title = { Text("Not logged in yet?") },
            text = {
                Text(
                    if (cookies.isBlank()) "No login was found. Finish logging in on the page first."
                    else "This doesn't look like a finished login. If you did log in, save it anyway."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmUnverified = null }) { Text("Keep trying") }
            },
            dismissButton = if (cookies.isNotBlank()) {
                { TextButton(onClick = { confirmUnverified = null; finish(cookies) }) { Text("Save anyway") } }
            } else null,
        )
    }
}

/**
 * The WebView's default user agent with the "; wv" token and "Version/4.0"
 * removed — i.e. what regular Chrome on this phone sends, with a version that
 * matches the engine.
 */
internal fun browserLikeUserAgent(context: Context): String =
    cleanWebViewUserAgent(WebSettings.getDefaultUserAgent(context))

internal fun cleanWebViewUserAgent(ua: String): String =
    ua.replace("; wv)", ")").replace(Regex("""\s*Version/\d+(\.\d+)*"""), "")

/** Saves [bitmap] to Pictures/XHSNoteGen-login so the XHS app can scan it from the album. */
private suspend fun saveScreenshot(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "xhs_login_qr_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XHSNoteGen-login")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    } catch (e: Exception) {
        false
    }
}
