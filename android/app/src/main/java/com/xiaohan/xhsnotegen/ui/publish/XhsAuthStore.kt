package com.xiaohan.xhsnotegen.ui.publish

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Stores XHS web cookies captured from WebView login.
 * Cookies are stored in SharedPreferences — they're long-lived (weeks).
 */
object XhsAuthStore {

    private const val PREFS_NAME = "xhs_auth"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_USER_ID = "user_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCookies(context: Context, cookies: String) {
        prefs(context).edit {
            putString(KEY_COOKIES, cookies)
            putBoolean(KEY_LOGGED_IN, true)
        }
    }

    fun getCookies(context: Context): String? {
        val cookies = prefs(context).getString(KEY_COOKIES, null)
        return if (cookies.isNullOrBlank()) null else cookies
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
