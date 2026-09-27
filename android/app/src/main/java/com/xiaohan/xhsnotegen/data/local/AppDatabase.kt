package com.xiaohan.xhsnotegen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiaohan.xhsnotegen.data.local.dao.*
import com.xiaohan.xhsnotegen.data.local.entity.*

@Database(
    entities = [
        NoteDraftEntity::class,
        FoodInfoEntity::class,
        StylePreferenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDraftDao(): NoteDraftDao
    abstract fun foodInfoDao(): FoodInfoDao
    abstract fun stylePreferenceDao(): StylePreferenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

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
                .build().also { INSTANCE = it }
            }
    }
}
