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
    version = 4,
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

        /**
         * v3 → v4: tags get an optional parent (root tag). SQLite can't add a
         * foreign key with ALTER TABLE, so the tags table is rebuilt; ids are kept,
         * so note_tags links stay valid.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`parent_id` INTEGER, FOREIGN KEY(`parent_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("INSERT INTO `tags_new` (`id`, `name`) SELECT `id`, `name` FROM `tags`")
                db.execSQL("DROP TABLE `tags`")
                db.execSQL("ALTER TABLE `tags_new` RENAME TO `tags`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_parent_id` ON `tags` (`parent_id`)")
            }
        }

        /** v2 → v3: star rating on notes; district + full street address on places. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `note_drafts` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `food_info` ADD COLUMN `district` TEXT")
                db.execSQL("ALTER TABLE `food_info` ADD COLUMN `address` TEXT")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
    }
}
