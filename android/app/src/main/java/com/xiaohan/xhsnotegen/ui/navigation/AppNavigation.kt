package com.xiaohan.xhsnotegen.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaohan.xhsnotegen.ui.create.CreateFormScreen
import com.xiaohan.xhsnotegen.ui.drafts.DraftListScreen
import com.xiaohan.xhsnotegen.ui.generate.GeneratingScreen
import com.xiaohan.xhsnotegen.ui.publish.XhsLoginScreen
import com.xiaohan.xhsnotegen.ui.review.ReviewScreen
import com.xiaohan.xhsnotegen.ui.settings.PromptEditorScreen
import com.xiaohan.xhsnotegen.ui.settings.SettingsScreen

object Routes {
    const val DRAFT_LIST = "drafts"
    const val CREATE_FORM = "create"
    const val GENERATING = "generating/{draftId}"
    const val REVIEW = "review/{draftId}"
    const val XHS_LOGIN = "xhs_login"
    const val SETTINGS = "settings"
    const val PROMPT_EDITOR = "prompt_editor"

    fun generating(draftId: Long) = "generating/$draftId"
    fun review(draftId: Long) = "review/$draftId"
}

private const val NAV_MS = 320

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.DRAFT_LIST,
        // A short slide-and-fade: forward pushes in from the right, back reverses it.
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(NAV_MS), initialOffset = { it / 5 }) + fadeIn(tween(NAV_MS)) },
        exitTransition = { fadeOut(tween(NAV_MS / 2)) },
        popEnterTransition = { fadeIn(tween(NAV_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(NAV_MS), targetOffset = { it / 5 }) + fadeOut(tween(NAV_MS)) },
    ) {
        composable(Routes.DRAFT_LIST) {
            DraftListScreen(
                onCreateClick = { navController.navigate(Routes.CREATE_FORM) },
                onDraftClick = { draftId -> navController.navigate(Routes.review(draftId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogin = { navController.navigate(Routes.XHS_LOGIN) },
                onEditPrompt = { navController.navigate(Routes.PROMPT_EDITOR) },
            )
        }

        composable(Routes.PROMPT_EDITOR) {
            PromptEditorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE_FORM) {
            CreateFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onDraftSaved = { draftId ->
                    navController.navigate(Routes.generating(draftId)) {
                        popUpTo(Routes.DRAFT_LIST)
                    }
                },
            )
        }

        composable(
            route = Routes.GENERATING,
            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getLong("draftId") ?: return@composable
            GeneratingScreen(
                draftId = draftId,
                onGenerationComplete = { id ->
                    navController.navigate(Routes.review(id)) {
                        popUpTo(Routes.GENERATING) { inclusive = true }
                    }
                },
                onError = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getLong("draftId") ?: return@composable
            ReviewScreen(
                draftId = draftId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Routes.XHS_LOGIN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.XHS_LOGIN) {
            XhsLoginScreen(
                onLoginComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
