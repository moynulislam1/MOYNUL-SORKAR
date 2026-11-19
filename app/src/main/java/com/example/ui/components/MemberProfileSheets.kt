package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.media.ProfilePhotoUtils
import com.example.data.model.ProfileCard
import com.example.data.model.UserProfile
import com.example.data.security.CryptoUtils
import com.example.data.security.QrCodeGenerator
import com.example.ui.theme.AvatarColorPresets
import com.example.ui.theme.OnlineDotGreen
import com.example.ui.theme.avatarColorFromHex
import kotlinx.coroutines.launch

private val MOOD_AIR_PRESETS = listOf(
    "✨ Available",
    "🌿 Calm Air",
    "☀️ Sunny Vibe",
    "🏡 At Home",
    "☕ Coffee Break",
    "🎧 Listening",
    "💪 Energized",
    "🌙 Resting"
)

private val AVATAR_BADGE_PRESETS = listOf(
    "🏡", "🌿", "☀️", "🦋", "🎧", "☕", "💫", "🫶"
)

@Composable
fun OnlineBadgeAvatar(
    displayName: String,
    avatarColorHex: String,
    avatarBadge: String,
    isOnline: Boolean,
    size: Dp = 44.dp,
    avatarPhotoBase64: String = "",
    modifier: Modifier = Modifier
) {
    val bgColor = avatarColorFromHex(avatarColorHex, MaterialTheme.colorScheme.primary)
    val photoBitmap = remember(avatarPhotoBase64) {
        ProfilePhotoUtils.decodeBase64ToImageBitmap(avatarPhotoBase64)
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = displayName.ifBlank { "Profile photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (avatarBadge.isNotBlank()) {
                Text(
                    text = avatarBadge,
                    fontSize = (size.value * 0.44f).sp
                )
            } else {
                Text(
                    text = displayName.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
        if (isOnline) {
            val dotSize = (size.value * 0.28f).coerceIn(10f, 18f).dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(OnlineDotGreen)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfilePreviewSheet(
    card: ProfileCard,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onEditOwnProfile: () -> Unit,
    onStartChat: (ProfileCard) -> Unit,
    onStartAudioCall: (ProfileCard) -> Unit,
    onStartVideoCall: (ProfileCard) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isOnlineVisible = card.onlineActive && card.showOnlineStatus
    val qrPayload = remember(card.userId, card.profileCode) {
        CryptoUtils.buildProfileQrPayload(card.userId, card.profileCode)
    }
    val qrBitmap = remember(qrPayload) {
        if (card.profileCode.isNotBlank() || card.userId.isNotBlank()) {
            QrCodeGenerator.generateQrBitmap(qrPayload, 440)
        } else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSelf) "My Public Card" else "Family Member Profile",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_member_profile_sheet")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close profile")
                }
            }

            OnlineBadgeAvatar(
                displayName = card.displayName,
                avatarColorHex = card.avatarColorHex,
                avatarBadge = card.avatarBadge,
                isOnline = isOnlineVisible,
                size = 82.dp,
                avatarPhotoBase64 = card.avatarPhotoBase64
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = card.displayName.ifBlank { "Family Member" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = card.familyRole.ifBlank { "Member" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = if (isOnlineVisible) OnlineDotGreen.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isOnlineVisible) OnlineDotGreen
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                            Text(
                                text = if (isOnlineVisible) "Online" else "Offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOnlineVisible) OnlineDotGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Air / Mood Vibe Card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Air,
                        contentDescription = "Current air and mood",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Current Air / Vibe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = card.moodTag.ifBlank { "✨ Available" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // About Me / Bio Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "About ${card.displayName.substringBefore(" ").ifBlank { "Member" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = card.bio.ifBlank { "No bio written yet." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (card.bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Actions
            if (isSelf) {
                Button(
                    onClick = {
                        onDismiss()
                        onEditOwnProfile()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_edit_my_profile_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit My Bio, Air & QR")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onStartChat(card)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_chat_button")
                    ) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat")
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onStartAudioCall(card)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_audio_call_button")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio")
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onStartVideoCall(card)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_video_call_button")
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video")
                    }
                }
            }

            if (qrBitmap != null && card.profileCode.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Personal QR code",
                                modifier = Modifier.size(74.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Profile QR & Code",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = card.profileCode,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Profile Code", card.profileCode))
                                    Toast.makeText(context, "Profile code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_member_profile_code")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Code")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileEditorSheet(
    profile: UserProfile?,
    currentUserId: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveProfile: (
        displayName: String,
        familyRole: String,
        bio: String,
        moodTag: String,
        avatarBadge: String,
        avatarColorHex: String,
        avatarPhotoBase64: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var familyRole by remember(profile?.familyRole) { mutableStateOf(profile?.familyRole ?: "Member") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var moodTag by remember(profile?.moodTag) { mutableStateOf(profile?.moodTag ?: "✨ Available") }
    var avatarBadge by remember(profile?.avatarBadge) { mutableStateOf(profile?.avatarBadge ?: "🏡") }
    var avatarColorHex by remember(profile?.avatarColorHex) { mutableStateOf(profile?.avatarColorHex ?: "#0F5C4D") }
    var avatarPhotoBase64 by remember(profile?.avatarPhotoBase64) { mutableStateOf(profile?.avatarPhotoBase64.orEmpty()) }
    var isEncodingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isEncodingPhoto = true
            scope.launch {
                val encoded = ProfilePhotoUtils.encodeUriToBase64Avatar(context, uri)
                isEncodingPhoto = false
                if (encoded != null) {
                    avatarPhotoBase64 = encoded
                    Toast.makeText(context, "Photo selected — tap Save Profile to apply", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read selected image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val profileCode = profile?.profileCode.orEmpty()
    val qrPayload = remember(currentUserId, profileCode) {
        CryptoUtils.buildProfileQrPayload(currentUserId, profileCode)
    }
    val qrBitmap = remember(qrPayload) {
        QrCodeGenerator.generateQrBitmap(qrPayload, 480)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "My Profile, Air & QR",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_my_profile_editor")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Personal QR + Code + User ID share card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "My Profile QR Code",
                                modifier = Modifier.size(92.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Share Your Profile QR or ID",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Anyone who scans your QR or enters your ID is added straight to both chat lists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (profileCode.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profileCode,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("My Profile Code", profileCode))
                                        Toast.makeText(context, "Code $profileCode copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp).testTag("copy_my_profile_code")
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy profile code", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (currentUserId.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ID: ${currentUserId.take(12)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("My User ID", currentUserId))
                                        Toast.makeText(context, "User ID copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp).testTag("copy_my_user_id")
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy User ID", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Live Avatar, Gallery Photo Picker & Badge Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OnlineBadgeAvatar(
                    displayName = displayName,
                    avatarColorHex = avatarColorHex,
                    avatarBadge = avatarBadge,
                    isOnline = profile?.showOnlineStatus ?: true,
                    size = 68.dp,
                    avatarPhotoBase64 = avatarPhotoBase64
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = displayName.ifBlank { "Your Name" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = moodTag.ifBlank { "✨ Available" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isEncodingPhoto && !isBusy,
                            modifier = Modifier.testTag("upload_profile_photo_button")
                        ) {
                            if (isEncodingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (avatarPhotoBase64.isNotBlank()) "Change Photo" else "Upload Photo")
                        }
                        if (avatarPhotoBase64.isNotBlank()) {
                            TextButton(
                                onClick = { avatarPhotoBase64 = "" },
                                modifier = Modifier.testTag("remove_profile_photo_button")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            // Badge & Color Pickers
            Text(
                text = "Avatar Badge & Color",
                style = MaterialTheme.typography.labelLarge
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(AVATAR_BADGE_PRESETS) { badge ->
                    FilterChip(
                        selected = avatarBadge == badge,
                        onClick = { avatarBadge = badge },
                        label = { Text(badge, fontSize = 18.sp) }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarColorPresets.forEach { hex ->
                    val selected = avatarColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(avatarColorFromHex(hex))
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                else Modifier
                            )
                            .clickable { avatarColorHex = hex }
                    )
                }
            }

            // Give Air / Mood
            Text(
                text = "Give Air / Set Your Mood",
                style = MaterialTheme.typography.labelLarge
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MOOD_AIR_PRESETS) { preset ->
                    FilterChip(
                        selected = moodTag == preset,
                        onClick = { moodTag = preset },
                        label = { Text(preset) }
                    )
                }
            }
            OutlinedTextField(
                value = moodTag,
                onValueChange = { if (it.length <= 40) moodTag = it },
                label = { Text("Custom Air / Vibe (max 40 chars)") },
                leadingIcon = { Icon(Icons.Rounded.Air, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_mood_air_input")
            )

            // About Me / Bio
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 300) bio = it },
                label = { Text("About Me (write about yourself)") },
                placeholder = { Text("Share what you're up to, hobbies, or a warm family note…") },
                minLines = 3,
                maxLines = 5,
                supportingText = { Text("${bio.length}/300") },
                modifier = Modifier.fillMaxWidth().testTag("profile_bio_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("editor_display_name_input")
                )
                OutlinedTextField(
                    value = familyRole,
                    onValueChange = { familyRole = it },
                    label = { Text("Role") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("editor_family_role_input")
                )
            }

            Button(
                onClick = {
                    onSaveProfile(
                        displayName,
                        familyRole,
                        bio,
                        moodTag,
                        avatarBadge,
                        avatarColorHex,
                        avatarPhotoBase64
                    )
                    onDismiss()
                },
                enabled = !isBusy && !isEncodingPhoto && displayName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_my_profile_editor_button")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile, Photo & Air")
            }
        }
    }
}
