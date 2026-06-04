package dev.mias.app.ui

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
import dev.mias.app.ui.chat.ChatScreen
import dev.mias.app.ui.chats.ChatsScreen
import dev.mias.app.ui.home.HomeScreen
import dev.mias.app.ui.knowledge.KnowledgeScreen
import dev.mias.app.ui.modelhub.ModelHubScreen
import dev.mias.app.ui.settings.SettingsScreen
import dev.mias.app.ui.splash.SplashScreen
import dev.mias.app.ui.vision.VisionChatScreen
import dev.mias.app.ui.voice.VoiceChatScreen

object MiasRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CHAT = "chat?conversationId={conversationId}"
    const val CHATS = "chats"
    const val SETTINGS = "settings"
    const val MODEL_HUB = "modelhub"
    const val VOICE = "voice"
    const val VISION = "vision"
    const val KNOWLEDGE = "knowledge"

    fun chatRoute(conversationId: String? = null): String =
        if (conversationId != null) "chat?conversationId=$conversationId" else "chat"
}

@Composable
fun MiasNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MiasRoutes.SPLASH,
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
        composable(MiasRoutes.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(MiasRoutes.HOME) {
                        popUpTo(MiasRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(MiasRoutes.HOME) {
            HomeScreen(
                onNavigateToChat = { navController.navigate(MiasRoutes.chatRoute(it)) },
                onNavigateToSettings = { navController.navigate(MiasRoutes.SETTINGS) },
                onNavigateToModelHub = { navController.navigate(MiasRoutes.MODEL_HUB) },
                onNavigateToChats = { navController.navigate(MiasRoutes.CHATS) },
                onNavigateToVoice = { navController.navigate(MiasRoutes.VOICE) },
                onNavigateToVision = { navController.navigate(MiasRoutes.VISION) },
                onNavigateToKnowledge = { navController.navigate(MiasRoutes.KNOWLEDGE) },
            )
        }

        composable(MiasRoutes.CHATS) {
            ChatsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenConversation = { id ->
                    navController.navigate(MiasRoutes.chatRoute(id))
                },
            )
        }

        composable(
            route = MiasRoutes.CHAT,
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

        composable(MiasRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(MiasRoutes.MODEL_HUB) },
            )
        }

        composable(MiasRoutes.MODEL_HUB) {
            ModelHubScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(MiasRoutes.VOICE) {
            VoiceChatScreen(onBack = { navController.navigateUp() })
        }

        composable(MiasRoutes.VISION) {
            VisionChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(MiasRoutes.MODEL_HUB) },
            )
        }

        composable(MiasRoutes.KNOWLEDGE) {
            KnowledgeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(MiasRoutes.MODEL_HUB) },
            )
        }
    }
}
