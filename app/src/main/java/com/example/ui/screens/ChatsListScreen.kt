package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.data.model.Conversation
import com.example.data.model.FamilyMember
import com.example.data.model.ProfileCard
import com.example.data.model.SavedContact
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.OnlineBadgeAvatar
import com.example.ui.theme.OnlineDotGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    currentUserId: String,
    profile: UserProfile?,
    conversationsState: UiState<List<Conversation>>,
    familyMembers: List<FamilyMember>,
    savedContacts: List<SavedContact> = emptyList(),
    peerCardsByUid: Map<String, ProfileCard> = emptyMap(),
    onlineStatusByUid: Map<String, Boolean>,
    onConversationClick: (Conversation) -> Unit,
    onMemberClick: (FamilyMember) -> Unit,
    onSavedContactClick: (SavedContact) -> Unit = {},
    onOpenMyProfile: () -> Unit,
    onOpenScanner: () -> Unit,
    onCreateFamilyClick: () -> Unit,
    onTogglePrivacyPeek: (Boolean) -> Unit,
    onLockAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hidePreviews = profile?.hideMessagePreviews ?: false

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenMyProfile)
                            .testTag("chats_my_profile_button")
                    ) {
                        OnlineBadgeAvatar(
                            displayName = profile?.displayName ?: "Me",
                            avatarColorHex = profile?.avatarColorHex ?: "#0F5C4D",
                            avatarBadge = profile?.avatarBadge ?: "🏡",
                            isOnline = profile?.showOnlineStatus ?: true,
                            size = 40.dp,
                            avatarPhotoBase64 = profile?.avatarPhotoBase64.orEmpty()
                        )
                        Column {
                            Text(
                                text = "Moinul Family",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (hidePreviews) "Privacy Peek Active"
                                else profile?.moodTag?.ifBlank { "Private Family Chats" } ?: "Private Family Chats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { onTogglePrivacyPeek(!hidePreviews) },
                        modifier = Modifier.testTag("toggle_privacy_peek_button")
                    ) {
                        Icon(
                            imageVector = if (hidePreviews) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle hide message previews"
                        )
                    }
                    IconButton(
                        onClick = onOpenScanner,
                        modifier = Modifier.testTag("chats_scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Family QR"
                        )
                    }
                    if (profile?.hasPassword == true) {
                        IconButton(
                            onClick = onLockAppClick,
                            modifier = Modifier.testTag("lock_app_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Lock Messenger"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateFamilyClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_family_chat")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Family Circle")
            }
        }
    ) { innerPadding ->
        when (conversationsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversationsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is UiState.Success -> {
                val conversations = conversationsState.data
                val familyMemberUids = familyMembers.map { it.userId }.toSet()
                val uniqueSavedContacts = savedContacts.filter {
                    val uid = it.peerUid.ifBlank { it.id }
                    uid.isNotBlank() && uid != currentUserId && !familyMemberUids.contains(uid)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add Family or Friends by QR / ID",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Scanned contacts stay in your chat list automatically.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onOpenScanner,
                                    modifier = Modifier.testTag("chats_add_id_qr_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add ID")
                                }
                            }
                        }
                    }

                    if (familyMembers.isNotEmpty() || uniqueSavedContacts.isNotEmpty()) {
                        item {
                            Text(
                                text = "Family & QR Contacts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(familyMembers, key = { "${it.familyId}_${it.userId}" }) { member ->
                                    val liveCard = peerCardsByUid[member.userId]
                                    val isOnline = onlineStatusByUid[member.userId] ?: member.onlineActive
                                    FamilyMemberChip(
                                        member = member,
                                        liveCard = liveCard,
                                        isMe = member.userId == currentUserId,
                                        isOnline = isOnline,
                                        onClick = { onMemberClick(member) }
                                    )
                                }
                                items(uniqueSavedContacts, key = { "contact_${it.peerUid.ifBlank { it.id }}" }) { contact ->
                                    val peerUid = contact.peerUid.ifBlank { contact.id }
                                    val liveCard = peerCardsByUid[peerUid]
                                    val isOnline = onlineStatusByUid[peerUid] == true
                                    SavedContactChip(
                                        contact = contact,
                                        liveCard = liveCard,
                                        isOnline = isOnline,
                                        onClick = { onSavedContactClick(contact) }
                                    )
                                }
                            }
                        }
                    }

                    if (conversations.isEmpty()) {
                        item {
                            EmptyChatsCard(
                                onCreateFamily = onCreateFamilyClick,
                                onScanQr = onOpenScanner
                            )
                        }
                    } else {
                        items(conversations, key = { it.id }) { conversation ->
                            val peerUid = if (conversation.type == "direct") {
                                conversation.memberUids.firstOrNull { it != currentUserId }
                            } else null
                            val isPeerOnline = peerUid != null && onlineStatusByUid[peerUid] == true
                            val peerCard = peerUid?.let { peerCardsByUid[it] }
                            val peerMember = peerUid?.let { uid -> familyMembers.firstOrNull { it.userId == uid } }
                            val peerContact = peerUid?.let { uid ->
                                savedContacts.firstOrNull { it.peerUid.ifBlank { it.id } == uid }
                            }
                            ConversationRowCard(
                                conversation = conversation,
                                currentUserId = currentUserId,
                                hidePreviews = hidePreviews,
                                isPeerOnline = isPeerOnline,
                                peerCard = peerCard,
                                peerMember = peerMember,
                                peerContact = peerContact,
                                onClick = { onConversationClick(conversation) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyMemberChip(
    member: FamilyMember,
    liveCard: ProfileCard?,
    isMe: Boolean,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("member_chip_${member.userId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnlineBadgeAvatar(
                displayName = liveCard?.displayName?.ifBlank { null } ?: member.displayName,
                avatarColorHex = liveCard?.avatarColorHex?.ifBlank { null } ?: member.avatarColorHex,
                avatarBadge = liveCard?.avatarBadge?.ifBlank { null } ?: member.avatarBadge,
                isOnline = isOnline,
                size = 36.dp,
                avatarPhotoBase64 = liveCard?.avatarPhotoBase64?.ifBlank { null } ?: member.avatarPhotoBase64
            )
            Column {
                Text(
                    text = if (isMe) "${member.displayName} (You)" else member.displayName,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = liveCard?.moodTag?.ifBlank { null }
                        ?: member.moodTag.ifBlank { member.statusText.ifBlank { member.familyRole } },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SavedContactChip(
    contact: SavedContact,
    liveCard: ProfileCard?,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val peerUid = contact.peerUid.ifBlank { contact.id }
    val name = liveCard?.displayName?.ifBlank { null } ?: contact.displayName
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("saved_contact_chip_$peerUid")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnlineBadgeAvatar(
                displayName = name,
                avatarColorHex = liveCard?.avatarColorHex?.ifBlank { null } ?: contact.avatarColorHex,
                avatarBadge = liveCard?.avatarBadge?.ifBlank { null } ?: contact.avatarBadge,
                isOnline = isOnline,
                size = 36.dp,
                avatarPhotoBase64 = liveCard?.avatarPhotoBase64?.ifBlank { null } ?: contact.avatarPhotoBase64
            )
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = liveCard?.moodTag?.ifBlank { null } ?: contact.moodTag.ifBlank { contact.familyRole },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConversationRowCard(
    conversation: Conversation,
    currentUserId: String,
    hidePreviews: Boolean,
    isPeerOnline: Boolean,
    peerCard: ProfileCard?,
    peerMember: FamilyMember?,
    peerContact: SavedContact?,
    onClick: () -> Unit
) {
    val isLocked = conversation.lockedByUids.contains(currentUserId)
    val displayTitle = if (conversation.type == "direct") {
        val otherEntry = conversation.memberNames.entries.firstOrNull { it.key != currentUserId }
        peerCard?.displayName?.ifBlank { null } ?: otherEntry?.value ?: conversation.title
    } else {
        conversation.title
    }
    val timeText = conversation.lastMessageAt?.toDate()?.let {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
    }.orEmpty()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_card_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (conversation.type == "direct") {
                val avatarColor = peerCard?.avatarColorHex?.ifBlank { null }
                    ?: peerContact?.avatarColorHex?.ifBlank { null }
                    ?: peerMember?.avatarColorHex
                    ?: "#0F5C4D"
                val avatarBadge = peerCard?.avatarBadge?.ifBlank { null }
                    ?: peerContact?.avatarBadge?.ifBlank { null }
                    ?: peerMember?.avatarBadge
                    ?: ""
                val avatarPhoto = peerCard?.avatarPhotoBase64?.ifBlank { null }
                    ?: peerContact?.avatarPhotoBase64?.ifBlank { null }
                    ?: peerMember?.avatarPhotoBase64.orEmpty()
                OnlineBadgeAvatar(
                    displayName = displayTitle,
                    avatarColorHex = avatarColor,
                    avatarBadge = avatarBadge,
                    isOnline = isPeerOnline,
                    size = 50.dp,
                    avatarPhotoBase64 = avatarPhoto
                )
            } else {
                Box(modifier = Modifier.size(50.dp)) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val activeTypers = conversation.typingUids
                    .filter { it != currentUserId }
                    .map { uid -> conversation.typingNames[uid]?.ifBlank { null } ?: "Someone" }

                val preview = when {
                    isLocked -> "🔒 Private Locked Chat — tap to unlock"
                    activeTypers.isNotEmpty() -> "✍️ ${activeTypers.joinToString(", ")} is typing…"
                    hidePreviews -> "•••••••• (Message preview hidden)"
                    conversation.lastMessageText.isNotBlank() -> {
                        val prefix = if (conversation.lastMessageSenderName.isNotBlank()) {
                            "${conversation.lastMessageSenderName}: "
                        } else ""
                        "$prefix${conversation.lastMessageText}"
                    }
                    else -> "Start messaging privately"
                }

                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activeTypers.isNotEmpty() && !isLocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (conversation.disappearingHours > 0 || isLocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isLocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Locked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (conversation.disappearingHours > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (conversation.disappearingHours == 24L) "24h" else "7d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsCard(
    onCreateFamily: () -> Unit,
    onScanQr: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Start Your Private Family Circle",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Create a family group room or scan a family member's QR code to start private messaging.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCreateFamily,
                modifier = Modifier.fillMaxWidth().testTag("empty_create_family_button")
            ) {
                Text("Create Family Circle")
            }
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth().testTag("empty_scan_qr_button")
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR or Add by ID")
            }
        }
    }
}
