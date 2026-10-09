package com.xiaohan.xhsnotegen.domain

/**
 * Filtering the home feed by several tags at once.
 *
 * Rule: tags in the same group are OR, different groups are AND. A tag's group
 * is the root of its branch, so 日本 › 京都 and 日本 › 大阪 are one group and
 * 拉面 is another: picking 京都 + 大阪 + 拉面 shows "ramen in Kyoto or Osaka".
 * A picked tag also matches notes that carry any tag below it.
 *
 * Picking drills down like a breadcrumb: picking 京都 also picks 日本, and a
 * picked tag with picked tags below it steps aside for them. So {日本, 京都}
 * shows Kyoto, and un-picking 京都 lands back on all of 日本.
 */
object TagFilter {

    fun matches(noteTags: List<NoteTag>, selected: Set<Long>, all: List<NoteTag>): Boolean {
        val active = active(selected, all)
        if (active.isEmpty()) return true
        val byId = (all + noteTags).associateBy { it.id }
        // The note's tags plus everything above them: picking 日本 matches a note tagged 京都.
        val reach = noteTags.flatMap { TagTree.path(it, byId) }.mapTo(HashSet()) { it.id }
        return active.mapNotNull { byId[it] }
            .groupBy { TagTree.rootOf(it, byId).id }
            .values
            .all { group -> group.any { it.id in reach } }
    }

    /** The picked tags that actually filter: a tag with a picked tag below it steps aside. */
    fun active(selected: Set<Long>, all: List<NoteTag>): Set<Long> =
        selected.filterTo(HashSet()) { id -> TagTree.descendants(id, all).none { it in selected } }

    /**
     * The selection after tapping tag [id]:
     * - not picked → pick it along with the tags above it (京都 brings 日本);
     * - picked, with picks below it → drop those, back to all of this tag;
     * - picked, nothing below → drop it, so the tag above takes over again.
     */
    fun toggle(selected: Set<Long>, id: Long, all: List<NoteTag>): Set<Long> {
        val below = TagTree.descendants(id, all)
        if (id in selected) {
            return if (below.any { it in selected }) selected - below else selected - id
        }
        val byId = all.associateBy { it.id }
        val path = byId[id]?.let { TagTree.path(it, byId).map { t -> t.id } } ?: listOf(id)
        return selected + path
    }
}
