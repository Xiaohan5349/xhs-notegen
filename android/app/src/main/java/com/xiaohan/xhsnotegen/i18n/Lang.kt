package com.xiaohan.xhsnotegen.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.util.Locale

/** The app's own language, separate from the language notes are written in. */
enum class AppLanguage(val id: String) {
    SYSTEM("system"), ENGLISH("en"), CHINESE("zh");

    /** Shown in the picker in its own language, so it can always be found. */
    val label: String
        get() = when (this) {
            SYSTEM -> tr("System", "跟随系统")
            ENGLISH -> "English"
            CHINESE -> "中文"
        }

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * App language. [isZh] is Compose state, so every screen that shows text via
 * [tr] redraws right away when the language changes — no restart needed.
 */
object LanguageStore {
    private const val PREFS = "appearance"
    private const val KEY = "language"

    private val listeners = mutableListOf<() -> Unit>()

    var choice by mutableStateOf(AppLanguage.SYSTEM)
        private set

    var isZh by mutableStateOf(systemIsZh())
        private set

    fun init(context: Context) {
        choice = AppLanguage.fromId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        isZh = resolve(choice)
    }

    fun set(context: Context, language: AppLanguage) {
        choice = language
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, language.id) }
        val zh = resolve(language)
        if (zh != isZh) {
            isZh = zh
            listeners.forEach { it() }
        }
    }

    /** Called when the resolved language changes (e.g. to relabel built-in modes). */
    fun onChange(listener: () -> Unit) { listeners += listener }

    private fun resolve(l: AppLanguage) = when (l) {
        AppLanguage.SYSTEM -> systemIsZh()
        AppLanguage.ENGLISH -> false
        AppLanguage.CHINESE -> true
    }

    private fun systemIsZh() = Locale.getDefault().language == "zh"
}

/** Picks the text for the current app language. Read in a composable, it recomposes on change. */
fun tr(en: String, zh: String): String = if (LanguageStore.isZh) zh else en

/** "1 note" / "3 notes" / "3 篇笔记". */
fun notesCount(n: Int): String = tr(if (n == 1) "1 note" else "$n notes", "$n 篇笔记")
