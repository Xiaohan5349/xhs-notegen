package com.xiaohan.xhsnotegen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaohan.xhsnotegen.data.local.dao.*
import com.xiaohan.xhsnotegen.data.local.entity.*

@Database(
    entities = [
        NoteDraftEntity::class,
        FoodInfoEntity::class,
        StylePreferenceEntity::class,
        TagEntity::class,
        NoteTagEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDraftDao(): NoteDraftDao
    abstract fun foodInfoDao(): FoodInfoDao
    abstract fun stylePreferenceDao(): StylePreferenceDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2: place columns on food_info, plus tags and note_tags.
         * SQL copied from the exported schemas/…/2.json so Room's validation matches.
         * Existing notes keep all their data; places start empty until organized.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("country TEXT", "region TEXT", "city TEXT", "latitude REAL", "longitude REAL", "place_source TEXT")
                    .forEach { db.execSQL("ALTER TABLE `food_info` ADD COLUMN `${it.substringBefore(' ')}` ${it.substringAfter(' ')}") }
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_tags` (`draft_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`draft_id`, `tag_id`), " +
                        "FOREIGN KEY(`draft_id`) REFERENCES `note_drafts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tag_id` ON `note_tags` (`tag_id`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xhs_notegen.db"
                )
                // No destructive fallback: a schema change without a Migration
                // must fail loudly in testing instead of silently wiping every
                // user's drafts in production.
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
    }
}
