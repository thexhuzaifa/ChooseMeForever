package com.chooseme.forever.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chooseme.forever.data.model.CallSession
import com.chooseme.forever.data.model.User
import com.chooseme.forever.ui.auth.AuthScreen
import com.chooseme.forever.ui.call.IncomingCallScreen
import com.chooseme.forever.ui.call.OutgoingCallScreen
import com.chooseme.forever.ui.chat.ChatListScreen
import com.chooseme.forever.ui.chat.ChatScreen
import com.chooseme.forever.ui.components.AppLogo
import com.chooseme.forever.ui.discover.DiscoverScreen
import com.chooseme.forever.ui.premium.PremiumScreen
import com.chooseme.forever.ui.profile.CreateProfileScreen
import com.chooseme.forever.ui.profile.EditProfileScreen
import com.chooseme.forever.ui.profile.ProfileScreen
import com.chooseme.forever.ui.welcome.WelcomeScreen
import com.chooseme.forever.ui.theme.WelcomeBackground
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ScreenRoutes {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val CREATE_PROFILE = "create_profile"
    const val DISCOVER = "discover"
    const val CHAT_LIST = "chat_list"
    const val CHAT_DETAIL = "chat_detail"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val OUTGOING_CALL = "outgoing_call"
    const val INCOMING_CALL = "incoming_call"
    const val PREMIUM = "premium"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Session Routing Check on App Startup
    LaunchedEffect(Unit) {
        val auth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
        val firestore = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

        val currentUser = auth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                val snapshot = firestore.collection("users").document(currentUser.uid).get().await()
                val user = snapshot.toObject(User::class.java)
                if (user != null && user.name.isNotBlank() && user.age >= 18) {
                    startDestination = ScreenRoutes.DISCOVER
                } else {
                    startDestination = ScreenRoutes.CREATE_PROFILE
                }
            } catch (e: Exception) {
                startDestination = ScreenRoutes.DISCOVER
            }
        } else if (currentUser != null) {
            startDestination = ScreenRoutes.CREATE_PROFILE
        } else {
            startDestination = ScreenRoutes.WELCOME
        }
    }

    if (startDestination == null) {
        // Splash Loading State with Branded Logo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WelcomeBackground),
            contentAlignment = Alignment.Center
        ) {
            AppLogo(size = 120.dp)
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
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
                        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
                        val firestore = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

                        if (currentUser != null && firestore != null) {
                            firestore.collection("users").document(currentUser.uid).get()
                                .addOnSuccessListener { snapshot ->
                                    val user = snapshot.toObject(User::class.java)
                                    if (user != null && user.name.isNotBlank() && user.age >= 18) {
                                        navController.navigate(ScreenRoutes.DISCOVER) {
                                            popUpTo(ScreenRoutes.WELCOME) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(ScreenRoutes.CREATE_PROFILE) {
                                            popUpTo(ScreenRoutes.WELCOME) { inclusive = true }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    navController.navigate(ScreenRoutes.CREATE_PROFILE) {
                                        popUpTo(ScreenRoutes.WELCOME) { inclusive = true }
                                    }
                                }
                        } else {
                            navController.navigate(ScreenRoutes.CREATE_PROFILE) {
                                popUpTo(ScreenRoutes.WELCOME) { inclusive = true }
                            }
                        }
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
                    },
                    onStartCall = { targetUserId ->
                        navController.navigate("${ScreenRoutes.OUTGOING_CALL}/$targetUserId")
                    },
                    onNavigateToPremium = {
                        navController.navigate(ScreenRoutes.PREMIUM)
                    }
                )
            }

            // 7. Outgoing Call Screen (Figma Screen 11)
            composable(
                route = "${ScreenRoutes.OUTGOING_CALL}/{partnerUserId}",
                arguments = listOf(navArgument("partnerUserId") { type = NavType.StringType })
            ) { backStackEntry ->
                val partnerUserId = backStackEntry.arguments?.getString("partnerUserId") ?: "user_maria"
                OutgoingCallScreen(
                    partnerUserId = partnerUserId,
                    onCallEnded = {
                        navController.popBackStack()
                    }
                )
            }

            // 8. Incoming Call Screen (Figma Screen 12)
            composable(
                route = "${ScreenRoutes.INCOMING_CALL}/{callId}",
                arguments = listOf(navArgument("callId") { type = NavType.StringType })
            ) { backStackEntry ->
                val callId = backStackEntry.arguments?.getString("callId") ?: "call_123"
                IncomingCallScreen(
                    callSession = CallSession(callId = callId, callerName = "Maria"),
                    onCallAccepted = {
                        Toast.makeText(context, "Call Connected", Toast.LENGTH_SHORT).show()
                    },
                    onCallDeclined = {
                        navController.popBackStack()
                    }
                )
            }

            // 9. Premium Paywall Screen
            composable(ScreenRoutes.PREMIUM) {
                PremiumScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSubscriptionSuccess = {
                        navController.popBackStack()
                    }
                )
            }

            // 10. Main Profile Screen (Figma Screen 6)
            composable(ScreenRoutes.PROFILE) {
                ProfileScreen(
                    onNavigateToEditProfile = {
                        navController.navigate(ScreenRoutes.EDIT_PROFILE)
                    },
                    onNavigateToDiscover = {
                        navController.navigate(ScreenRoutes.DISCOVER)
                    },
                    onNavigateToChat = {
                        navController.navigate(ScreenRoutes.CHAT_LIST)
                    },
                    onLogoutSuccess = {
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        navController.navigate(ScreenRoutes.WELCOME) {
                            popUpTo(0)
                        }
                    }
                )
            }

            // 11. Edit Profile Sub-Screen
            composable(ScreenRoutes.EDIT_PROFILE) {
                EditProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
