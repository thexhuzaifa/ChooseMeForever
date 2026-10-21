package com.chooseme.forever.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chooseme.forever.ui.auth.AuthScreen
import com.chooseme.forever.ui.chat.ChatListScreen
import com.chooseme.forever.ui.chat.ChatScreen
import com.chooseme.forever.ui.discover.DiscoverScreen
import com.chooseme.forever.ui.profile.CreateProfileScreen
import com.chooseme.forever.ui.profile.EditProfileScreen
import com.chooseme.forever.ui.profile.ProfileScreen
import com.chooseme.forever.ui.welcome.WelcomeScreen

object ScreenRoutes {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val CREATE_PROFILE = "create_profile"
    const val DISCOVER = "discover"
    const val CHAT_LIST = "chat_list"
    const val CHAT_DETAIL = "chat_detail"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.WELCOME,
        modifier = modifier
    ) {
        // 1. Welcome Screen
        composable(ScreenRoutes.WELCOME) {
            WelcomeScreen(
                onNavigateToCreateProfile = {
                    navController.navigate("${ScreenRoutes.AUTH}?isSignUp=true")
                },
                onLoginClick = {
                    navController.navigate("${ScreenRoutes.AUTH}?isSignUp=false")
                }
            )
        }

        // 2. Authentication Screen (Google + Email/Password)
        composable(
            route = "${ScreenRoutes.AUTH}?isSignUp={isSignUp}",
            arguments = listOf(
                navArgument("isSignUp") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) { backStackEntry ->
            val isSignUp = backStackEntry.arguments?.getBoolean("isSignUp") ?: true
            AuthScreen(
                initialIsSignUp = isSignUp,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAuthSuccess = {
                    Toast.makeText(context, "Authentication successful!", Toast.LENGTH_SHORT).show()
                    navController.navigate(ScreenRoutes.CREATE_PROFILE)
                }
            )
        }

        // 3. Create Profile Screen
        composable(ScreenRoutes.CREATE_PROFILE) {
            CreateProfileScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(ScreenRoutes.WELCOME) {
                            popUpTo(0)
                        }
                    }
                },
                onNavigateNext = {
                    Toast.makeText(context, "Profile Setup Complete!", Toast.LENGTH_SHORT).show()
                    navController.navigate(ScreenRoutes.DISCOVER) {
                        popUpTo(ScreenRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        // 4. Discover Screen (Figma Screen 3 - Tinder Swiping)
        composable(ScreenRoutes.DISCOVER) {
            DiscoverScreen(
                onNavigateToProfile = {
                    navController.navigate(ScreenRoutes.PROFILE)
                },
                onNavigateToChat = {
                    navController.navigate(ScreenRoutes.CHAT_LIST)
                }
            )
        }

        // 5. Chat List Screen (Figma Screen 4 - Conversation List)
        composable(ScreenRoutes.CHAT_LIST) {
            ChatListScreen(
                onNavigateToChat = { matchId, partnerUserId ->
                    navController.navigate("${ScreenRoutes.CHAT_DETAIL}/$matchId/$partnerUserId")
                },
                onNavigateToDiscover = {
                    navController.navigate(ScreenRoutes.DISCOVER)
                },
                onNavigateToProfile = {
                    navController.navigate(ScreenRoutes.PROFILE)
                }
            )
        }

        // 6. 1-on-1 Chat Screen (Figma Screen 5 - Realtime Chat)
        composable(
            route = "${ScreenRoutes.CHAT_DETAIL}/{matchId}/{partnerUserId}",
            arguments = listOf(
                navArgument("matchId") { type = NavType.StringType },
                navArgument("partnerUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: "match_maria"
            val partnerUserId = backStackEntry.arguments?.getString("partnerUserId") ?: "user_maria"

            ChatScreen(
                matchId = matchId,
                partnerUserId = partnerUserId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 7. Main Profile Screen (Figma Screen 6)
        composable(ScreenRoutes.PROFILE) {
            ProfileScreen(
                onNavigateToEditProfile = {
                    navController.navigate(ScreenRoutes.EDIT_PROFILE)
                },
                onNavigateToDiscover = {
                    navController.navigate(ScreenRoutes.DISCOVER)
                },
                onLogoutSuccess = {
                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    navController.navigate(ScreenRoutes.WELCOME) {
                        popUpTo(0)
                    }
                }
            )
        }

        // 8. Edit Profile Sub-Screen
        composable(ScreenRoutes.EDIT_PROFILE) {
            EditProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
