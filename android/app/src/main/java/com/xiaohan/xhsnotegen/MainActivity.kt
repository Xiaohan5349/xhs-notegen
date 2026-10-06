package com.xiaohan.xhsnotegen

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.xiaohan.xhsnotegen.ui.navigation.AppNavigation
import com.xiaohan.xhsnotegen.ui.theme.AppearanceStore
import com.xiaohan.xhsnotegen.ui.theme.DarkMode
import com.xiaohan.xhsnotegen.ui.theme.XhsNoteGenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by AppearanceStore.theme.collectAsState()
            val mode by AppearanceStore.darkMode.collectAsState()
            val dark = when (mode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }

            // Status/nav bar icons follow the app's choice, not just the system setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }

            XhsNoteGenTheme(theme = theme, darkTheme = dark) {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
