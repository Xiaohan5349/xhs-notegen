package com.xiaohan.xhsnotegen.ui.publish

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var pageTitle by remember { mutableStateOf("Login to XHS") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://creator.xiaohongshu.com")
                        if (!cookies.isNullOrBlank()) {
                            XhsAuthStore.saveCookies(context, cookies)
                            Toast.makeText(context,
                                "XHS login successful!",
                                Toast.LENGTH_SHORT).show()
                            onLoginComplete()
                        } else {
                            pageTitle = "Please log in first"
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Done",
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        // Separate WebView just for login — cookies shared globally via CookieManager
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
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) {
                                pageTitle = when {
                                    url.contains("/login") -> "Login to XHS"
                                    url.contains("/publish") -> "Logged in ✓ — tap Done"
                                    else -> "Creator Studio"
                                }
                            }
                        }
                    }
                    loadUrl("https://creator.xiaohongshu.com")
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
