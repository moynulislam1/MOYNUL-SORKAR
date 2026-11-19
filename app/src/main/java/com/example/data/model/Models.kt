package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class LoginTab {
    GMAIL,
    NUMBER_PASSWORD,
    QR_SCANNER
}

enum class AppDestination {
    CHATS,
    FAMILY,
    PRIVACY
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val phoneNumber: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val hasPassword: Boolean = false,
    val familyRole: String = "Member",
    val bio: String = "",
    val moodTag: String = "✨ Available",
    val avatarBadge: String = "🏡",
    val privacyLockEnabled: Boolean = false,
    val hideMessagePreviews: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val avatarColorHex: String = "#0F5C4D",
    val avatarPhotoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val familyRole: String = "Member",
    val bio: String = "",
    val moodTag: String = "✨ Available",
    val avatarBadge: String = "🏡",
    val avatarColorHex: String = "#0F5C4D",
    val avatarPhotoBase64: String = "",
    val onlineActive: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedContact(
    @DocumentId val id: String = "",
    val peerUid: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val familyRole: String = "Member",
    val bio: String = "",
    val moodTag: String = "✨ Available",
    val avatarBadge: String = "🏡",
    val avatarColorHex: String = "#0F5C4D",
    val avatarPhotoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileCode(
    @DocumentId val id: String = "",
    val profileCode: String = "",
    val targetUid: String = "",
    val displayName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyCircle(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val lastJoinedInviteCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val familyId: String = "",
    val profileCode: String = "",
    val displayName: String = "",
    val familyRole: String = "Member",
    val phoneHint: String = "",
    val avatarColorHex: String = "#0F5C4D",
    val avatarBadge: String = "🏡",
    val avatarPhotoBase64: String = "",
    val bio: String = "",
    val moodTag: String = "✨ Available",
    val statusText: String = "Available",
    val onlineActive: Boolean = false,
    val lastSeenAt: Timestamp? = null,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyInvite(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String = "",
    val type: String = "group",
    val familyId: String = "",
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageAt: Timestamp? = null,
    val disappearingHours: Long = 0L,
    val lockedByUids: List<String> = emptyList(),
    val typingUids: List<String> = emptyList(),
    val typingNames: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val mediaBase64: String = "",
    val thumbnailBase64: String = "",
    val mediaMimeType: String = "",
    val videoDurationMs: Long = 0L,
    val stickerId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val seenByUids: List<String> = emptyList(),
    val replyToText: String = "",
    val replyToSender: String = "",
    val reactionEmoji: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val conversationId: String = "",
    val callType: String = "audio",
    val status: String = "ringing",
    val participantUids: List<String> = emptyList(),
    val offerSdp: String = "",
    val answerSdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IceCandidateDoc(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val sdpCandidate: String = "",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
