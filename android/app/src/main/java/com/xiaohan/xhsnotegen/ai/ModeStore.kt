package com.xiaohan.xhsnotegen.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.ModeDto
import com.xiaohan.xhsnotegen.domain.BuiltInModes
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.WritingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Writing modes. Built-ins (Food, Travel) are stored only as your edits on top
 * of the app's defaults, so "Reset" restores them and untouched ones pick up
 * improved defaults in future versions. Custom modes are stored in full.
 */
object ModeStore {
    private const val PREFS = "writing_modes"
    private const val KEY_OVERRIDES = "overrides"   // key -> ModeDto, built-ins only
    private const val KEY_CUSTOM = "custom"         // list of ModeDto
    private const val KEY_LAST = "last_used"

    private val mapType = object : TypeToken<Map<String, ModeDto?>>() {}.type
    private val listType = object : TypeToken<List<ModeDto?>>() {}.type

    private lateinit var prefs: SharedPreferences
    private val _modes = MutableStateFlow(BuiltInModes.all)
    val modes: StateFlow<List<WritingMode>> = _modes.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateOldFoodPrompt(context)
        reload()
    }

    fun get(key: String?): WritingMode = _modes.value.firstOrNull { it.key == key } ?: BuiltInModes.food

    fun lastUsed(): WritingMode = get(prefs.getString(KEY_LAST, null))
    fun setLastUsed(key: String) = prefs.edit { putString(KEY_LAST, key) }

    fun isCustomized(key: String): Boolean = overrides().containsKey(key)

    /** Saves a mode (built-in → as an override, custom → in full). */
    fun save(mode: WritingMode) {
        if (BuiltInModes.builtIn(mode.key) != null) {
            val o = overrides().toMutableMap()
            o[mode.key] = ModeDto.from(mode)
            prefs.edit { putString(KEY_OVERRIDES, JsonCodec.toJson(o)) }
        } else {
            val list = customs().filter { it.key != mode.key } + ModeDto.from(mode)
            prefs.edit { putString(KEY_CUSTOM, JsonCodec.toJson(list)) }
        }
        reload()
    }

    fun resetBuiltIn(key: String) {
        prefs.edit { putString(KEY_OVERRIDES, JsonCodec.toJson(overrides() - key)) }
        reload()
    }

    fun delete(key: String) {
        if (BuiltInModes.builtIn(key) != null) return
        prefs.edit { putString(KEY_CUSTOM, JsonCodec.toJson(customs().filter { it.key != key })) }
        if (prefs.getString(KEY_LAST, null) == key) prefs.edit { remove(KEY_LAST) }
        reload()
    }

    fun create(name: String): WritingMode {
        val mode = BuiltInModes.blank("custom_${UUID.randomUUID().toString().take(8)}", name.trim())
        save(mode)
        return get(mode.key)
    }

    private fun reload() {
        val o = overrides()
        val builtIns = BuiltInModes.all.map { b -> o[b.key]?.toDomain(b) ?: b }
        val custom = customs().mapNotNull { d ->
            val key = d.key ?: return@mapNotNull null
            d.toDomain(BuiltInModes.blank(key, d.name ?: "Custom"))
        }
        _modes.value = builtIns + custom
    }

    private fun overrides(): Map<String, ModeDto> = runCatching {
        JsonCodec.gson.fromJson<Map<String, ModeDto?>>(prefs.getString(KEY_OVERRIDES, null) ?: "{}", mapType)
    }.getOrNull().orEmpty().filterValues { it != null }.mapValues { it.value!! }

    private fun customs(): List<ModeDto> = runCatching {
        JsonCodec.gson.fromJson<List<ModeDto?>>(prefs.getString(KEY_CUSTOM, null) ?: "[]", listType)
    }.getOrNull().orEmpty().filterNotNull()

    /** v1.3 kept one editable (food) prompt in "prompts"; carry it into the Food mode once. */
    private fun migrateOldFoodPrompt(context: Context) {
        val old = context.getSharedPreferences("prompts", Context.MODE_PRIVATE)
        if (old.all.isEmpty() || overrides().containsKey(BuiltInModes.FOOD)) return
        val base = BuiltInModes.food
        val migrated = base.copy(
            instructions = old.getString("system", null)?.takeIf { it.isNotBlank() } ?: base.instructions,
            styles = NoteStyle.entries.associate { s -> s.key to (old.getString("style_${s.key}", null)?.takeIf { it.isNotBlank() } ?: base.style(s)) },
        )
        val o = overrides() + (BuiltInModes.FOOD to ModeDto.from(migrated))
        prefs.edit { putString(KEY_OVERRIDES, JsonCodec.toJson(o)) }
        old.edit { clear() }
    }
}
