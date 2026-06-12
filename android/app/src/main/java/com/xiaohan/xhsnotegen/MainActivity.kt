package com.xiaohan.xhsnotegen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.xiaohan.xhsnotegen.ui.navigation.AppNavigation
import com.xiaohan.xhsnotegen.ui.theme.XhsNoteGenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XhsNoteGenTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
