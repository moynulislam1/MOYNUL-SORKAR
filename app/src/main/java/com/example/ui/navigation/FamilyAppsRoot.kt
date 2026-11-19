package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.call.WebRtcCallManager
import com.example.data.firebase.CallAndProfileRepository
import com.example.data.firebase.FamilyRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutSafely
import com.example.data.local.DeviceSessionStore
import com.example.data.model.AppDestination
import com.example.data.model.UiState
import com.example.data.security.CryptoUtils
import com.example.data.security.ParsedQrAction
import com.example.ui.components.ActiveCallOverlayScreen
import com.example.ui.components.IncomingCallOverlay
import com.example.ui.components.MemberProfilePreviewSheet
import com.example.ui.components.MyProfileEditorSheet
import com.example.ui.components.PrivacyLockOverlay
import com.example.ui.components.QrScannerSheet
import com.example.ui.components.rememberCallPermissionStarter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.FamilyCircleScreen
import com.example.ui.screens.PrivacyProfileScreen
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun FamilyAppsRoot() {
    val context = LocalContext.current
    val sessionStore = remember(context) { DeviceSessionStore(context.applicationContext) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(
            sessionStore = sessionStore,
            onAuthSuccess = { /* AuthStateListener drives navigation cleanly */ }
        )
    } else {
        key(user.uid) {
            val viewModel: MainViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        MainViewModel(
                            repository = FamilyRepository(db, Firebase.auth),
                            callAndProfileRepository = CallAndProfileRepository(db, Firebase.auth),
                            sessionStore = DeviceSessionStore(app),
                            callManagerFactory = { onLocalIce, onStateChanged ->
                                WebRtcCallManager(app, onLocalIce, onStateChanged)
                            },
                            currentUserId = user.uid,
                            currentUserEmail = user.email.orEmpty(),
                            currentUserName = user.displayName.orEmpty(),
                            emailVerified = user.isEmailVerified
                        )
                    }
                }
            )
            AuthenticatedAppShell(viewModel = viewModel)
        }
    }
}

private data class PendingOutgoingCall(
    val calleeUid: String,
    val calleeName: String,
    val conversationId: String?
)

