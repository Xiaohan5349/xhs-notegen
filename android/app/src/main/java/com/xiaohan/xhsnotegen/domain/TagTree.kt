package com.xiaohan.xhsnotegen.domain

/**
 * Tags form a tree of any depth (旅行 › 日本 › 京都), like places do
 * (Country › Region › City). Helpers here are cycle-safe: a broken parent
 * link can never loop forever.
 */
object TagTree {

    /** "生活/咖啡", "#生活 › 咖啡" → ["生活", "咖啡"]. */
    fun parsePath(raw: String): List<String> =
        raw.split('/', '／', '›', '>').map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }

    /** Root first, [tag] last. */
    fun path(tag: NoteTag, byId: Map<Long, NoteTag>): List<NoteTag> {
        val chain = mutableListOf(tag)
        var parent = tag.parentId?.let { byId[it] }
        while (parent != null) {
            val p: NoteTag = parent
            if (chain.any { it.id == p.id }) break
            chain.add(0, p)
            parent = p.parentId?.let { byId[it] }
        }
        return chain
    }

    fun pathLabel(tag: NoteTag, byId: Map<Long, NoteTag>): String = path(tag, byId).joinToString(" › ") { it.name }

    /** Top of [tag]'s branch. */
    fun rootOf(tag: NoteTag, byId: Map<Long, NoteTag>): NoteTag = path(tag, byId).first()

    fun children(id: Long, all: List<NoteTag>): List<NoteTag> =
        all.filter { it.parentId == id }.sortedBy { it.name.lowercase() }

    /** Every tag below [id], at any depth. */
    fun descendants(id: Long, all: List<NoteTag>): Set<Long> {
        val out = mutableSetOf<Long>()
        var frontier = listOf(id)
        while (frontier.isNotEmpty()) {
            frontier = all.filter { it.parentId in frontier && out.add(it.id) }.map { it.id }
        }
        return out - id
    }

    /** Tags without a (known) parent. */
    fun roots(all: List<NoteTag>): List<NoteTag> {
        val ids = all.map { it.id }.toSet()
        return all.filter { it.parentId == null || it.parentId !in ids }.sortedBy { it.name.lowercase() }
    }

    /** Whole tree in display order, each tag with its depth (0 = root). */
    fun ordered(all: List<NoteTag>): List<Pair<NoteTag, Int>> = buildList {
        val seen = mutableSetOf<Long>()
        fun visit(t: NoteTag, depth: Int) {
            if (!seen.add(t.id)) return
            add(t to depth)
            children(t.id, all).forEach { visit(it, depth + 1) }
        }
        roots(all).forEach { visit(it, 0) }
        // Anything left is part of a loop; show it at the top level rather than hide it.
        all.filter { it.id !in seen }.forEach { visit(it, 0) }
    }
}
