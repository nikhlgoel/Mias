package dev.kid.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.kid.app.ui.agent.AgentScreen
import dev.kid.app.ui.chat.ChatScreen
import dev.kid.app.ui.evolution.EvolutionScreen
import dev.kid.app.ui.home.HomeScreen
import dev.kid.app.ui.modelhub.ModelHubScreen
import dev.kid.app.ui.settings.SettingsScreen
import dev.kid.app.ui.splash.SplashScreen
import dev.kid.app.ui.voice.VoiceChatScreen

object KidRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CHAT = "chat?conversationId={conversationId}"
    const val SETTINGS = "settings"
    const val MODEL_HUB = "modelhub"
    const val AGENT = "agent"
    const val EVOLUTION = "evolution"
    const val VOICE = "voice"

    fun chatRoute(conversationId: String? = null): String =
        if (conversationId != null) "chat?conversationId=$conversationId" else "chat"
}

@Composable
fun KidNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = KidRoutes.SPLASH,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn()
        },
        exitTransition = { fadeOut() },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn()
        },
        popExitTransition = { fadeOut() },
    ) {
        composable(KidRoutes.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(KidRoutes.HOME) {
                        popUpTo(KidRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(KidRoutes.HOME) {
            HomeScreen(
                onNavigateToChat = { navController.navigate(KidRoutes.chatRoute(it)) },
                onNavigateToSettings = { navController.navigate(KidRoutes.SETTINGS) },
                onNavigateToModelHub = { navController.navigate(KidRoutes.MODEL_HUB) },
                onNavigateToAgent = { navController.navigate(KidRoutes.AGENT) },
                onNavigateToEvolution = { navController.navigate(KidRoutes.EVOLUTION) },
                onNavigateToVoice = { navController.navigate(KidRoutes.VOICE) },
            )
        }

        composable(
            route = KidRoutes.CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ChatScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(KidRoutes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(KidRoutes.MODEL_HUB) {
            ModelHubScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(KidRoutes.AGENT) {
            AgentScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(KidRoutes.EVOLUTION) {
            EvolutionScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(KidRoutes.VOICE) {
            VoiceChatScreen(onBack = { navController.navigateUp() })
        }
    }
}
