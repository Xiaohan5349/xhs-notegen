package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.NoteTagEntity
import com.xiaohan.xhsnotegen.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun allFlow(): Flow<List<TagEntity>>

    @Query("SELECT * FROM note_tags")
    fun linksFlow(): Flow<List<NoteTagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId AND draft_id IN (:draftIds)")
    suspend fun unlink(tagId: Long, draftIds: List<Long>)

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tag_id = t.id WHERE nt.draft_id = :draftId ORDER BY t.name COLLATE NOCASE")
    suspend fun tagsFor(draftId: Long): List<TagEntity>
}
