package com.chooseme.forever.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chooseme.forever.data.model.User
import com.chooseme.forever.ui.components.AppLogo
import com.chooseme.forever.ui.theme.ChooseMeForeverTheme
import com.chooseme.forever.ui.theme.PrimaryPink
import com.chooseme.forever.ui.theme.SurfaceWhite
import com.chooseme.forever.ui.theme.TextPrimary
import com.chooseme.forever.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val topProfile = uiState.topProfile
    val backgroundProfile = uiState.backgroundProfile

    // It's a Match! Celebration Dialog
    uiState.matchEvent?.let { matchData ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMatchDialog() },
            title = { },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "It's a Match!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPink,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You and ${matchData.matchedUser.name} liked each other.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Both User Avatars Side by Side
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(2.dp, PrimaryPink, CircleShape)
                        ) {
                            AppLogo(size = 80.dp)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Match Heart",
                            tint = PrimaryPink,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(2.dp, PrimaryPink, CircleShape)
                        ) {
                            val photoUrl = matchData.matchedUser.photos.firstOrNull()
                            if (!photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Matched User Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                AppLogo(size = 80.dp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            viewModel.dismissMatchDialog()
                            onNavigateToChat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPink,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Send a message",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.dismissMatchDialog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPink)
                    ) {
                        Text(
                            text = "Keep swiping",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryPink
                        )
                    }
                }
            },
            confirmButton = { },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = "discover",
                onTabSelected = { tab ->
                    when (tab) {
                        "profile" -> onNavigateToProfile()
                        "chat" -> onNavigateToChat()
                    }
                }
            )
        },
        containerColor = SurfaceWhite
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding Header
            Text(
                text = "ChooseMe Forever",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryPink,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card Stack Box Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = PrimaryPink)
                } else if (topProfile == null) {
                    // Empty State when all candidates have been swiped
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AppLogo(size = 90.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No more profiles around you!",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check back later or update your filter preferences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadDiscoveryProfiles() },
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPink)
                        ) {
                            Text("Refresh Candidates", color = PrimaryPink)
                        }
                    }
                } else {
                    // Background Card Preview
                    backgroundProfile?.let { bgUser ->
                        ProfileCardContent(
                            user = bgUser,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 12.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
                        )
                    }

                    // Top Interactive Swipable Card
                    SwipeableCard(
                        onSwiped = { direction ->
                            when (direction) {
                                SwipeDirection.RIGHT -> viewModel.swipeRight(topProfile)
                                SwipeDirection.LEFT -> viewModel.swipeLeft(topProfile)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { dragRatio ->
                        ProfileCardContent(
                            user = topProfile,
                            dragRatio = dragRatio,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Pass & Like Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pass Button (Cross)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFFE0E0E0), CircleShape)
                        .background(Color.White)
                        .clickable(enabled = topProfile != null) {
                            topProfile?.let { viewModel.swipeLeft(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Pass",
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Like Button (Heart Pink Fill)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(PrimaryPink)
                        .clickable(enabled = topProfile != null) {
                            topProfile?.let { viewModel.swipeRight(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCardContent(
    user: User,
    modifier: Modifier = Modifier,
    dragRatio: Float = 0f
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val photoUrl = user.photos.firstOrNull()

            if (!photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = user.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Figma Screen 3 Rose/Purple Gradient Background Fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF9A8B),
                                    Color(0xFFFF6A88),
                                    Color(0xFF5B3068)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(100.dp)
                    )
                }
            }

            // Bottom Dark Gradient Overlay for Text Visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF1B0B2E).copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Swipe Overlay Cues (LIKE / PASS Tags)
            if (dragRatio > 0.1f) {
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.TopStart)
                        .border(3.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "LIKE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            } else if (dragRatio < -0.1f) {
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.TopEnd)
                        .border(3.dp, Color(0xFFE53935), RoundedCornerShape(12.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "NOPE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }

            // Bottom Details Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = "${user.name}, ${if (user.age > 0) user.age else 24}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (user.city.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.city,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val displayInterests = if (user.interests.isNotEmpty()) user.interests else listOf("Music", "Travel", "Books")

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayInterests.forEach { interest ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF6B5885).copy(alpha = 0.8f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = interest,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceWhite,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == "discover",
            onClick = { onTabSelected("discover") },
            icon = { Icon(imageVector = Icons.Outlined.Explore, contentDescription = "Discover") },
            label = { Text("Discover", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryPink,
                selectedTextColor = PrimaryPink,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == "chat",
            onClick = { onTabSelected("chat") },
            icon = { Icon(imageVector = Icons.Outlined.ChatBubbleOutline, contentDescription = "Chat") },
            label = { Text("Chat", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryPink,
                selectedTextColor = PrimaryPink,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == "profile",
            onClick = { onTabSelected("profile") },
            icon = { Icon(imageVector = Icons.Outlined.Person, contentDescription = "Profile") },
            label = { Text("Profile", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryPink,
                selectedTextColor = PrimaryPink,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiscoverScreenPreview() {
    ChooseMeForeverTheme {
        DiscoverScreen(
            onNavigateToProfile = {},
            onNavigateToChat = {}
        )
    }
}
