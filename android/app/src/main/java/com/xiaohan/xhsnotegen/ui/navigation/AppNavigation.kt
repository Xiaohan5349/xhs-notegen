package com.xiaohan.xhsnotegen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaohan.xhsnotegen.ui.create.CreateFormScreen
import com.xiaohan.xhsnotegen.ui.drafts.DraftListScreen
import com.xiaohan.xhsnotegen.ui.generate.GeneratingScreen
import com.xiaohan.xhsnotegen.ui.review.ReviewScreen

object Routes {
    const val DRAFT_LIST = "drafts"
    const val CREATE_FORM = "create"
    const val GENERATING = "generating/{draftId}"
    const val REVIEW = "review/{draftId}"

    fun generating(draftId: Long) = "generating/$draftId"
    fun review(draftId: Long) = "review/$draftId"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DRAFT_LIST) {
        composable(Routes.DRAFT_LIST) {
            DraftListScreen(
                onCreateClick = { navController.navigate(Routes.CREATE_FORM) },
                onDraftClick = { draftId ->
                    navController.navigate(Routes.review(draftId))
                },
            )
        }

        composable(Routes.CREATE_FORM) {
            CreateFormScreen(
                onNavigateBack = { navController.popBackStack() },
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
            )
        }
    }
}
