package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyCircle
import com.example.data.model.FamilyMember
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.OnlineBadgeAvatar
import com.example.ui.components.QrDisplayDialog

private val STATUS_OPTIONS = listOf("At Home", "At Work", "On the Way", "Quiet / Busy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCircleScreen(
    currentUserId: String,
    profile: UserProfile?,
    familiesState: UiState<List<FamilyCircle>>,
    members: List<FamilyMember>,
    onlineStatusByUid: Map<String, Boolean>,
    onCreateFamily: (String) -> Unit,
    onJoinFamilyCode: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenMyProfile: () -> Unit,
    onInspectMember: (FamilyMember) -> Unit,
    onUpdateStatus: (familyId: String, status: String) -> Unit,
    onMessageMember: (FamilyMember) -> Unit,
    onAudioCallMember: (FamilyMember) -> Unit,
    onVideoCallMember: (FamilyMember) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var qrFamilyToDisplay by remember { mutableStateOf<FamilyCircle?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Family Circle", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Invite via QR code & manage members",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when (familiesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(familiesState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val families = familiesState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenMyProfile)
                                .testTag("family_my_profile_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OnlineBadgeAvatar(
                                    displayName = profile?.displayName ?: "Me",
                                    avatarColorHex = profile?.avatarColorHex ?: "#0F5C4D",
                                    avatarBadge = profile?.avatarBadge ?: "🏡",
                                    isOnline = profile?.showOnlineStatus ?: true,
                                    size = 50.dp,
                                    avatarPhotoBase64 = profile?.avatarPhotoBase64.orEmpty()
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = profile?.displayName?.ifBlank { "My Profile" } ?: "My Profile",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = profile?.moodTag?.ifBlank { "✨ Available" } ?: "✨ Available",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = profile?.bio?.ifBlank { "Tap to write about yourself, give air & share QR" }
                                            ?: "Tap to write about yourself, give air & share QR",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Open My Profile QR",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.weight(1f).testTag("create_family_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Family")
                            }
                            OutlinedButton(
                                onClick = onOpenScanner,
                                modifier = Modifier.weight(1f).testTag("family_scan_qr_button")
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showJoinDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("enter_invite_code_button")
                        ) {
                            Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join with Invite Code")
                        }
                    }

                    if (families.isNotEmpty()) {
                        item {
                            Text(
                                text = "My Check-In Status",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 6.dp)
                            ) {
                                items(STATUS_OPTIONS) { status ->
                                    AssistChip(
                                        onClick = {
                                            families.firstOrNull()?.let { fam ->
                                                onUpdateStatus(fam.id, status)
                                            }
                                        },
                                        label = { Text(status) }
                                    )
                                }
                            }
                        }

                        items(families, key = { it.id }) { family ->
                            val familyMembers = members.filter { it.familyId == family.id }
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Home,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = family.name,
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                                Text(
                                                    text = "${family.memberUids.size} members • Code: ${family.inviteCode}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = { qrFamilyToDisplay = family },
                                            modifier = Modifier.testTag("show_qr_button_${family.id}")
                                        ) {
                                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("QR")
                                        }
                                    }

                                    familyMembers.forEach { member ->
                                        val isMe = member.userId == currentUserId
                                        val isOnline = onlineStatusByUid[member.userId] ?: member.onlineActive
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isMe) onOpenMyProfile() else onInspectMember(member)
                                                }
                                                .testTag("family_member_row_${member.userId}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    OnlineBadgeAvatar(
                                                        displayName = member.displayName,
                                                        avatarColorHex = member.avatarColorHex,
                                                        avatarBadge = member.avatarBadge,
                                                        isOnline = isOnline,
                                                        size = 42.dp,
                                                        avatarPhotoBase64 = member.avatarPhotoBase64
                                                    )
                                                    Column {
                                                        Text(
                                                            text = if (isMe) "${member.displayName} (You)" else member.displayName,
                                                            style = MaterialTheme.typography.titleSmall
                                                        )
                                                        Text(
                                                            text = listOfNotNull(
                                                                member.familyRole.ifBlank { null },
                                                                member.moodTag.ifBlank { member.statusText.ifBlank { null } }
                                                            ).joinToString(" • "),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (member.bio.isNotBlank()) {
                                                            Text(
                                                                text = member.bio,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (!isMe) {
                                                        IconButton(
                                                            onClick = { onAudioCallMember(member) },
                                                            modifier = Modifier.testTag("family_audio_call_${member.userId}")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Call,
                                                                contentDescription = "Audio call ${member.displayName}",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { onVideoCallMember(member) },
                                                            modifier = Modifier.testTag("family_video_call_${member.userId}")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Videocam,
                                                                contentDescription = "Video call ${member.displayName}",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    } else {
                                                        IconButton(
                                                            onClick = onOpenMyProfile,
                                                            modifier = Modifier.testTag("edit_self_from_family_button")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Edit,
                                                                contentDescription = "Edit my profile",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { onMessageMember(member) },
                                                        modifier = Modifier.testTag("message_member_${member.userId}")
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.ChatBubbleOutline,
                                                            contentDescription = "Message ${member.displayName}",
                                                            tint = MaterialTheme.colorScheme.secondary
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
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Family Circle") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Family Name (e.g. Sorkar Home)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_family_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateFamily(name.trim())
                            showCreateDialog = false
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_family_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Family Circle") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite Code (e.g. FAM842910)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_family_code_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (code.isNotBlank()) {
                            onJoinFamilyCode(code.trim())
                            showJoinDialog = false
                        }
                    },
                    enabled = code.trim().length >= 6,
                    modifier = Modifier.testTag("confirm_join_family_button")
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (qrFamilyToDisplay != null) {
        QrDisplayDialog(
            familyName = qrFamilyToDisplay!!.name,
            inviteCode = qrFamilyToDisplay!!.inviteCode,
            onDismiss = { qrFamilyToDisplay = null }
        )
    }
}
