package com.xiaohan.xhsnotegen.ui.theme

import android.content.Context
import androidx.core.content.edit
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DarkMode(val id: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    val label: String
        get() = when (this) {
            SYSTEM -> tr("System", "跟随系统")
            LIGHT -> tr("Light", "浅色")
            DARK -> tr("Dark", "深色")
        }

    companion object {
        fun fromId(id: String?): DarkMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Theme + day/night choice, observable so the whole app recolors instantly. */
object AppearanceStore {
    private const val PREFS = "appearance"

    private val _theme = MutableStateFlow(AppTheme.TOMATO)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _darkMode = MutableStateFlow(DarkMode.SYSTEM)
    val darkMode: StateFlow<DarkMode> = _darkMode.asStateFlow()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val p = prefs(context)
        _theme.value = AppTheme.fromId(p.getString("theme", null))
        _darkMode.value = DarkMode.fromId(p.getString("dark_mode", null))
    }

    fun setTheme(context: Context, theme: AppTheme) {
        _theme.value = theme
        prefs(context).edit { putString("theme", theme.id) }
    }

    fun setDarkMode(context: Context, mode: DarkMode) {
        _darkMode.value = mode
        prefs(context).edit { putString("dark_mode", mode.id) }
    }
}
