package com.xiaohan.xhsnotegen.ai

import android.content.Context
import androidx.core.content.edit
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts

/**
 * User edits to the writing prompt. Nothing is stored while a text equals its
 * default, so improved defaults in future app versions still reach users who
 * never customized that part.
 */
object PromptStore {
    private const val PREFS = "prompts"
    private const val KEY_SYSTEM = "system"
    private fun styleKey(style: NoteStyle) = "style_${style.key}"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun instructions(c: Context): String =
        prefs(c).getString(KEY_SYSTEM, null)?.takeIf { it.isNotBlank() } ?: FoodPrompts.DEFAULT_SYSTEM_PROMPT

    fun setInstructions(c: Context, text: String) = save(c, KEY_SYSTEM, text, FoodPrompts.DEFAULT_SYSTEM_PROMPT)

    fun styleInstruction(c: Context, style: NoteStyle): String =
        prefs(c).getString(styleKey(style), null)?.takeIf { it.isNotBlank() }
            ?: FoodPrompts.defaultStyleInstruction(style)

    fun setStyleInstruction(c: Context, style: NoteStyle, text: String) =
        save(c, styleKey(style), text, FoodPrompts.defaultStyleInstruction(style))

    fun isCustomized(c: Context): Boolean = prefs(c).all.isNotEmpty()

    fun resetAll(c: Context) = prefs(c).edit { clear() }

    private fun save(c: Context, key: String, text: String, default: String) = prefs(c).edit {
        if (text.isBlank() || text.trim() == default.trim()) remove(key) else putString(key, text)
    }
}
