package com.xiaohan.xhsnotegen.ui.drafts

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How notes are laid out on the home screen. */
enum class HomeLayout(val columns: Int) {
    /** Two columns, the first note of each group full width (the original look). */
    GRID(2),
    /** Thumbnail + text rows; many notes per screen. */
    LIST(1),
    /** Covers only, three across. */
    GALLERY(3),
}

/** Home-screen choices you can change in Settings. Observable, so the home screen follows at once. */
object HomePrefs {
    private const val PREFS = "home"
    private const val KEY_ORDER = "group_order"
    private const val KEY_LAYOUT = "layout"

    /** The order of the Group buttons on the home screen. */
    val DEFAULT_ORDER = listOf(GroupBy.PLACE, GroupBy.RATING, GroupBy.MODE)

    private val _groupOrder = MutableStateFlow(DEFAULT_ORDER)
    val groupOrder: StateFlow<List<GroupBy>> = _groupOrder.asStateFlow()

    private val _layout = MutableStateFlow(HomeLayout.GRID)
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ORDER, null)
        _groupOrder.value = normalize(saved?.split(',').orEmpty().mapNotNull { n -> GroupBy.entries.firstOrNull { it.name == n } })
        _layout.value = HomeLayout.entries.firstOrNull { it.name == prefs.getString(KEY_LAYOUT, null) } ?: HomeLayout.GRID
    }

    fun setLayout(context: Context, layout: HomeLayout) {
        _layout.value = layout
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LAYOUT, layout.name) }
    }

    /** Keeps only real groups, once each; any the list is missing (e.g. added later) go at the end. */
    internal fun normalize(order: List<GroupBy>): List<GroupBy> =
        (order.filter { it != GroupBy.NONE }.distinct() + DEFAULT_ORDER).distinct()

    fun setGroupOrder(context: Context, order: List<GroupBy>) {
        val fixed = normalize(order)
        _groupOrder.value = fixed
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_ORDER, fixed.joinToString(",") { it.name }) }
    }

    /** Moves [group] up (-1) or down (+1) one place. */
    fun move(context: Context, group: GroupBy, delta: Int) {
        val list = _groupOrder.value.toMutableList()
        val from = list.indexOf(group)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.add(to, list.removeAt(from))
        setGroupOrder(context, list)
    }
}
