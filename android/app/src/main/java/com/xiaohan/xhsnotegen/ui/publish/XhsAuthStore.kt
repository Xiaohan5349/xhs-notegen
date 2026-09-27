package com.xiaohan.xhsnotegen.ui.publish

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores XHS web cookies captured from WebView login.
 * Cookies are stored in SharedPreferences — they're long-lived (weeks).
 */
object XhsAuthStore {

    private const val PREFS_NAME = "xhs_auth"
    private const val KEY_COOKIES = "cookies"

    /**
     * Cookies that only exist after a real login. XHS sets anonymous cookies
     * (a1, webId, ...) on first page load, so "has cookies" alone does not
     * mean "logged in".
     */
    private val SESSION_COOKIES = listOf(
        "web_session", "galaxy_creator_session_id", "customer-sso-sid",
        "access-token-creator.xiaohongshu.com",
    )

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        _loggedIn.value = getCookies(context) != null
    }

    fun hasSessionCookie(cookies: String?): Boolean {
        if (cookies.isNullOrBlank()) return false
        val names = cookies.split(";").map { it.trim().substringBefore("=") }
        return SESSION_COOKIES.any { it in names }
    }

    fun saveCookies(context: Context, cookies: String) {
        prefs(context).edit { putString(KEY_COOKIES, cookies) }
        _loggedIn.value = true
    }

    fun getCookies(context: Context): String? {
        val cookies = prefs(context).getString(KEY_COOKIES, null)
        return if (cookies.isNullOrBlank()) null else cookies
    }

    /** Log out: forget saved cookies and clear the WebView's copy too. */
    fun clear(context: Context) {
        prefs(context).edit { clear() }
        _loggedIn.value = false
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}
