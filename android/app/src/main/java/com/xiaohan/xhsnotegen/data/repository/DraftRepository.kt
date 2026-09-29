package com.xiaohan.xhsnotegen.data.repository

import androidx.room.withTransaction
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.entity.NoteDraftEntity
import com.xiaohan.xhsnotegen.data.local.entity.NoteTagEntity
import com.xiaohan.xhsnotegen.data.local.entity.TagEntity
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.data.local.toEntity
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.util.ImageCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DraftRepository(private val db: AppDatabase) {

    private val draftDao = db.noteDraftDao()
    private val foodDao = db.foodInfoDao()
    private val tagDao = db.tagDao()

    /** Maps a row to the domain model; a row with corrupted JSON is skipped, not fatal. */
    private suspend fun NoteDraftEntity.toDomainOrNull(): NoteDraft? = try {
        toDomain(foodDao.getByDraftId(id)?.toDomain())
            .copy(tags = tagDao.tagsFor(id).map { it.toDomain() })
    } catch (e: Exception) {
        null
    }

    /**
     * All notes with their food info and tags. Built from all four tables so a
     * change to any of them (e.g. tagging from the list) updates the list.
     */
    fun getAllFlow(): Flow<List<NoteDraft>> = combine(
        draftDao.getAllFlow(), foodDao.getAllFlow(), tagDao.allFlow(), tagDao.linksFlow(),
    ) { drafts, foods, tags, links ->
        val foodByDraft = foods.associateBy { it.draftId }
        val tagById = tags.associateBy { it.id }
        val tagsByDraft = links.groupBy({ it.draftId }, { tagById[it.tagId] })
        drafts.mapNotNull { entity ->
            try {
                entity.toDomain(foodByDraft[entity.id]?.toDomain()).copy(
                    tags = tagsByDraft[entity.id].orEmpty().filterNotNull().map { it.toDomain() }.sortedBy { it.name.lowercase() },
                )
            } catch (e: Exception) {
                null // a row with corrupted JSON must not crash the whole list
            }
        }
    }

    fun allTagsFlow(): Flow<List<NoteTag>> = tagDao.allFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): NoteDraft? = draftDao.getById(id)?.toDomainOrNull()

    suspend fun getAll(): List<NoteDraft> = draftDao.getAll().mapNotNull { it.toDomainOrNull() }

    suspend fun insert(draft: NoteDraft): Long = db.withTransaction {
        val draftId = draftDao.insert(draft.toEntity())
        foodDao.insert(draft.foodInfo.toEntity(draftId))
        draftId
    }

    suspend fun update(draft: NoteDraft) = db.withTransaction {
        draftDao.update(draft.copy(updatedAt = System.currentTimeMillis()).toEntity())
        // Keep the stored place: it may have been organized after this copy of the draft was loaded.
        val storedPlace = foodDao.getByDraftId(draft.id)?.toDomain()?.place
        val place = if (draft.foodInfo.place.isKnown || storedPlace == null) draft.foodInfo.place else storedPlace
        foodDao.deleteByDraftId(draft.id)
        foodDao.insert(draft.foodInfo.copy(place = place).toEntity(draft.id))
    }

    suspend fun deleteById(id: Long) {
        val draft = getById(id)
        draftDao.deleteById(id)
        // Draft deletion must also remove the local photo copies in filesDir/images/,
        // otherwise deleted drafts leave unmanaged (and private) image files behind.
        draft?.let { ImageCleanup.deleteLocalFiles(it.photoUris) }
    }

    suspend fun deleteByIds(ids: Collection<Long>) = ids.forEach { deleteById(it) }

    suspend fun setStatus(ids: Collection<Long>, status: NoteStatus) =
        draftDao.setStatus(ids.toList(), status.key)

    // ---- Places ----

    suspend fun setPlace(draftId: Long, place: Place) = foodDao.setPlace(
        draftId,
        place.country.ifBlank { null }, place.region.ifBlank { null }, place.city.ifBlank { null },
        place.latitude, place.longitude, place.source?.key,
        place.district.ifBlank { null }, place.address.ifBlank { null },
    )

    suspend fun setRating(ids: Collection<Long>, rating: Int) =
        draftDao.setRating(ids.toList(), rating.coerceIn(0, 5))

    // ---- Tags ----

    /**
     * Returns the tag with this name, creating it if needed. A new tag goes under
     * [parentId]; an existing tag keeps its place in the hierarchy.
     */
    suspend fun getOrCreateTag(name: String, parentId: Long? = null): NoteTag {
        val clean = name.trim().removePrefix("#").trim()
        require(clean.isNotEmpty()) { "Tag name is empty" }
        tagDao.byName(clean)?.let { return it.toDomain() }
        val id = tagDao.insertTag(TagEntity(name = clean, parentId = parentId?.takeIf { it > 0 }))
        return (tagDao.byName(clean) ?: TagEntity(id = id, name = clean)).toDomain()
    }

    /**
     * A mode's root tag. It may be a path ("生活/咖啡"): each level is created
     * under the one before if missing, and the last one is returned.
     */
    suspend fun rootTag(path: String): NoteTag? {
        var tag: NoteTag? = null
        TagTree.parsePath(path).forEach { name -> tag = getOrCreateTag(name, tag?.id) }
        return tag
    }

    /**
     * Moves a tag (with everything under it) under [parentId] (null = top level).
     * A tag can't sit under itself or anything below it.
     */
    suspend fun setTagParent(tagId: Long, parentId: Long?) {
        if (parentId == tagId) return
        val all = tagDao.all().map { it.toDomain() }
        if (parentId != null && parentId in TagTree.descendants(tagId, all)) return
        tagDao.setParent(tagId, parentId)
    }

    /** name → parent name, for backups. */
    suspend fun tagParentsByName(): Map<String, String> {
        val all = tagDao.all().associateBy { it.id }
        return all.values.mapNotNull { t -> t.parentId?.let { all[it]?.name }?.let { t.name to it } }.toMap()
    }

    /**
     * Gives every note its mode's root tag. Runs once after an update (notes
     * made before modes existed) and is harmless to repeat.
     */
    suspend fun backfillRootTags(rootTagOf: (String) -> String) {
        draftDao.idsAndTypes().groupBy { it.type }.forEach { (type, rows) ->
            val root = rootTag(rootTagOf(type)) ?: return@forEach
            addTag(rows.map { it.id }, root.id)
        }
    }

    suspend fun addTag(draftIds: Collection<Long>, tagId: Long) =
        tagDao.link(draftIds.map { NoteTagEntity(draftId = it, tagId = tagId) })

    suspend fun removeTag(draftIds: Collection<Long>, tagId: Long) = tagDao.unlink(tagId, draftIds.toList())

    suspend fun renameTag(tagId: Long, name: String) = tagDao.rename(tagId, name.trim().removePrefix("#").trim())

    /** Deletes a tag; the tags under it move up one level (notes keep their other tags). */
    suspend fun deleteTag(tagId: Long) = db.withTransaction {
        val all = tagDao.all()
        val parent = all.firstOrNull { it.id == tagId }?.parentId
        all.filter { it.parentId == tagId }.forEach { tagDao.setParent(it.id, parent) }
        tagDao.deleteTag(tagId)
    }

    /**
     * Import: all-or-nothing, and every draft gets a fresh auto-generated id;
     * tags are matched by name, and [tagParents] (name → root name) rebuilds the hierarchy.
     */
    suspend fun insertAll(drafts: List<NoteDraft>, tagParents: Map<String, String> = emptyMap()) = db.withTransaction {
        // Upper levels first, so a parent is placed before its own children are created.
        fun depth(name: String): Int {
            var d = 0
            var cur: String? = tagParents[name]
            while (cur != null && d < tagParents.size) { d++; cur = tagParents[cur] }
            return d
        }
        tagParents.entries.sortedBy { depth(it.key) }.forEach { (child, parent) ->
            val p = getOrCreateTag(parent)
            getOrCreateTag(child, p.id)
        }
        drafts.forEach { draft ->
            val newDraft = draft.copy(id = 0)
            val draftId = draftDao.insert(newDraft.toEntity())
            foodDao.insert(newDraft.foodInfo.toEntity(draftId))
            newDraft.tags.map { it.name }.filter { it.isNotBlank() }.distinct().forEach { name ->
                addTag(listOf(draftId), getOrCreateTag(name).id)
            }
        }
    }

    /**
     * Notes the user has already reviewed or posted, newest first. Their
     * (user-edited) text is the best example of the user's own voice, so it is
     * fed back to the model as a style reference.
     */
    suspend fun getVoiceSamples(excludeId: Long, mode: String, limit: Int = 3): List<String> =
        draftDao.getAll()
            .asSequence()
            .filter { it.id != excludeId && it.type == mode } // voice of the same kind of note
            .filter { it.status == NoteStatus.SHARED.key || it.status == NoteStatus.REVIEWED.key }
            .mapNotNull { entity ->
                runCatching { entity.toDomain() }.getOrNull()?.selectedVariant
                    ?.let { v -> listOf(v.title, v.body).filter { it.isNotBlank() }.joinToString("\n") }
                    ?.takeIf { it.length >= 20 }
            }
            .take(limit)
            .toList()
}
