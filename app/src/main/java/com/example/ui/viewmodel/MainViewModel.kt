package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.call.WebRtcCallManager
import com.example.data.firebase.CallAndProfileRepository
import com.example.data.firebase.FamilyRepository
import com.example.data.local.DeviceSessionStore
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FamilyCircle
import com.example.data.model.FamilyMember
import com.example.data.model.ProfileCard
import com.example.data.model.SavedContact
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.security.CryptoUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

class MainViewModel(
    private val repository: FamilyRepository,
    private val callAndProfileRepository: CallAndProfileRepository,
    private val sessionStore: DeviceSessionStore,
    private val callManagerFactory: (
        onLocalIce: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
        onStateChanged: (PeerConnection.PeerConnectionState) -> Unit
    ) -> WebRtcCallManager,
    val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String,
    private val emailVerified: Boolean
) : ViewModel() {

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _unlockedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedChatIds: StateFlow<Set<String>> = _unlockedChatIds.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _scannedProfileCard = MutableStateFlow<ProfileCard?>(null)
    val scannedProfileCard: StateFlow<ProfileCard?> = _scannedProfileCard.asStateFlow()

    private val _directPeerOnlineMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _peerCardsByUid = MutableStateFlow<Map<String, ProfileCard>>(emptyMap())
    val peerCardsByUid: StateFlow<Map<String, ProfileCard>> = _peerCardsByUid.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // WebRTC Call State
    private val _incomingCall = MutableStateFlow<CallSession?>(null)
    val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _activeCallManager = MutableStateFlow<WebRtcCallManager?>(null)
    val activeCallManager: StateFlow<WebRtcCallManager?> = _activeCallManager.asStateFlow()

    private val _webrtcState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val webrtcState: StateFlow<PeerConnection.PeerConnectionState> = _webrtcState.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private var lockCheckedOnce = false
    private var profileCardInitialized = false
    private var callObserverJob: Job? = null
    private var iceObserverJob: Job? = null
    private var directPeerObserverJob: Job? = null
    private var savedContactCardsJob: Job? = null

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { profile ->
                if (profile != null) {
                    if (!lockCheckedOnce) {
                        lockCheckedOnce = true
                        if (profile.privacyLockEnabled && profile.hasPassword) {
                            _isAppLocked.value = true
                        }
                        syncDeviceSessionCache(profile)
                    }
                    if (!profileCardInitialized) {
                        profileCardInitialized = true
                        ensureProfileCard(profile)
                    }
                }
                UiState.Success(profile)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val familiesState: StateFlow<UiState<List<FamilyCircle>>> =
        repository.observeFamilies(currentUserId)
            .map<List<FamilyCircle>, UiState<List<FamilyCircle>>> { families ->
                refreshMembersForFamilies(families)
                UiState.Success(families)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing families", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load family circles"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { convs ->
                refreshDirectPeerPresence(convs)
                UiState.Success(convs)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load messages"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val savedContactsState: StateFlow<UiState<List<SavedContact>>> =
        callAndProfileRepository.observeSavedContacts(currentUserId)
            .map<List<SavedContact>, UiState<List<SavedContact>>> { contacts ->
                refreshPeerCardsForContacts(contacts)
                UiState.Success(contacts)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing saved contacts", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load saved contacts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val onlineStatusByUid: StateFlow<Map<String, Boolean>> =
        combine(familyMembers, _directPeerOnlineMap, _peerCardsByUid, userProfileState) { members, directMap, peerCards, profState ->
            val combined = mutableMapOf<String, Boolean>()
            directMap.forEach { (uid, online) -> combined[uid] = online }
            peerCards.forEach { (uid, card) ->
                combined[uid] = card.onlineActive && card.showOnlineStatus
            }
            members.forEach { member ->
                if (member.onlineActive) {
                    combined[member.userId] = true
                } else if (!combined.containsKey(member.userId)) {
                    combined[member.userId] = false
                }
            }
            val me = (profState as? UiState.Success)?.data
            if (me != null) {
                combined[currentUserId] = me.showOnlineStatus
            }
            combined
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeConversationId
            .flatMapLatest { convId ->
                if (convId.isNullOrBlank()) {
                    flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeMessages(convId, currentUserId)
                        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = unwrapFirestoreError(error)
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing messages", rootError)
                            emit(UiState.Error(rootError.message ?: "Failed to load conversation"))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activePeerProfileCard: StateFlow<ProfileCard?> =
        combine(_activeConversationId, conversationsState) { convId, convState ->
            val convs = (convState as? UiState.Success)?.data.orEmpty()
            val active = convs.firstOrNull { it.id == convId }
            if (active != null && active.type == "direct") {
                active.memberUids.firstOrNull { it != currentUserId }
            } else null
        }.flatMapLatest { peerUid ->
            if (peerUid.isNullOrBlank()) {
                flowOf(null)
            } else {
                callAndProfileRepository.observeProfileCard(peerUid, currentUserId)
                    .catch { emit(null) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            val local = sessionStore.getSnapshot()
            if (local.appLockEnabled && local.pairedUid == currentUserId && local.passwordHash.isNotBlank()) {
                _isAppLocked.value = true
                lockCheckedOnce = true
            }

            val profileResult = repository.ensureUserProfile(
                uid = currentUserId,
                email = currentUserEmail,
                displayName = currentUserName,
                emailVerified = emailVerified
            )
            profileResult.onSuccess { profile ->
                syncDeviceSessionCache(profile)
                ensureProfileCard(profile)
                setAppForegroundOnline(true, profile)
            }

            val pendingCode = sessionStore.consumePendingInviteCode()
            if (pendingCode.isNotBlank()) {
                val p = profileResult.getOrNull()
                joinFamilyByCode(
                    code = pendingCode,
                    displayName = p?.displayName ?: currentUserName,
                    role = p?.familyRole ?: "Member",
                    phone = p?.phoneNumber ?: ""
                )
            }
        }

        // Listen for incoming audio/video calls
        viewModelScope.launch {
            callAndProfileRepository.observeActiveCalls(currentUserId)
                .catch { err -> Log.w(TAG, "Call observer error", err) }
                .collect { sessions ->
                    val currentActiveId = _activeCall.value?.id
                    val ringingForMe = sessions.firstOrNull {
                        it.status == "ringing" &&
                            it.calleeId == currentUserId &&
                            it.id != currentActiveId
                    }
                    if (ringingForMe != null && currentActiveId == null) {
                        _incomingCall.value = ringingForMe
                    } else if (_incomingCall.value != null &&
                        sessions.none { it.id == _incomingCall.value?.id && it.status == "ringing" }
                    ) {
                        _incomingCall.value = null
                    }
                }
        }
    }

    private fun ensureProfileCard(profile: UserProfile) {
        viewModelScope.launch {
            callAndProfileRepository.ensureProfileCardAndCode(
                uid = profile.userId.ifBlank { currentUserId },
                displayName = profile.displayName,
                familyRole = profile.familyRole,
                existingCode = profile.profileCode,
                bio = profile.bio,
                moodTag = profile.moodTag,
                avatarBadge = profile.avatarBadge,
                avatarColorHex = profile.avatarColorHex,
                avatarPhotoBase64 = profile.avatarPhotoBase64,
                showOnlineStatus = profile.showOnlineStatus
            )
        }
    }

    fun setAppForegroundOnline(online: Boolean, overrideProfile: UserProfile? = null) {
        viewModelScope.launch {
            val profile = overrideProfile ?: (userProfileState.value as? UiState.Success)?.data
            val showOnline = profile?.showOnlineStatus ?: true
            val familyIds = (familiesState.value as? UiState.Success)?.data?.map { it.id }.orEmpty()
            callAndProfileRepository.setOnlinePresence(
                uid = currentUserId,
                online = online,
                showOnlineStatus = showOnline,
                familyIds = familyIds
            )
        }
    }

    private fun refreshMembersForFamilies(families: List<FamilyCircle>) {
        viewModelScope.launch {
            val combined = mutableListOf<FamilyMember>()
            families.forEach { family ->
                repository.fetchFamilyMembersByUids(family.id, family.memberUids)
                    .onSuccess { members -> combined.addAll(members) }
            }
            _familyMembers.value = combined.distinctBy { "${it.familyId}_${it.userId}" }
        }
    }

    private fun refreshDirectPeerPresence(conversations: List<Conversation>) {
        val peerUids = conversations
            .filter { it.type == "direct" }
            .mapNotNull { conv -> conv.memberUids.firstOrNull { it != currentUserId } }
            .distinct()
            .take(20)
        if (peerUids.isEmpty()) return

        directPeerObserverJob?.cancel()
        directPeerObserverJob = viewModelScope.launch {
            val onlineMap = _directPeerOnlineMap.value.toMutableMap()
            val cardsMap = _peerCardsByUid.value.toMutableMap()
            peerUids.forEach { peerUid ->
                callAndProfileRepository.lookupProfileByQrOrCode(targetUid = peerUid, profileCode = null)
                    .onSuccess { card ->
                        onlineMap[peerUid] = card.onlineActive && card.showOnlineStatus
                        cardsMap[peerUid] = card
                    }
            }
            _directPeerOnlineMap.value = onlineMap
            _peerCardsByUid.value = cardsMap
        }
    }

    private fun refreshPeerCardsForContacts(contacts: List<SavedContact>) {
        val peerUids = contacts
            .mapNotNull { it.peerUid.ifBlank { it.id }.takeIf(String::isNotBlank) }
            .distinct()
            .take(20)
        if (peerUids.isEmpty()) return

        savedContactCardsJob?.cancel()
        savedContactCardsJob = viewModelScope.launch {
            val onlineMap = _directPeerOnlineMap.value.toMutableMap()
            val cardsMap = _peerCardsByUid.value.toMutableMap()
            peerUids.forEach { peerUid ->
                callAndProfileRepository.lookupProfileByQrOrCode(targetUid = peerUid, profileCode = null)
                    .onSuccess { card ->
                        onlineMap[peerUid] = card.onlineActive && card.showOnlineStatus
                        cardsMap[peerUid] = card
                    }
            }
            _directPeerOnlineMap.value = onlineMap
            _peerCardsByUid.value = cardsMap
        }
    }

    private fun syncDeviceSessionCache(profile: UserProfile) {
        viewModelScope.launch {
            sessionStore.savePairedCredentials(
                uid = profile.userId,
                email = profile.email,
                phone = profile.phoneNumber,
                passwordHash = profile.passwordHash,
                passwordSalt = profile.passwordSalt,
                appLockEnabled = profile.privacyLockEnabled
            )
        }
    }

    // --- Profile QR Scanning, Bio & Mood ("Air") ---

    fun lookupProfileFromQr(uid: String, profileCode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = callAndProfileRepository.lookupProfileByQrOrCode(
                targetUid = uid.takeIf { it.isNotBlank() },
                profileCode = profileCode.takeIf { it.isNotBlank() }
            )
            result.onSuccess { card ->
                val resolvedUid = card.userId.ifBlank { card.id }
                _peerCardsByUid.value = _peerCardsByUid.value + (resolvedUid to card)
                _directPeerOnlineMap.value = _directPeerOnlineMap.value +
                    (resolvedUid to (card.onlineActive && card.showOnlineStatus))

                if (resolvedUid.isNotBlank() && resolvedUid != currentUserId) {
                    callAndProfileRepository.saveContactCard(currentUserId, card)
                    val myName = (userProfileState.value as? UiState.Success)?.data?.displayName ?: currentUserName
                    val chatResult = callAndProfileRepository.createOrOpenDirectChatFromCard(
                        uid = currentUserId,
                        myName = myName,
                        peerCard = card
                    )
                    chatResult.onSuccess {
                        _feedbackMessage.value = "Added ${card.displayName} to your chat list"
                    }.onFailure { err ->
                        _feedbackMessage.value = err.localizedMessage ?: "Saved contact, but could not open chat"
                    }
                }
                _scannedProfileCard.value = card
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Profile QR or ID not found"
            }
            _isBusy.value = false
        }
    }

    fun openMemberProfile(member: FamilyMember) {
        _scannedProfileCard.value = ProfileCard(
            id = member.userId,
            userId = member.userId,
            profileCode = member.profileCode,
            displayName = member.displayName,
            familyRole = member.familyRole,
            bio = member.bio,
            moodTag = member.moodTag,
            avatarBadge = member.avatarBadge,
            avatarColorHex = member.avatarColorHex,
            avatarPhotoBase64 = member.avatarPhotoBase64,
            onlineActive = member.onlineActive,
            showOnlineStatus = true,
            lastSeenAt = member.lastSeenAt
        )
        viewModelScope.launch {
            callAndProfileRepository.lookupProfileByQrOrCode(targetUid = member.userId, profileCode = null)
                .onSuccess { freshCard ->
                    _scannedProfileCard.value = freshCard
                    _peerCardsByUid.value = _peerCardsByUid.value + (member.userId to freshCard)
                }
        }
    }

    fun openContactProfile(contact: SavedContact) {
        val peerUid = contact.peerUid.ifBlank { contact.id }
        val cached = _peerCardsByUid.value[peerUid]
        _scannedProfileCard.value = cached ?: ProfileCard(
            id = peerUid,
            userId = peerUid,
            profileCode = contact.profileCode,
            displayName = contact.displayName,
            familyRole = contact.familyRole,
            bio = contact.bio,
            moodTag = contact.moodTag,
            avatarBadge = contact.avatarBadge,
            avatarColorHex = contact.avatarColorHex,
            avatarPhotoBase64 = contact.avatarPhotoBase64,
            onlineActive = onlineStatusByUid.value[peerUid] ?: false,
            showOnlineStatus = true
        )
        viewModelScope.launch {
            callAndProfileRepository.lookupProfileByQrOrCode(targetUid = peerUid, profileCode = null)
                .onSuccess { freshCard ->
                    _scannedProfileCard.value = freshCard
                    _peerCardsByUid.value = _peerCardsByUid.value + (peerUid to freshCard)
                    _directPeerOnlineMap.value = _directPeerOnlineMap.value +
                        (peerUid to (freshCard.onlineActive && freshCard.showOnlineStatus))
                }
        }
    }

    fun openPeerProfileByUid(peerUid: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = callAndProfileRepository.lookupProfileByQrOrCode(targetUid = peerUid, profileCode = null)
            _isBusy.value = false
            result.onSuccess { card ->
                _scannedProfileCard.value = card
                _peerCardsByUid.value = _peerCardsByUid.value + (peerUid to card)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not load member profile"
            }
        }
    }

    fun clearScannedProfile() {
        _scannedProfileCard.value = null
    }

    fun updateBioAndMood(
        displayName: String,
        familyRole: String,
        bio: String,
        moodTag: String,
        avatarBadge: String,
        avatarColorHex: String,
        avatarPhotoBase64: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            if (profile != null) {
                callAndProfileRepository.ensureProfileCardAndCode(
                    uid = currentUserId,
                    displayName = displayName,
                    familyRole = familyRole,
                    existingCode = profile.profileCode,
                    bio = bio,
                    moodTag = moodTag,
                    avatarBadge = avatarBadge,
                    avatarColorHex = avatarColorHex,
                    avatarPhotoBase64 = avatarPhotoBase64,
                    showOnlineStatus = profile.showOnlineStatus
                )
            }
            val familyIds = (familiesState.value as? UiState.Success)?.data?.map { it.id }.orEmpty()
            val result = callAndProfileRepository.updateProfileBioAndMood(
                uid = currentUserId,
                displayName = displayName,
                familyRole = familyRole,
                bio = bio,
                moodTag = moodTag,
                avatarBadge = avatarBadge,
                avatarColorHex = avatarColorHex,
                avatarPhotoBase64 = avatarPhotoBase64,
                familyIds = familyIds
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Profile, photo & vibe updated"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    // --- Navigation & Privacy Lock ---

    fun selectConversation(conversationId: String?) {
        val prevId = _activeConversationId.value
        if (prevId != null && prevId != conversationId) {
            val prevConv = (conversationsState.value as? UiState.Success)?.data?.firstOrNull { it.id == prevId }
            if (prevConv != null && prevConv.typingUids.contains(currentUserId)) {
                updateTypingStatus(prevConv, false)
            }
        }
        _activeConversationId.value = conversationId
    }

    fun lockAppNow() {
        _isAppLocked.value = true
        _unlockedChatIds.value = emptySet()
    }

    fun verifyAndUnlockApp(phoneInput: String?, passwordInput: String): Boolean {
        val profile = (userProfileState.value as? UiState.Success)?.data
        if (profile == null || !profile.hasPassword) {
            _isAppLocked.value = false
            return true
        }
        val normalizedInput = phoneInput?.let { CryptoUtils.normalizePhone(it) }.orEmpty()
        if (normalizedInput.isNotBlank() && profile.phoneNumber.isNotBlank() && normalizedInput != profile.phoneNumber) {
            _feedbackMessage.value = "Phone number does not match your registered Family Number."
            return false
        }
        val valid = CryptoUtils.verifyPassword(passwordInput, profile.passwordSalt, profile.passwordHash)
        if (valid) {
            _isAppLocked.value = false
            return true
        }
        _feedbackMessage.value = "Incorrect password. Please try again."
        return false
    }

    fun verifyAndUnlockChat(conversationId: String, passwordInput: String): Boolean {
        val profile = (userProfileState.value as? UiState.Success)?.data
        if (profile == null || !profile.hasPassword) {
            _unlockedChatIds.value = _unlockedChatIds.value + conversationId
            return true
        }
        val valid = CryptoUtils.verifyPassword(passwordInput, profile.passwordSalt, profile.passwordHash)
        if (valid) {
            _unlockedChatIds.value = _unlockedChatIds.value + conversationId
            return true
        }
        _feedbackMessage.value = "Incorrect privacy password for locked chat."
        return false
    }

    fun saveCredentialsAndProfile(
        displayName: String,
        familyRole: String,
        phoneNumber: String,
        newPassword: String?
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateUserCredentialsAndRole(
                uid = currentUserId,
                displayName = displayName,
                familyRole = familyRole,
                phoneNumber = phoneNumber,
                newPassword = newPassword
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Profile & login credentials saved"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    fun updatePrivacySettings(
        privacyLockEnabled: Boolean,
        hideMessagePreviews: Boolean,
        readReceiptsEnabled: Boolean,
        showOnlineStatus: Boolean
    ) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            if (privacyLockEnabled && profile?.hasPassword != true) {
                _feedbackMessage.value = "Set a login password first before enabling App Privacy Lock."
                return@launch
            }
            val result = repository.updatePrivacySettings(
                uid = currentUserId,
                privacyLockEnabled = privacyLockEnabled,
                hideMessagePreviews = hideMessagePreviews,
                readReceiptsEnabled = readReceiptsEnabled,
                showOnlineStatus = showOnlineStatus
            )
            result.onSuccess {
                val familyIds = (familiesState.value as? UiState.Success)?.data?.map { it.id }.orEmpty()
                callAndProfileRepository.setOnlinePresence(
                    uid = currentUserId,
                    online = true,
                    showOnlineStatus = showOnlineStatus,
                    familyIds = familyIds
                )
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to update privacy settings"
            }
        }
    }

    fun createFamilyCircle(familyName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val result = repository.createFamilyCircle(
                uid = currentUserId,
                displayName = profile?.displayName ?: currentUserName,
                familyRole = profile?.familyRole ?: "Parent",
                phoneNumber = profile?.phoneNumber.orEmpty(),
                familyName = familyName,
                profileCode = profile?.profileCode.orEmpty(),
                bio = profile?.bio.orEmpty(),
                moodTag = profile?.moodTag ?: "✨ Available",
                avatarBadge = profile?.avatarBadge ?: "🏡",
                avatarColorHex = profile?.avatarColorHex ?: "#0F5C4D",
                avatarPhotoBase64 = profile?.avatarPhotoBase64.orEmpty(),
                showOnlineStatus = profile?.showOnlineStatus ?: true
            )
            _isBusy.value = false
            result.onSuccess { fam ->
                _feedbackMessage.value = "Created ${fam.name} (${fam.inviteCode})"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to create family"
            }
        }
    }

    fun joinFamilyByCode(
        code: String,
        displayName: String? = null,
        role: String? = null,
        phone: String? = null
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val result = repository.joinFamilyByInviteCode(
                uid = currentUserId,
                displayName = displayName ?: profile?.displayName ?: currentUserName,
                familyRole = role ?: profile?.familyRole ?: "Member",
                phoneNumber = phone ?: profile?.phoneNumber.orEmpty(),
                rawCode = code,
                profileCode = profile?.profileCode.orEmpty(),
                bio = profile?.bio.orEmpty(),
                moodTag = profile?.moodTag ?: "✨ Available",
                avatarBadge = profile?.avatarBadge ?: "🏡",
                avatarColorHex = profile?.avatarColorHex ?: "#D96B43",
                avatarPhotoBase64 = profile?.avatarPhotoBase64.orEmpty(),
                showOnlineStatus = profile?.showOnlineStatus ?: true
            )
            _isBusy.value = false
            result.onSuccess { familyName ->
                _feedbackMessage.value = "Joined $familyName!"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not join family"
            }
        }
    }

    fun updateMyStatus(familyId: String, statusText: String) {
        viewModelScope.launch {
            repository.updateMyMemberStatus(familyId, currentUserId, statusText)
                .onSuccess { _feedbackMessage.value = "Status updated: $statusText" }
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Status update failed" }
        }
    }

    fun openOrStartDirectChat(peer: FamilyMember, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val myName = profile?.displayName ?: currentUserName
            val result = repository.createOrOpenDirectChat(currentUserId, myName, peer)
            _isBusy.value = false
            result.onSuccess { conv ->
                _activeConversationId.value = conv.id
                onOpened(conv.id)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Unable to open chat"
            }
        }
    }

    fun openOrStartDirectChatFromCard(peerCard: ProfileCard, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val myName = profile?.displayName ?: currentUserName
            val result = callAndProfileRepository.createOrOpenDirectChatFromCard(
                uid = currentUserId,
                myName = myName,
                peerCard = peerCard
            )
            _isBusy.value = false
            result.onSuccess { conv ->
                _activeConversationId.value = conv.id
                onOpened(conv.id)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Unable to start chat"
            }
        }
    }

    fun sendMessage(
        conversation: Conversation,
        text: String,
        messageType: String = "text",
        mediaBase64: String? = null,
        thumbnailBase64: String? = null,
        mediaMimeType: String? = null,
        videoDurationMs: Long? = null,
        stickerId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null
    ) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val senderName = profile?.displayName ?: currentUserName
            repository.sendMessage(
                conversationId = conversation.id,
                senderName = senderName,
                text = text,
                messageType = messageType,
                mediaBase64 = mediaBase64,
                thumbnailBase64 = thumbnailBase64,
                mediaMimeType = mediaMimeType,
                videoDurationMs = videoDurationMs,
                stickerId = stickerId,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                replyToText = replyToText,
                replyToSender = replyToSender,
                memberUids = conversation.memberUids
            ).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Message failed to send"
            }
        }
    }

    fun markConversationMessagesSeen(conversationId: String, messages: List<ChatMessage>) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        if (profile?.readReceiptsEnabled == false) return
        val unseenIds = messages
            .filter { it.id.isNotBlank() && it.senderId != currentUserId && !it.seenByUids.contains(currentUserId) }
            .map { it.id }
        if (unseenIds.isEmpty()) return
        viewModelScope.launch {
            repository.markMessagesAsSeen(conversationId, unseenIds)
        }
    }

    fun updateTypingStatus(conversation: Conversation, isTyping: Boolean) {
        val currentlyTyping = conversation.typingUids.contains(currentUserId)
        if (currentlyTyping == isTyping) return
        val profile = (userProfileState.value as? UiState.Success)?.data
        val displayName = profile?.displayName ?: currentUserName
        viewModelScope.launch {
            repository.setTypingStatus(
                conversation = conversation,
                uid = currentUserId,
                displayName = displayName,
                isTyping = isTyping
            )
        }
    }

    fun reportFeedback(message: String) {
        _feedbackMessage.value = message
    }

    fun toggleReaction(conversationId: String, message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(conversationId, message.id, message.reactionEmoji, emoji)
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Reaction failed"
                }
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(conversationId, messageId)
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Could not delete message"
                }
        }
    }

    fun updateConversationPrivacy(conversation: Conversation, disappearingHours: Long, toggleLock: Boolean) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            if (toggleLock && profile?.hasPassword != true) {
                _feedbackMessage.value = "Set a privacy password in the Privacy tab before locking chats."
                return@launch
            }
            val currentLocks = conversation.lockedByUids.toMutableList()
            if (toggleLock) {
                if (currentLocks.contains(currentUserId)) {
                    currentLocks.remove(currentUserId)
                } else {
                    currentLocks.add(currentUserId)
                }
            }
            repository.updateConversationPrivacy(conversation.id, disappearingHours, currentLocks)
                .onSuccess {
                    if (toggleLock && currentLocks.contains(currentUserId)) {
                        _unlockedChatIds.value = _unlockedChatIds.value - conversation.id
                        _feedbackMessage.value = "Chat locked with your Privacy Password"
                    } else if (toggleLock) {
                        _feedbackMessage.value = "Chat lock removed"
                    } else {
                        _feedbackMessage.value = if (disappearingHours == 0L) {
                            "Disappearing messages turned off"
                        } else {
                            "Disappearing timer set to ${disappearingHours}h"
                        }
                    }
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Privacy update failed"
                }
        }
    }

    // --- WebRTC Audio & Video Calls ---

    fun startCall(
        calleeUid: String,
        calleeName: String,
        conversationId: String?,
        callType: String
    ) {
        if (calleeUid == currentUserId) {
            _feedbackMessage.value = "You cannot call yourself."
            return
        }
        if (_activeCall.value != null) {
            _feedbackMessage.value = "A call is already in progress."
            return
        }

        val isVideo = callType == "video"
        _isMicMuted.value = false
        _isVideoEnabled.value = isVideo
        _isSpeakerOn.value = isVideo
        _webrtcState.value = PeerConnection.PeerConnectionState.CONNECTING

        var createdCallId: String? = null
        val participants = listOf(currentUserId, calleeUid)
        val bufferedIce = mutableListOf<Triple<String, Int, String>>()

        val manager = callManagerFactory(
            { sdpMid, sdpMLineIndex, sdpCandidate ->
                viewModelScope.launch {
                    val cId = createdCallId
                    if (cId == null) {
                        bufferedIce.add(Triple(sdpMid, sdpMLineIndex, sdpCandidate))
                    } else {
                        callAndProfileRepository.addIceCandidate(
                            callId = cId,
                            isCaller = true,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex,
                            sdpCandidate = sdpCandidate,
                            participantUids = participants
                        )
                    }
                }
            },
            { state ->
                _webrtcState.value = state
            }
        )
        _activeCallManager.value = manager
        manager.initialize(enableVideo = isVideo)

        val profile = (userProfileState.value as? UiState.Success)?.data
        val callerName = profile?.displayName ?: currentUserName.ifBlank { "Family Member" }

        // Placeholder session immediately shows outgoing ringing screen while SDP offer creates
        _activeCall.value = CallSession(
            id = "",
            callerId = currentUserId,
            callerName = callerName,
            calleeId = calleeUid,
            calleeName = calleeName,
            conversationId = conversationId.orEmpty(),
            callType = callType,
            status = "ringing",
            participantUids = participants
        )

        manager.createOffer { offerSdp ->
            viewModelScope.launch {
                val result = callAndProfileRepository.createCallSession(
                    callerId = currentUserId,
                    callerName = callerName,
                    calleeId = calleeUid,
                    calleeName = calleeName,
                    conversationId = conversationId,
                    callType = callType,
                    offerSdp = offerSdp
                )
                result.onSuccess { session ->
                    createdCallId = session.id
                    _activeCall.value = session
                    bufferedIce.forEach { (mid, idx, cand) ->
                        callAndProfileRepository.addIceCandidate(
                            callId = session.id,
                            isCaller = true,
                            sdpMid = mid,
                            sdpMLineIndex = idx,
                            sdpCandidate = cand,
                            participantUids = participants
                        )
                    }
                    bufferedIce.clear()
                    observeCallAndRemoteIce(
                        callId = session.id,
                        isCaller = true,
                        manager = manager
                    )
                }.onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to place call"
                    cleanupActiveCall()
                }
            }
        }
    }

    fun acceptIncomingCall(call: CallSession) {
        _incomingCall.value = null
        if (_activeCall.value != null) return

        val isVideo = call.callType == "video"
        _isMicMuted.value = false
        _isVideoEnabled.value = isVideo
        _isSpeakerOn.value = isVideo
        _webrtcState.value = PeerConnection.PeerConnectionState.CONNECTING

        val participants = call.participantUids.ifEmpty { listOf(call.callerId, call.calleeId) }
        val manager = callManagerFactory(
            { sdpMid, sdpMLineIndex, sdpCandidate ->
                viewModelScope.launch {
                    callAndProfileRepository.addIceCandidate(
                        callId = call.id,
                        isCaller = false,
                        sdpMid = sdpMid,
                        sdpMLineIndex = sdpMLineIndex,
                        sdpCandidate = sdpCandidate,
                        participantUids = participants
                    )
                }
            },
            { state ->
                _webrtcState.value = state
            }
        )
        _activeCallManager.value = manager
        _activeCall.value = call
        manager.initialize(enableVideo = isVideo)

        observeCallAndRemoteIce(
            callId = call.id,
            isCaller = false,
            manager = manager
        )

        if (call.offerSdp.isNotBlank()) {
            manager.setRemoteOfferAndCreateAnswer(call.offerSdp) { answerSdp ->
                viewModelScope.launch {
                    callAndProfileRepository.acceptCallSession(call.id, answerSdp)
                        .onFailure { err ->
                            _feedbackMessage.value = err.localizedMessage ?: "Could not answer call"
                            cleanupActiveCall()
                        }
                }
            }
        }
    }

    fun declineIncomingCall(call: CallSession) {
        _incomingCall.value = null
        viewModelScope.launch {
            callAndProfileRepository.endOrDeclineCall(call.id, "declined")
        }
    }

    fun endActiveCall() {
        val current = _activeCall.value
        cleanupActiveCall()
        if (current != null && current.id.isNotBlank()) {
            viewModelScope.launch {
                callAndProfileRepository.endOrDeclineCall(current.id, "ended")
            }
        }
    }

    private fun observeCallAndRemoteIce(
        callId: String,
        isCaller: Boolean,
        manager: WebRtcCallManager
    ) {
        var appliedRemoteAnswer = false
        val appliedCandidates = mutableSetOf<String>()

        callObserverJob?.cancel()
        callObserverJob = viewModelScope.launch {
            callAndProfileRepository.observeCall(callId, currentUserId)
                .catch { err -> Log.w(TAG, "observeCall error", err) }
                .collect { updated ->
                    if (updated == null) return@collect
                    _activeCall.value = updated
                    if (isCaller && !appliedRemoteAnswer && updated.answerSdp.isNotBlank()) {
                        appliedRemoteAnswer = true
                        manager.setRemoteAnswer(updated.answerSdp)
                    }
                    if (updated.status == "ended" || updated.status == "declined") {
                        if (updated.status == "declined" && isCaller) {
                            _feedbackMessage.value = "${updated.calleeName} declined the call"
                        }
                        cleanupActiveCall()
                    }
                }
        }

        iceObserverJob?.cancel()
        iceObserverJob = viewModelScope.launch {
            callAndProfileRepository.observeIceCandidates(
                callId = callId,
                fromCallerCollection = !isCaller,
                uid = currentUserId
            )
                .catch { err -> Log.w(TAG, "observeIceCandidates error", err) }
                .collect { docs ->
                    docs.forEach { doc ->
                        if (appliedCandidates.add(doc.id)) {
                            manager.addRemoteIceCandidate(
                                sdpMid = doc.sdpMid,
                                sdpMLineIndex = doc.sdpMLineIndex.toInt(),
                                sdpCandidate = doc.sdpCandidate
                            )
                        }
                    }
                }
        }
    }

    fun toggleMuteMic() {
        val next = !_isMicMuted.value
        _isMicMuted.value = next
        _activeCallManager.value?.setMicMuted(next)
    }

    fun toggleSpeakerphone() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        _activeCallManager.value?.setSpeakerphone(next)
    }

    fun toggleCallVideo() {
        val next = !_isVideoEnabled.value
        _isVideoEnabled.value = next
        _activeCallManager.value?.setVideoEnabled(next)
    }

    fun switchCallCamera() {
        _activeCallManager.value?.switchCamera()
    }

    private fun cleanupActiveCall() {
        callObserverJob?.cancel()
        callObserverJob = null
        iceObserverJob?.cancel()
        iceObserverJob = null
        _activeCallManager.value?.release()
        _activeCallManager.value = null
        _activeCall.value = null
        _webrtcState.value = PeerConnection.PeerConnectionState.NEW
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupActiveCall()
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