@Composable
private fun AuthenticatedAppShell(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val familiesState by viewModel.familiesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val savedContactsState by viewModel.savedContactsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val peerCardsByUid by viewModel.peerCardsByUid.collectAsStateWithLifecycle()
    val onlineStatusByUid by viewModel.onlineStatusByUid.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activePeerProfileCard by viewModel.activePeerProfileCard.collectAsStateWithLifecycle()
    val scannedProfileCard by viewModel.scannedProfileCard.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val unlockedChatIds by viewModel.unlockedChatIds.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    // Call State
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val activeCallManager by viewModel.activeCallManager.collectAsStateWithLifecycle()
    val webrtcState by viewModel.webrtcState.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val savedContacts = (savedContactsState as? UiState.Success)?.data.orEmpty()
    val activeConversation = conversations.firstOrNull { it.id == activeConversationId }

    var currentDestination by remember { mutableStateOf(AppDestination.CHATS) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showCreateFamilyDialog by remember { mutableStateOf(false) }
    var showMyProfileEditor by remember { mutableStateOf(false) }
    var pendingOutgoingCall by remember { mutableStateOf<PendingOutgoingCall?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val callPermissionStarter = rememberCallPermissionStarter(
        onPermissionsGranted = { callType ->
            val target = pendingOutgoingCall
            pendingOutgoingCall = null
            if (target != null) {
                viewModel.startCall(
                    calleeUid = target.calleeUid,
                    calleeName = target.calleeName,
                    conversationId = target.conversationId,
                    callType = callType
                )
            }
        },
        onPermissionDenied = {
            pendingOutgoingCall = null
            scope.launch {
                snackbarHostState.showSnackbar("Microphone & Camera permissions are needed for calls.")
            }
        }
    )

    fun initiateCall(calleeUid: String, calleeName: String, conversationId: String?, callType: String) {
        pendingOutgoingCall = PendingOutgoingCall(calleeUid, calleeName, conversationId)
        callPermissionStarter(callType)
    }

    // Track app foreground/background for live green online sign
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setAppForegroundOnline(true)
                Lifecycle.Event.ON_STOP -> viewModel.setAppForegroundOnline(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 840.dp
            val useRail = maxWidth >= 600.dp

            if (activeConversation != null && !isWideScreen) {
                ChatDetailScreen(
                    conversation = activeConversation,
                    currentUserId = viewModel.currentUserId,
                    isChatUnlocked = unlockedChatIds.contains(activeConversation.id),
                    readReceiptsEnabled = profile?.readReceiptsEnabled ?: true,
                    messagesState = messagesState,
                    peerProfileCard = activePeerProfileCard,
                    onUnlockChat = { pwd -> viewModel.verifyAndUnlockChat(activeConversation.id, pwd) },
                    onSendMessage = { text, type, mediaBase64, thumbnailBase64, mediaMimeType, videoDurationMs, stickerId, latitude, longitude, locationLabel, replyText, replySender ->
                        viewModel.sendMessage(
                            conversation = activeConversation,
                            text = text,
                            messageType = type,
                            mediaBase64 = mediaBase64,
                            thumbnailBase64 = thumbnailBase64,
                            mediaMimeType = mediaMimeType,
                            videoDurationMs = videoDurationMs,
                            stickerId = stickerId,
                            latitude = latitude,
                            longitude = longitude,
                            locationLabel = locationLabel,
                            replyToText = replyText,
                            replyToSender = replySender
                        )
                    },
                    onMessagesViewed = { msgs ->
                        viewModel.markConversationMessagesSeen(activeConversation.id, msgs)
                    },
                    onTypingChanged = { isTyping ->
                        viewModel.updateTypingStatus(activeConversation, isTyping)
                    },
                    onFeedbackMessage = { msg -> viewModel.reportFeedback(msg) },
                    onReact = { msg, emoji -> viewModel.toggleReaction(activeConversation.id, msg, emoji) },
                    onDeleteMessage = { msgId -> viewModel.deleteMessage(activeConversation.id, msgId) },
                    onUpdatePrivacy = { hours, toggleLock ->
                        viewModel.updateConversationPrivacy(activeConversation, hours, toggleLock)
                    },
                    onPeerHeaderClick = { peerUid -> viewModel.openPeerProfileByUid(peerUid) },
                    onStartAudioCall = { calleeUid, calleeName ->
                        initiateCall(calleeUid, calleeName, activeConversation.id, "audio")
                    },
                    onStartVideoCall = { calleeUid, calleeName ->
                        initiateCall(calleeUid, calleeName, activeConversation.id, "video")
                    },
                    onBack = { viewModel.selectConversation(null) }
                )
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar {
                                AppDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = destinationIcon(dest, selected),
                                                contentDescription = destinationLabel(dest)
                                            )
                                        },
                                        label = { Text(destinationLabel(dest)) },
                                        modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail {
                                AppDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = destinationIcon(dest, selected),
                                                contentDescription = destinationLabel(dest)
                                            )
                                        },
                                        label = { Text(destinationLabel(dest)) },
                                        modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                                    )
                                }
                            }
                            VerticalDivider()
                        }

                        val listPaneModifier = if (isWideScreen && currentDestination == AppDestination.CHATS) {
                            Modifier.weight(0.42f).fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        }

                        Box(modifier = listPaneModifier) {
                            when (currentDestination) {
                                AppDestination.CHATS -> ChatsListScreen(
                                    currentUserId = viewModel.currentUserId,
                                    profile = profile,
                                    conversationsState = conversationsState,
                                    familyMembers = familyMembers,
                                    savedContacts = savedContacts,
                                    peerCardsByUid = peerCardsByUid,
                                    onlineStatusByUid = onlineStatusByUid,
                                    onConversationClick = { conv -> viewModel.selectConversation(conv.id) },
                                    onMemberClick = { member ->
                                        if (member.userId == viewModel.currentUserId) {
                                            showMyProfileEditor = true
                                        } else {
                                            viewModel.openMemberProfile(member)
                                        }
                                    },
                                    onSavedContactClick = { contact ->
                                        viewModel.openContactProfile(contact)
                                    },
                                    onOpenMyProfile = { showMyProfileEditor = true },
                                    onOpenScanner = { showQrScanner = true },
                                    onCreateFamilyClick = { showCreateFamilyDialog = true },
                                    onTogglePrivacyPeek = { hide ->
                                        viewModel.updatePrivacySettings(
                                            privacyLockEnabled = profile?.privacyLockEnabled ?: false,
                                            hideMessagePreviews = hide,
                                            readReceiptsEnabled = profile?.readReceiptsEnabled ?: true,
                                            showOnlineStatus = profile?.showOnlineStatus ?: true
                                        )
                                    },
                                    onLockAppClick = { viewModel.lockAppNow() }
                                )

                                AppDestination.FAMILY -> FamilyCircleScreen(
                                    currentUserId = viewModel.currentUserId,
                                    profile = profile,
                                    familiesState = familiesState,
                                    members = familyMembers,
                                    onlineStatusByUid = onlineStatusByUid,
                                    onCreateFamily = { name -> viewModel.createFamilyCircle(name) },
                                    onJoinFamilyCode = { code -> viewModel.joinFamilyByCode(code) },
                                    onOpenScanner = { showQrScanner = true },
                                    onOpenMyProfile = { showMyProfileEditor = true },
                                    onInspectMember = { member -> viewModel.openMemberProfile(member) },
                                    onUpdateStatus = { familyId, status ->
                                        viewModel.updateMyStatus(familyId, status)
                                    },
                                    onMessageMember = { member ->
                                        viewModel.openOrStartDirectChat(member) {
                                            currentDestination = AppDestination.CHATS
                                        }
                                    },
                                    onAudioCallMember = { member ->
                                        initiateCall(member.userId, member.displayName, null, "audio")
                                    },
                                    onVideoCallMember = { member ->
                                        initiateCall(member.userId, member.displayName, null, "video")
                                    }
                                )

                                AppDestination.PRIVACY -> PrivacyProfileScreen(
                                    profile = profile,
                                    isBusy = isBusy,
                                    onOpenMyProfileEditor = { showMyProfileEditor = true },
                                    onSaveCredentials = { name, role, phone, pwd ->
                                        viewModel.saveCredentialsAndProfile(name, role, phone, pwd)
                                    },
                                    onUpdatePrivacy = { lock, hide, receipts, online ->
                                        viewModel.updatePrivacySettings(lock, hide, receipts, online)
                                    },
                                    onLockNow = { viewModel.lockAppNow() },
                                    onSignOut = {
                                        viewModel.setAppForegroundOnline(false)
                                        signOutSafely(credentialManager, scope)
                                    }
                                )
                            }
                        }

                        if (isWideScreen && currentDestination == AppDestination.CHATS) {
                            VerticalDivider()
                            Box(
                                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeConversation != null) {
                                    ChatDetailScreen(
                                        conversation = activeConversation,
                                        currentUserId = viewModel.currentUserId,
                                        isChatUnlocked = unlockedChatIds.contains(activeConversation.id),
                                        readReceiptsEnabled = profile?.readReceiptsEnabled ?: true,
                                        messagesState = messagesState,
                                        peerProfileCard = activePeerProfileCard,
                                        onUnlockChat = { pwd -> viewModel.verifyAndUnlockChat(activeConversation.id, pwd) },
                                        onSendMessage = { text, type, mediaBase64, thumbnailBase64, mediaMimeType, videoDurationMs, stickerId, latitude, longitude, locationLabel, replyText, replySender ->
                                            viewModel.sendMessage(
                                                conversation = activeConversation,
                                                text = text,
                                                messageType = type,
                                                mediaBase64 = mediaBase64,
                                                thumbnailBase64 = thumbnailBase64,
                                                mediaMimeType = mediaMimeType,
                                                videoDurationMs = videoDurationMs,
                                                stickerId = stickerId,
                                                latitude = latitude,
                                                longitude = longitude,
                                                locationLabel = locationLabel,
                                                replyToText = replyText,
                                                replyToSender = replySender
                                            )
                                        },
                                        onMessagesViewed = { msgs ->
                                            viewModel.markConversationMessagesSeen(activeConversation.id, msgs)
                                        },
                                        onTypingChanged = { isTyping ->
                                            viewModel.updateTypingStatus(activeConversation, isTyping)
                                        },
                                        onFeedbackMessage = { msg -> viewModel.reportFeedback(msg) },
                                        onReact = { msg, emoji -> viewModel.toggleReaction(activeConversation.id, msg, emoji) },
                                        onDeleteMessage = { msgId -> viewModel.deleteMessage(activeConversation.id, msgId) },
                                        onUpdatePrivacy = { hours, toggleLock ->
                                            viewModel.updateConversationPrivacy(activeConversation, hours, toggleLock)
                                        },
                                        onPeerHeaderClick = { peerUid -> viewModel.openPeerProfileByUid(peerUid) },
                                        onStartAudioCall = { calleeUid, calleeName ->
                                            initiateCall(calleeUid, calleeName, activeConversation.id, "audio")
                                        },
                                        onStartVideoCall = { calleeUid, calleeName ->
                                            initiateCall(calleeUid, calleeName, activeConversation.id, "video")
                                        },
                                        onBack = { viewModel.selectConversation(null) }
                                    )
                                } else {
                                    Text(
                                        text = "Select a family conversation to start messaging privately",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isAppLocked) {
            PrivacyLockOverlay(
                title = stringResource(R.string.privacy_lock_title),
                subtitle = "Enter your Family Phone Number and Privacy Password to unlock your chats.",
                requirePhoneVerification = true,
                registeredPhoneHint = CryptoUtils.maskPhone(profile?.phoneNumber.orEmpty()),
                onUnlock = { phone, pwd -> viewModel.verifyAndUnlockApp(phone, pwd) }
            )
        }

        // Active Audio/Video Call Overlay (above app shell)
        val currentActiveCall = activeCall
        if (currentActiveCall != null) {
            ActiveCallOverlayScreen(
                call = currentActiveCall,
                currentUserId = viewModel.currentUserId,
                callManager = activeCallManager,
                webrtcState = webrtcState,
                isMicMuted = isMicMuted,
                isSpeakerOn = isSpeakerOn,
                isVideoEnabled = isVideoEnabled,
                onToggleMute = { viewModel.toggleMuteMic() },
                onToggleSpeaker = { viewModel.toggleSpeakerphone() },
                onToggleVideo = { viewModel.toggleCallVideo() },
                onSwitchCamera = { viewModel.switchCallCamera() },
                onEndCall = { viewModel.endActiveCall() }
            )
        }

        // Incoming Call Ringing Overlay
        val currentIncoming = incomingCall
        if (currentIncoming != null && currentActiveCall == null) {
            IncomingCallOverlay(
                call = currentIncoming,
                onAccept = { viewModel.acceptIncomingCall(it) },
                onDecline = { viewModel.declineIncomingCall(it) },
                onPermissionDenied = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Microphone/Camera permission is required to accept the call.")
                    }
                }
            )
        }
    }

    if (showQrScanner) {
        QrScannerSheet(
            title = "Scan QR or Add by ID",
            subtitle = "Scan a Member Profile QR, type their Profile Code (MEM...) or User ID to add them to your chat list, or scan a Family Invite QR (FAM...).",
            onQrScanned = { raw ->
                showQrScanner = false
                when (val parsed = CryptoUtils.parseQrPayload(raw)) {
                    is ParsedQrAction.JoinInvite -> viewModel.joinFamilyByCode(parsed.inviteCode)
                    is ParsedQrAction.ViewProfile -> viewModel.lookupProfileFromQr(parsed.uid, parsed.profileCode)
                    null -> viewModel.joinFamilyByCode(raw)
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (scannedProfileCard != null) {
        val card = scannedProfileCard!!
        MemberProfilePreviewSheet(
            card = card,
            isSelf = card.userId == viewModel.currentUserId,
            onDismiss = { viewModel.clearScannedProfile() },
            onEditOwnProfile = { showMyProfileEditor = true },
            onStartChat = { targetCard ->
                viewModel.openOrStartDirectChatFromCard(targetCard) {
                    currentDestination = AppDestination.CHATS
                }
            },
            onStartAudioCall = { targetCard ->
                initiateCall(targetCard.userId, targetCard.displayName, null, "audio")
            },
            onStartVideoCall = { targetCard ->
                initiateCall(targetCard.userId, targetCard.displayName, null, "video")
            }
        )
    }

    if (showMyProfileEditor) {
        MyProfileEditorSheet(
            profile = profile,
            currentUserId = viewModel.currentUserId,
            isBusy = isBusy,
            onDismiss = { showMyProfileEditor = false },
            onSaveProfile = { name, role, bio, mood, badge, colorHex, photoBase64 ->
                viewModel.updateBioAndMood(name, role, bio, mood, badge, colorHex, photoBase64)
            }
        )
    }

    if (showCreateFamilyDialog) {
        var familyName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFamilyDialog = false },
            title = { Text("New Family Circle") },
            text = {
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("Family Name (e.g. Home Circle)") },
                    singleLine = true,
                    modifier = Modifier.testTag("quick_create_family_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (familyName.isNotBlank()) {
                            viewModel.createFamilyCircle(familyName.trim())
                            showCreateFamilyDialog = false
                        }
                    },
                    enabled = familyName.isNotBlank(),
                    modifier = Modifier.testTag("quick_create_family_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFamilyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun destinationLabel(dest: AppDestination): String = when (dest) {
    AppDestination.CHATS -> stringResource(R.string.nav_chats)
    AppDestination.FAMILY -> stringResource(R.string.nav_family)
    AppDestination.PRIVACY -> stringResource(R.string.nav_privacy)
}

private fun destinationIcon(dest: AppDestination, selected: Boolean) = when (dest) {
    AppDestination.CHATS -> if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
    AppDestination.FAMILY -> if (selected) Icons.Rounded.Groups else Icons.Outlined.Groups
    AppDestination.PRIVACY -> if (selected) Icons.Rounded.Shield else Icons.Outlined.Shield
}
