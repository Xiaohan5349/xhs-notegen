package com.xiaohan.xhsnotegen

import android.app.Application
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.repository.DraftRepository
import com.xiaohan.xhsnotegen.data.repository.StylePreferencesRepository

class XhsNoteGenApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var draftRepository: DraftRepository
        private set

    lateinit var stylePrefsRepository: StylePreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        draftRepository = DraftRepository(database)
        stylePrefsRepository = StylePreferencesRepository(database)
    }
}
