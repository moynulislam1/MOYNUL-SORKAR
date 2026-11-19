package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.media.ChatMediaUtils
import com.example.data.media.FamilyStickerCatalog
import com.example.data.media.ProfilePhotoUtils
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ProfileCard
import com.example.data.model.UiState
import com.example.ui.components.OnlineBadgeAvatar
import com.example.ui.components.PrivacyLockOverlay
import com.example.ui.theme.OnlineDotGreen
import com.example.ui.theme.avatarColorFromHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val QUICK_CHECKINS = listOf(
    "🏠 Home safely",
    "🚗 On my way",
    "🍽️ Dinner ready",
    "📞 Call me when free"
)

private val QUICK_EMOJIS = listOf("❤️", "👍", "🙏", "😊", "🎉")

private val COMPOSER_EMOJIS = listOf(
    "❤️", "😍", "😘", "🤗", "😊", "😂", "🥹", "🙏",
    "🤲", "🎉", "🥳", "🌟", "✨", "👍", "👏", "💐",
    "🏡", "☕", "🍽️", "🎂", "🚗", "✈️", "📞", "📍",
    "💚", "💙", "🤍", "🙌", "🤝", "😴", "🌸", "🫶"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    currentUserId: String,
    isChatUnlocked: Boolean,
    readReceiptsEnabled: Boolean = true,
    messagesState: UiState<List<ChatMessage>>,
    peerProfileCard: ProfileCard? = null,
    onUnlockChat: (String) -> Boolean,
    onSendMessage: (
        text: String,
        type: String,
        mediaBase64: String?,
        thumbnailBase64: String?,
        mediaMimeType: String?,
        videoDurationMs: Long?,
        stickerId: String?,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?,
        replyText: String?,
        replySender: String?
    ) -> Unit,
    onMessagesViewed: (List<ChatMessage>) -> Unit = {},
    onTypingChanged: (Boolean) -> Unit = {},
    onFeedbackMessage: (String) -> Unit = {},
    onReact: (ChatMessage, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdatePrivacy: (disappearingHours: Long, toggleLock: Boolean) -> Unit,
    onPeerHeaderClick: (String) -> Unit = {},
    onStartAudioCall: (calleeUid: String, calleeName: String) -> Unit = { _, _ -> },
    onStartVideoCall: (calleeUid: String, calleeName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val isLockedByMe = conversation.lockedByUids.contains(currentUserId)
    if (isLockedByMe && !isChatUnlocked) {
        PrivacyLockOverlay(
            title = "Locked Private Chat",
            subtitle = "Enter your Privacy Password to open ${conversation.title}.",
            requirePhoneVerification = false,
            registeredPhoneHint = "",
            onUnlock = { _, password -> onUnlockChat(password) },
            onCancel = onBack
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showPrivacyMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiStickerTray by remember { mutableStateOf(false) }
    var trayTabIndex by remember { mutableIntStateOf(0) }
    var selectedStickerCategory by remember { mutableStateOf("All") }
    var isSendingMedia by remember { mutableStateOf(false) }
    var mediaStatusText by remember { mutableStateOf("") }

    var previewImageMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var playingVideoMessage by remember { mutableStateOf<ChatMessage?>(null) }

    fun sendSimpleText(text: String, type: String = "text") {
        onSendMessage(
            text, type, null, null, null, null, null, null, null, null,
            replyingTo?.text, replyingTo?.senderName
        )
        replyingTo = null
    }

    fun fetchAndSendLocation() {
        scope.launch {
            isSendingMedia = true
            mediaStatusText = "Getting your current location…"
            val loc = ChatMediaUtils.fetchCurrentLocation(context)
            isSendingMedia = false
            mediaStatusText = ""
            if (loc != null) {
                onSendMessage(
                    "📍 ${loc.label}",
                    "location",
                    null, null, null, null, null,
                    loc.latitude,
                    loc.longitude,
                    loc.label,
                    replyingTo?.text,
                    replyingTo?.senderName
                )
                replyingTo = null
            } else {
                val msg = "Unable to detect GPS location. Enable Location services and try again."
                onFeedbackMessage(msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchAndSendLocation()
        } else {
            val msg = "Location permission is required to share your location with family."
            onFeedbackMessage(msg)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val galleryPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isSendingMedia = true
                mediaStatusText = "Preparing photo from gallery…"
                val encoded = ChatMediaUtils.encodeChatImageToBase64(context, uri)
                isSendingMedia = false
                mediaStatusText = ""
                if (encoded != null) {
                    val caption = messageText.trim().ifBlank { "📷 Photo" }
                    onSendMessage(
                        caption,
                        "image",
                        encoded,
                        null,
                        "image/jpeg",
                        null, null, null, null, null,
                        replyingTo?.text,
                        replyingTo?.senderName
                    )
                    if (messageText.isNotBlank()) messageText = ""
                    replyingTo = null
                } else {
                    val msg = "Could not load selected photo."
                    onFeedbackMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isSendingMedia = true
                mediaStatusText = "Preparing video from gallery…"
                val encoded = ChatMediaUtils.encodeChatVideo(context, uri)
                isSendingMedia = false
                mediaStatusText = ""
                if (encoded != null) {
                    val caption = messageText.trim().ifBlank { "🎥 Video" }
                    onSendMessage(
                        caption,
                        "video",
                        encoded.mediaBase64.ifBlank { null },
                        encoded.thumbnailBase64.ifBlank { null },
                        encoded.mimeType,
                        encoded.durationMs,
                        null, null, null, null,
                        replyingTo?.text,
                        replyingTo?.senderName
                    )
                    if (messageText.isNotBlank()) messageText = ""
                    replyingTo = null
                } else {
                    val msg = "Could not process selected video."
                    onFeedbackMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Live typing indicator broadcast ("text mark" visible to peer)
    LaunchedEffect(messageText, conversation.id) {
        if (messageText.isNotBlank()) {
            onTypingChanged(true)
            delay(2500L)
            onTypingChanged(false)
        } else {
            onTypingChanged(false)
        }
    }

    DisposableEffect(conversation.id) {
        onDispose { onTypingChanged(false) }
    }

    val peerUid = if (conversation.type == "direct") {
        conversation.memberUids.firstOrNull { it != currentUserId }
    } else null

    val displayTitle = if (conversation.type == "direct") {
        conversation.memberNames.entries.firstOrNull { it.key != currentUserId }?.value ?: conversation.title
    } else {
        conversation.title
    }
    val isPeerOnline = peerProfileCard != null && peerProfileCard.onlineActive && peerProfileCard.showOnlineStatus
    val otherTypers = conversation.typingUids
        .filter { it != currentUserId }
        .map { uid -> conversation.typingNames[uid]?.ifBlank { null } ?: "Family Member" }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = if (peerUid != null) {
                            Modifier.clickable { onPeerHeaderClick(peerUid) }
                        } else Modifier
                    ) {
                        if (peerUid != null) {
                            OnlineBadgeAvatar(
                                displayName = displayTitle,
                                avatarColorHex = peerProfileCard?.avatarColorHex ?: "#0F5C4D",
                                avatarBadge = peerProfileCard?.avatarBadge ?: "🏡",
                                isOnline = isPeerOnline,
                                size = 38.dp,
                                avatarPhotoBase64 = peerProfileCard?.avatarPhotoBase64.orEmpty()
                            )
                        }
                        Column {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (otherTypers.isNotEmpty()) {
                                    Text(
                                        text = "✍️ ${otherTypers.joinToString(", ")} typing…",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    if (isPeerOnline) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(OnlineDotGreen)
                                        )
                                        Text(
                                            text = "Online",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = OnlineDotGreen
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    val timerLabel = when (conversation.disappearingHours) {
                                        24L -> " • 24h auto-clear"
                                        168L -> " • 7d auto-clear"
                                        else -> ""
                                    }
                                    val baseSubtitle = if (peerUid != null) {
                                        peerProfileCard?.moodTag?.ifBlank { "Tap to view profile" } ?: "Tap to view profile"
                                    } else {
                                        "${conversation.memberUids.size} members"
                                    }
                                    Text(
                                        text = "$baseSubtitle$timerLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (peerUid != null) {
                        IconButton(
                            onClick = { onStartAudioCall(peerUid, displayTitle) },
                            modifier = Modifier.testTag("chat_audio_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Start audio call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onStartVideoCall(peerUid, displayTitle) },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Start video call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showPrivacyMenu = true },
                            modifier = Modifier.testTag("chat_privacy_menu_button")
                        ) {
                            Icon(
                                imageVector = if (isLockedByMe) Icons.Rounded.Lock else Icons.Rounded.Timer,
                                contentDescription = "Chat privacy options"
                            )
                        }
                        DropdownMenu(
                            expanded = showPrivacyMenu,
                            onDismissRequest = { showPrivacyMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isLockedByMe) "Unlock this chat" else "Lock chat with password")
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isLockedByMe) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showPrivacyMenu = false
                                    onUpdatePrivacy(conversation.disappearingHours, true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Disappearing: Off") },
                                onClick = {
                                    showPrivacyMenu = false
                                    onUpdatePrivacy(0L, false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Disappearing: 24 Hours") },
                                onClick = {
                                    showPrivacyMenu = false
                                    onUpdatePrivacy(24L, false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Disappearing: 7 Days") },
                                onClick = {
                                    showPrivacyMenu = false
                                    onUpdatePrivacy(168L, false)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    AnimatedVisibility(visible = isSendingMedia) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            if (mediaStatusText.isNotBlank()) {
                                Text(
                                    text = mediaStatusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = otherTypers.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("peer_typing_mark")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Typing indicator",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${otherTypers.joinToString(", ")} is writing a message…",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (replyingTo != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${replyingTo!!.senderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = replyingTo!!.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingTo = null }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(QUICK_CHECKINS) { checkin ->
                            AssistChip(
                                onClick = { sendSimpleText(checkin, "checkin") },
                                label = { Text(checkin, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = true },
                                enabled = !isSendingMedia,
                                modifier = Modifier.testTag("chat_attach_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddCircleOutline,
                                    contentDescription = "Attach photo, video or location",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Photo from Gallery") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        galleryPhotoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("attach_gallery_photo")
                                )
                                DropdownMenuItem(
                                    text = { Text("Video from Gallery") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        galleryVideoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("attach_gallery_video")
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Current Location") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        if (hasLocationPermission(context)) {
                                            fetchAndSendLocation()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("attach_location")
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEmojiStickerTray = !showEmojiStickerTray },
                            modifier = Modifier.testTag("chat_emoji_sticker_button")
                        ) {
                            Icon(
                                imageVector = if (showEmojiStickerTray) Icons.Rounded.Keyboard else Icons.Rounded.EmojiEmotions,
                                contentDescription = "Toggle emojis and stickers",
                                tint = if (showEmojiStickerTray) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text(stringResource(R.string.message_hint)) },
                            trailingIcon = {
                                if (messageText.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .testTag("composer_text_mark")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Writing text mark",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Text",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_composer_input")
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = {
                                val trimmed = messageText.trim()
                                if (trimmed.isNotEmpty()) {
                                    sendSimpleText(trimmed, "text")
                                    messageText = ""
                                    showEmojiStickerTray = false
                                }
                            },
                            enabled = messageText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = showEmojiStickerTray) {
                        EmojiAndStickerTray(
                            tabIndex = trayTabIndex,
                            onTabSelected = { trayTabIndex = it },
                            selectedStickerCategory = selectedStickerCategory,
                            onStickerCategorySelected = { selectedStickerCategory = it },
                            onInsertEmoji = { emoji -> messageText += emoji },
                            onQuickSendEmoji = { emoji -> sendSimpleText(emoji, "text") },
                            onSendSticker = { stickerId, text ->
                                onSendMessage(
                                    text,
                                    "sticker",
                                    null, null, null, null,
                                    stickerId,
                                    null, null, null,
                                    replyingTo?.text,
                                    replyingTo?.senderName
                                )
                                replyingTo = null
                                showEmojiStickerTray = false
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (messagesState) {
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
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is UiState.Success -> {
                val messages = messagesState.data
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }

                // Mark incoming messages as Seen when viewed ("when you see the message, the scene will appear")
                LaunchedEffect(messages, readReceiptsEnabled) {
                    if (readReceiptsEnabled && messages.any {
                            it.id.isNotBlank() && it.senderId != currentUserId && !it.seenByUids.contains(currentUserId)
                        }
                    ) {
                        onMessagesViewed(messages)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            currentUserId = currentUserId,
                            isMine = msg.senderId == currentUserId,
                            onImageClick = { previewImageMessage = msg },
                            onVideoClick = {
                                if (msg.mediaBase64.isNotBlank()) {
                                    playingVideoMessage = msg
                                } else {
                                    val info = "Video clip exceeded 460 KB preview limit — thumbnail shown."
                                    onFeedbackMessage(info)
                                    Toast.makeText(context, info, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenLocation = { lat, lng, label ->
                                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                val opened = runCatching { context.startActivity(intent) }.isSuccess
                                if (!opened) {
                                    val webIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://maps.google.com/?q=$lat,$lng")
                                    )
                                    runCatching { context.startActivity(webIntent) }
                                }
                            },
                            onReply = { replyingTo = msg },
                            onReact = { emoji -> onReact(msg, emoji) },
                            onDelete = { onDeleteMessage(msg.id) }
                        )
                    }
                }
            }
        }
    }

    previewImageMessage?.let { imgMsg ->
        ImageLightboxDialog(
            message = imgMsg,
            onDismiss = { previewImageMessage = null }
        )
    }

    playingVideoMessage?.let { vidMsg ->
        VideoPlayerDialog(
            message = vidMsg,
            onDismiss = { playingVideoMessage = null }
        )
    }
}

@Composable
private fun EmojiAndStickerTray(
    tabIndex: Int,
    onTabSelected: (Int) -> Unit,
    selectedStickerCategory: String,
    onStickerCategorySelected: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onQuickSendEmoji: (String) -> Unit,
    onSendSticker: (stickerId: String, text: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { onTabSelected(0) },
                text = { Text("Emojis") },
                modifier = Modifier.testTag("tray_tab_emojis")
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { onTabSelected(1) },
                text = { Text("Stickers & Benefits") },
                modifier = Modifier.testTag("tray_tab_stickers")
            )
        }

        if (tabIndex == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Quick send:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    QUICK_EMOJIS.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clickable { onQuickSendEmoji(emoji) }
                                .testTag("quick_emoji_$emoji")
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(COMPOSER_EMOJIS) { emoji ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onInsertEmoji(emoji) }
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        } else {
            val categories = listOf("All") + FamilyStickerCatalog.categories
            val filteredStickers = remember(selectedStickerCategory) {
                if (selectedStickerCategory == "All") FamilyStickerCatalog.stickers
                else FamilyStickerCatalog.stickers.filter { it.category == selectedStickerCategory }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedStickerCategory == category,
                            onClick = { onStickerCategorySelected(category) },
                            label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStickers, key = { it.id }) { sticker ->
                        val cardColor = avatarColorFromHex(sticker.bgColorHex)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSendSticker(sticker.id, "${sticker.emoji} ${sticker.title}")
                                }
                                .testTag("sticker_item_${sticker.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = sticker.emoji, fontSize = 28.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sticker.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = sticker.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    currentUserId: String,
    isMine: Boolean,
    onImageClick: () -> Unit,
    onVideoClick: () -> Unit,
    onOpenLocation: (Double, Double, String) -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    if (message.messageType == "system") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val bubbleColor = when {
        message.messageType == "sticker" -> Color.Transparent
        message.messageType == "checkin" -> MaterialTheme.colorScheme.secondaryContainer
        isMine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        message.messageType == "sticker" -> MaterialTheme.colorScheme.onSurface
        message.messageType == "checkin" -> MaterialTheme.colorScheme.onSecondaryContainer
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val timeText = message.createdAt?.toDate()?.let {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
    }.orEmpty()
    val isSeenByPeer = isMine && message.seenByUids.any { it != currentUserId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isMine) 20.dp else 6.dp,
                    bottomEnd = if (isMine) 6.dp else 20.dp
                ),
                color = bubbleColor,
                tonalElevation = if (isMine || message.messageType == "sticker") 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .combinedClickable(
                        onClick = { showActions = true },
                        onLongClick = { showActions = true }
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!isMine) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (message.replyToText.isNotBlank()) {
                        Surface(
                            color = textColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                if (message.replyToSender.isNotBlank()) {
                                    Text(
                                        text = message.replyToSender,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.85f)
                                    )
                                }
                                Text(
                                    text = message.replyToText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    when (message.messageType) {
                        "image" -> {
                            val imgBitmap = remember(message.mediaBase64) {
                                ProfilePhotoUtils.decodeBase64ToImageBitmap(message.mediaBase64)
                            }
                            if (imgBitmap != null) {
                                Image(
                                    bitmap = imgBitmap,
                                    contentDescription = message.text.ifBlank { "Shared family photo" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 230.dp)
                                        .padding(top = 4.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(onClick = onImageClick)
                                        .testTag("chat_image_${message.id}")
                                )
                            }
                            if (message.text.isNotBlank() && message.text != "📷 Photo") {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        "video" -> {
                            val thumbBitmap = remember(message.thumbnailBase64) {
                                ProfilePhotoUtils.decodeBase64ToImageBitmap(message.thumbnailBase64)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(165.dp)
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF15201E))
                                    .clickable(onClick = onVideoClick)
                                    .testTag("chat_video_${message.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap,
                                        contentDescription = "Shared family video thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f))
                                )
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircleFilled,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (message.videoDurationMs > 0L) {
                                            ChatMediaUtils.formatDuration(message.videoDurationMs)
                                        } else "Video",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (message.text.isNotBlank() && message.text != "🎥 Video") {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        "sticker" -> {
                            val sticker = remember(message.stickerId) {
                                FamilyStickerCatalog.findById(message.stickerId)
                            }
                            val accent = if (sticker != null) {
                                avatarColorFromHex(sticker.bgColorHex)
                            } else MaterialTheme.colorScheme.primary
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = accent.copy(alpha = 0.16f),
                                tonalElevation = 1.dp,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = sticker?.emoji ?: "✨",
                                        fontSize = 44.sp
                                    )
                                    Text(
                                        text = sticker?.title ?: message.text,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    if (sticker != null) {
                                        Text(
                                            text = sticker.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        "location" -> {
                            val lat = message.latitude ?: 0.0
                            val lng = message.longitude ?: 0.0
                            val label = message.locationLabel.ifBlank {
                                String.format(Locale.US, "%.5f, %.5f", lat, lng)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isMine) Color.White.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isMine) Color.White.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocationOn,
                                                    contentDescription = "Location pin",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = textColor,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.5f, %.5f", lat, lng),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor.copy(alpha = 0.78f)
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenLocation(lat, lng, label) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_location_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Open in Maps",
                                            color = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (timeText.isNotEmpty()) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.72f)
                            )
                        }
                        if (isMine) {
                            val seenPillColor = if (message.messageType == "sticker") {
                                if (isSeenByPeer) OnlineDotGreen.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                if (isSeenByPeer) Color.White.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.1f)
                            }
                            val seenTextColor = if (message.messageType == "sticker") {
                                if (isSeenByPeer) OnlineDotGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                if (isSeenByPeer) Color(0xFFA7F3D0) else Color.White.copy(alpha = 0.78f)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = seenPillColor,
                                modifier = Modifier.testTag("seen_badge_${message.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSeenByPeer) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                        contentDescription = if (isSeenByPeer) "Message seen" else "Message sent",
                                        tint = seenTextColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isSeenByPeer) "Seen" else "Sent",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = seenTextColor
                                    )
                                }
                            }
                        }
                        if (message.reactionEmoji.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = message.reactionEmoji,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showActions,
                onDismissRequest = { showActions = false }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_EMOJIS.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clickable {
                                    showActions = false
                                    onReact(emoji)
                                }
                                .padding(4.dp)
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null) },
                    onClick = {
                        showActions = false
                        onReply()
                    }
                )
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            showActions = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageLightboxDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val bmp = remember(message.mediaBase64) {
        ProfilePhotoUtils.decodeBase64ToImageBitmap(message.mediaBase64)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.94f),
            modifier = Modifier
                .fillMaxSize()
                .testTag("image_lightbox_dialog")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = message.text.ifBlank { "Full photo preview" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
                }
                if (message.text.isNotBlank() && message.text != "📷 Photo") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tempFile by remember(message.id) { mutableStateOf<File?>(null) }

    LaunchedEffect(message.id, message.mediaBase64) {
        tempFile = ChatMediaUtils.decodeVideoBase64ToTempFile(context, message.id, message.mediaBase64)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier
                .fillMaxSize()
                .testTag("video_player_dialog")
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val file = tempFile
                if (file != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    start()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 520.dp)
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close video", tint = Color.White)
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}
