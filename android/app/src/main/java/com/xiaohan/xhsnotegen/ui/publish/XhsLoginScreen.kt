package com.xiaohan.xhsnotegen.ui.publish

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XhsLoginScreen(
    onLoginComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var looksLoggedIn by remember { mutableStateOf(false) }
    var confirmUnverified by remember { mutableStateOf<String?>(null) }

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
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        else "Log in on the page below (QR code or phone), then tap Done.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Separate WebView just for login — cookies are shared globally via CookieManager.
            AndroidView(
                factory = { ctx ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = (
                            "Mozilla/5.0 (Linux; Android 14; SM-S918U1) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
                            )
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                looksLoggedIn = XhsAuthStore.hasSessionCookie(currentCookies())
                            }
                        }
                        loadUrl("https://creator.xiaohongshu.com")
                    }
                },
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
