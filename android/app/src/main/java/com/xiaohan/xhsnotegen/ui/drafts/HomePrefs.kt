package com.xiaohan.xhsnotegen.ui.drafts

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Home-screen choices you can change in Settings. Observable, so the home screen follows at once. */
object HomePrefs {
    private const val PREFS = "home"
    private const val KEY_ORDER = "group_order"

    /** The order of the Group buttons on the home screen. */
    val DEFAULT_ORDER = listOf(GroupBy.PLACE, GroupBy.TAG, GroupBy.RATING, GroupBy.MODE)

    private val _groupOrder = MutableStateFlow(DEFAULT_ORDER)
    val groupOrder: StateFlow<List<GroupBy>> = _groupOrder.asStateFlow()

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ORDER, null)
        _groupOrder.value = normalize(saved?.split(',').orEmpty().mapNotNull { n -> GroupBy.entries.firstOrNull { it.name == n } })
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
