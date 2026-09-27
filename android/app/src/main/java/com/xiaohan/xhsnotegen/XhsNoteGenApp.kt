package com.xiaohan.xhsnotegen

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.repository.DraftRepository
import com.xiaohan.xhsnotegen.data.repository.StylePreferencesRepository
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.AppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class XhsNoteGenApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var draftRepository: DraftRepository
        private set

    lateinit var stylePrefsRepository: StylePreferencesRepository
        private set

    /**
     * Outlives any screen. Used for writes that must finish even when the
     * ViewModel that started them is cleared (e.g. the last debounced edit).
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        draftRepository = DraftRepository(database)
        stylePrefsRepository = StylePreferencesRepository(database)
        XhsAuthStore.init(this)
        AppearanceStore.init(this)
        // Debug builds only: lets chrome://inspect on a computer debug the XHS login page.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
