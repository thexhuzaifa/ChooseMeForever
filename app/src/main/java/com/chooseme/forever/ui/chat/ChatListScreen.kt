package com.chooseme.forever.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chooseme.forever.data.model.ChatConversation
import com.chooseme.forever.ui.profile.BottomNavigationBar
import com.chooseme.forever.ui.theme.BorderSubtle
import com.chooseme.forever.ui.theme.ChooseMeForeverTheme
import com.chooseme.forever.ui.theme.PrimaryPink
import com.chooseme.forever.ui.theme.SurfaceWhite
import com.chooseme.forever.ui.theme.TextPrimary
import com.chooseme.forever.ui.theme.TextSecondary

@Composable
fun ChatListScreen(
    onNavigateToChat: (matchId: String, partnerUserId: String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = "chat",
                onTabSelected = { tab ->
                    when (tab) {
                        "discover" -> onNavigateToDiscover()
                        "profile" -> onNavigateToProfile()
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Title
            Text(
                text = "Chat",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryPink,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search chats", color = TextSecondary.copy(alpha = 0.7f), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPink,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryPink)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredConversations) { conversation ->
                        ConversationListItem(
                            conversation = conversation,
                            onClick = {
                                onNavigateToChat(
                                    conversation.matchId,
                                    conversation.otherUser.userId
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationListItem(
    conversation: ChatConversation,
    onClick: () -> Unit
) {
    val otherUser = conversation.otherUser
    val photoUrl = otherUser.photos.firstOrNull()

    // Determine colorful avatar badge based on name matching Figma Screen 4
    val badgeColor = when (otherUser.name.take(1).uppercase()) {
        "A" -> Color(0xFFFF4B72) // Pink
        "S" -> Color(0xFF7A52C7) // Purple
        "H" -> Color(0xFF1CA49A) // Teal
        "Z", "F" -> Color(0xFFF7A038) // Orange
        else -> PrimaryPink
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = otherUser.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = otherUser.name.take(1).uppercase().ifEmpty { "A" }
                    Text(
                        text = initial,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name & Last Message
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherUser.name.ifEmpty { "User" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = conversation.lastMessage,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Relative Timestamp
            Text(
                text = formatRelativeTime(conversation.lastMessageTimestamp),
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(
            color = BorderSubtle.copy(alpha = 0.5f),
            thickness = 0.8.dp
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "9:41"
        days < 2 -> "Yesterday"
        days < 7 -> "Mon"
        else -> "Sun"
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenPreview() {
    ChooseMeForeverTheme {
        ChatListScreen(
            onNavigateToChat = { _, _ -> },
            onNavigateToDiscover = {},
            onNavigateToProfile = {}
        )
    }
}
