package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FamilyCircle
import com.example.data.model.FamilyInvite
import com.example.data.model.FamilyMember
import com.example.data.model.UserProfile
import com.example.data.security.CryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FamilyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Gmail before accessing Firestore.")
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    suspend fun ensureUserProfile(
        uid: String,
        email: String,
        displayName: String,
        emailVerified: Boolean
    ): Result<UserProfile> {
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    if (existing.email != email || existing.emailVerified != emailVerified) {
                        docRef.update(
                            mapOf(
                                "email" to email.take(120),
                                "emailVerified" to emailVerified,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    return Result.success(existing)
                }
            }
            val safeName = displayName.trim().ifBlank { email.substringBefore("@").ifBlank { "Family Member" } }.take(50)
            val safeEmail = email.trim().ifBlank { "member@gmail.com" }.take(120)
            val generatedCode = CryptoUtils.generateProfileCode()
            val payload = mapOf(
                "userId" to uid,
                "profileCode" to generatedCode,
                "displayName" to safeName,
                "email" to safeEmail,
                "emailVerified" to emailVerified,
                "hasPassword" to false,
                "familyRole" to "Member",
                "bio" to "",
                "moodTag" to "✨ Available",
                "avatarBadge" to "🏡",
                "privacyLockEnabled" to false,
                "hideMessagePreviews" to false,
                "readReceiptsEnabled" to true,
                "showOnlineStatus" to true,
                "avatarColorHex" to "#0F5C4D",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(
                id = uid,
                userId = uid,
                profileCode = generatedCode,
                displayName = safeName,
                email = safeEmail,
                emailVerified = emailVerified
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserCredentialsAndRole(
        uid: String,
        displayName: String,
        familyRole: String,
        phoneNumber: String,
        newPassword: String?
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch" }
            val safeName = displayName.trim().take(50)
            require(safeName.isNotEmpty()) { "Display name is required" }
            val safeRole = familyRole.trim().ifBlank { "Member" }.take(30)
            val normalizedPhone = CryptoUtils.normalizePhone(phoneNumber).take(24)

            val updates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "familyRole" to safeRole,
                "phoneNumber" to normalizedPhone,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newPassword.isNullOrBlank()) {
                val salt = CryptoUtils.generateSalt()
                val hash = CryptoUtils.hashPassword(newPassword, salt)
                updates["passwordSalt"] = salt
                updates["passwordHash"] = hash
                updates["hasPassword"] = true
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        uid: String,
        privacyLockEnabled: Boolean,
        hideMessagePreviews: Boolean,
        readReceiptsEnabled: Boolean,
        showOnlineStatus: Boolean
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch" }
            db.collection("users").document(uid).update(
                mapOf(
                    "privacyLockEnabled" to privacyLockEnabled,
                    "hideMessagePreviews" to hideMessagePreviews,
                    "readReceiptsEnabled" to readReceiptsEnabled,
                    "showOnlineStatus" to showOnlineStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFamilies(uid: String): Flow<List<FamilyCircle>> = flow {
        val path = "families"
        emitAll(
            db.collection("families")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyCircle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeFamilyMembers(familyId: String, uid: String): Flow<List<FamilyMember>> = flow {
        val path = "families/$familyId/members"
        emitAll(
            db.collection("families").document(familyId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.displayName.lowercase() }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun fetchFamilyMembersByUids(
        familyId: String,
        memberUids: List<String>
    ): Result<List<FamilyMember>> {
        val path = "families/$familyId/members"
        return try {
            requireUserId()
            val members = memberUids.distinct().take(25).mapNotNull { memberUid ->
                val snap = db.collection("families").document(familyId)
                    .collection("members").document(memberUid)
                    .get()
                    .await()
                if (snap.exists()) {
                    snap.toObject(
                        FamilyMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }.sortedBy { it.displayName.lowercase() }
            Result.success(members)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createFamilyCircle(
        uid: String,
        displayName: String,
        familyRole: String,
        phoneNumber: String,
        familyName: String,
        profileCode: String = "",
        bio: String = "",
        moodTag: String = "✨ Available",
        avatarBadge: String = "🏡",
        avatarColorHex: String = "#0F5C4D",
        avatarPhotoBase64: String = "",
        showOnlineStatus: Boolean = true
    ): Result<FamilyCircle> {
        var path = "families"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch" }
            val safeFamilyName = familyName.trim().take(50)
            require(safeFamilyName.isNotEmpty()) { "Family name cannot be empty" }
            val safeName = displayName.trim().ifBlank { "Family Member" }.take(50)
            val safeRole = familyRole.trim().ifBlank { "Member" }.take(30)

            val familyRef = db.collection("families").document()
            val familyId = familyRef.id
            path = familyRef.path
            val inviteCode = CryptoUtils.generateInviteCode()

            val familyPayload = mapOf(
                "name" to safeFamilyName,
                "inviteCode" to inviteCode,
                "createdBy" to uid,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            familyRef.set(familyPayload).await()

            val memberPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "familyId" to familyId,
                "displayName" to safeName,
                "familyRole" to safeRole,
                "phoneHint" to CryptoUtils.maskPhone(phoneNumber).take(24),
                "avatarColorHex" to avatarColorHex.ifBlank { "#0F5C4D" }.take(9),
                "avatarBadge" to avatarBadge.ifBlank { "🏡" }.take(16),
                "bio" to bio.take(300),
                "moodTag" to moodTag.ifBlank { "✨ Available" }.take(40),
                "statusText" to "At Home",
                "onlineActive" to showOnlineStatus,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).apply {
                if (profileCode.isNotBlank()) put("profileCode", profileCode.take(20))
                if (avatarPhotoBase64.isNotBlank()) put("avatarPhotoBase64", avatarPhotoBase64.take(59500))
            }.filterValues { it != "" }
            db.collection("families").document(familyId)
                .collection("members").document(uid)
                .set(memberPayload)
                .await()

            val invitePayload = mapOf(
                "familyId" to familyId,
                "familyName" to safeFamilyName,
                "createdBy" to uid,
                "createdByName" to safeName,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("family_invites").document(inviteCode)
                .set(invitePayload)
                .await()

            val convId = "family_$familyId"
            val convRef = db.collection("conversations").document(convId)
            val convPayload = mapOf(
                "title" to safeFamilyName.take(60),
                "type" to "group",
                "familyId" to familyId,
                "createdBy" to uid,
                "memberUids" to listOf(uid),
                "memberNames" to mapOf(uid to safeName),
                "lastMessageText" to "Private family group created",
                "lastMessageSenderName" to safeName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "disappearingHours" to 0L,
                "lockedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(convPayload).await()

            val welcomeMsgRef = convRef.collection("messages").document()
            welcomeMsgRef.set(
                mapOf(
                    "conversationId" to convId,
                    "senderId" to uid,
                    "senderName" to safeName,
                    "text" to "$safeName created the $safeFamilyName private room. Share the QR code to invite family!",
                    "messageType" to "system",
                    "memberUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                FamilyCircle(
                    id = familyId,
                    name = safeFamilyName,
                    inviteCode = inviteCode,
                    createdBy = uid,
                    memberUids = listOf(uid)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinFamilyByInviteCode(
        uid: String,
        displayName: String,
        familyRole: String,
        phoneNumber: String,
        rawCode: String,
        profileCode: String = "",
        bio: String = "",
        moodTag: String = "✨ Available",
        avatarBadge: String = "🏡",
        avatarColorHex: String = "#D96B43",
        avatarPhotoBase64: String = "",
        showOnlineStatus: Boolean = true
    ): Result<String> {
        val code = rawCode.trim().uppercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        var path = "family_invites/$code"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch" }
            require(code.length in 6..20) { "Invalid invite code format" }

            val inviteSnap = db.collection("family_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Family invite code not found"))
            }
            val invite = inviteSnap.toObject(
                FamilyInvite::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid invite payload"))
            if (!invite.active) {
                return Result.failure(IllegalStateException("This family invite has been deactivated"))
            }

            val familyId = invite.familyId
            val familyRef = db.collection("families").document(familyId)
            path = familyRef.path

            val safeName = displayName.trim().ifBlank { "Family Member" }.take(50)
            val safeRole = familyRole.trim().ifBlank { "Member" }.take(30)

            // Check if already a member
            val existingFamilySnap = try {
                familyRef.get().await()
            } catch (_: Exception) {
                null
            }
            val existingMembers = existingFamilySnap?.get("memberUids") as? List<*>
            val alreadyMember = existingMembers?.contains(uid) == true

            if (!alreadyMember) {
                familyRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "lastJoinedInviteCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val updatedFamilySnap = familyRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val allMemberUids = (updatedFamilySnap.get("memberUids") as? List<String>).orEmpty()
                .ifEmpty { listOf(uid) }

            val memberDocRef = familyRef.collection("members").document(uid)
            val memberSnap = memberDocRef.get().await()
            if (!memberSnap.exists()) {
                val memberPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "familyId" to familyId,
                    "displayName" to safeName,
                    "familyRole" to safeRole,
                    "phoneHint" to CryptoUtils.maskPhone(phoneNumber).take(24),
                    "avatarColorHex" to avatarColorHex.ifBlank { "#D96B43" }.take(9),
                    "avatarBadge" to avatarBadge.ifBlank { "🏡" }.take(16),
                    "bio" to bio.take(300),
                    "moodTag" to moodTag.ifBlank { "✨ Available" }.take(40),
                    "statusText" to "Joined Family",
                    "onlineActive" to showOnlineStatus,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "memberUids" to allMemberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).apply {
                    if (profileCode.isNotBlank()) put("profileCode", profileCode.take(20))
                    if (avatarPhotoBase64.isNotBlank()) put("avatarPhotoBase64", avatarPhotoBase64.take(59500))
                }.filterValues { it != "" }
                memberDocRef.set(memberPayload).await()
            }

            // Update denormalized memberUids on existing member cards
            val existingCards = familyRef.collection("members")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            existingCards.documents.forEach { doc ->
                if (doc.id != uid) {
                    doc.reference.update(
                        mapOf(
                            "memberUids" to allMemberUids,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Join the family's default group conversation
            val convId = "family_$familyId"
            val convRef = db.collection("conversations").document(convId)
            val convSnap = convRef.get().await()
            if (convSnap.exists()) {
                @Suppress("UNCHECKED_CAST")
                val currentNames = (convSnap.get("memberNames") as? Map<String, String>).orEmpty().toMutableMap()
                currentNames[uid] = safeName
                @Suppress("UNCHECKED_CAST")
                val currentConvMembers = (convSnap.get("memberUids") as? List<String>).orEmpty()
                if (!currentConvMembers.contains(uid)) {
                    convRef.update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "memberNames" to currentNames,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(invite.familyName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyMemberStatus(
        familyId: String,
        uid: String,
        statusText: String
    ): Result<Unit> {
        val path = "families/$familyId/members/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            db.collection("families").document(familyId)
                .collection("members").document(uid)
                .update(
                    mapOf(
                        "statusText" to statusText.trim().take(80),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { (it.lastMessageAt ?: it.updatedAt ?: it.createdAt)?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun createOrOpenDirectChat(
        uid: String,
        myName: String,
        peer: FamilyMember
    ): Result<Conversation> {
        val sortedPair = listOf(uid, peer.userId).sorted()
        val convId = "dm_${sortedPair[0].take(20)}_${sortedPair[1].take(20)}"
        val path = "conversations/$convId"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val convRef = db.collection("conversations").document(convId)
            val snap = convRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return Result.success(existing)
            }
            val safeMyName = myName.trim().ifBlank { "Me" }.take(50)
            val safePeerName = peer.displayName.trim().ifBlank { "Family Member" }.take(50)
            val title = if (uid == peer.userId) "$safeMyName (Personal Vault)" else safePeerName
            val memberUids = if (uid == peer.userId) listOf(uid) else sortedPair
            val memberNames = if (uid == peer.userId) {
                mapOf(uid to safeMyName)
            } else {
                mapOf(uid to safeMyName, peer.userId to safePeerName)
            }
            val payload = mapOf(
                "title" to title.take(60),
                "type" to "direct",
                "familyId" to peer.familyId,
                "createdBy" to uid,
                "memberUids" to memberUids,
                "memberNames" to memberNames,
                "lastMessageText" to "Private chat started",
                "lastMessageSenderName" to safeMyName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "disappearingHours" to 0L,
                "lockedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                convRef.set(payload).await()
            } catch (writeErr: Exception) {
                if (writeErr is CancellationException) throw writeErr
                val retrySnap = runCatching { convRef.get().await() }.getOrNull()
                val existing = if (retrySnap != null && retrySnap.exists()) {
                    retrySnap.toObject(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
                if (existing != null) return Result.success(existing)
                throw writeErr
            }
            val created = convRef.get().await().toObject(
                Conversation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: Conversation(
                id = convId,
                title = title,
                type = "direct",
                familyId = peer.familyId,
                createdBy = uid,
                memberUids = memberUids,
                memberNames = memberNames
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationPrivacy(
        conversationId: String,
        disappearingHours: Long,
        lockedByUids: List<String>
    ): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            requireUserId()
            require(disappearingHours in listOf(0L, 24L, 168L))
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "disappearingHours" to disappearingHours,
                    "lockedByUids" to lockedByUids.take(25),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun sendMessage(
        conversationId: String,
        senderName: String,
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
        replyToSender: String? = null,
        memberUids: List<String>
    ): Result<Unit> {
        val convRef = db.collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document()
        val path = msgRef.path
        return try {
            val uid = requireUserId()
            val safeText = text.trim().take(2000)
            require(safeText.isNotEmpty()) { "Message cannot be empty" }
            val safeSender = senderName.trim().ifBlank { "Family Member" }.take(50)
            val validMembers = if (memberUids.contains(uid)) memberUids.take(25) else (memberUids + uid).take(25)

            val payload = mutableMapOf<String, Any?>(
                "conversationId" to conversationId,
                "senderId" to uid,
                "senderName" to safeSender,
                "text" to safeText,
                "messageType" to messageType,
                "mediaBase64" to mediaBase64?.take(690000)?.ifBlank { null },
                "thumbnailBase64" to thumbnailBase64?.take(58000)?.ifBlank { null },
                "mediaMimeType" to mediaMimeType?.take(64)?.ifBlank { null },
                "videoDurationMs" to videoDurationMs?.takeIf { it >= 0L },
                "stickerId" to stickerId?.take(64)?.ifBlank { null },
                "latitude" to latitude,
                "longitude" to longitude,
                "locationLabel" to locationLabel?.trim()?.take(200)?.ifBlank { null },
                "seenByUids" to listOf(uid),
                "replyToText" to replyToText?.trim()?.take(200)?.ifBlank { null },
                "replyToSender" to replyToSender?.trim()?.take(50)?.ifBlank { null },
                "memberUids" to validMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            msgRef.set(payload).await()
            val previewSummary = when (messageType) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                "location" -> "📍 ${locationLabel?.take(40) ?: "Location"}"
                "sticker" -> safeText
                else -> safeText
            }
            convRef.update(
                mapOf(
                    "lastMessageText" to previewSummary.take(500),
                    "lastMessageSenderName" to safeSender,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "typingUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsSeen(
        conversationId: String,
        messageIds: List<String>
    ): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        val path = "conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            messageIds.distinct().take(20).forEach { msgId ->
                try {
                    db.collection("conversations").document(conversationId)
                        .collection("messages").document(msgId)
                        .update(
                            mapOf(
                                "seenByUids" to FieldValue.arrayUnion(uid),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                } catch (_: Exception) {
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(
        conversation: Conversation,
        uid: String,
        displayName: String,
        isTyping: Boolean
    ) {
        try {
            val activeUid = auth.currentUser?.uid ?: return
            if (activeUid != uid) return
            val convRef = db.collection("conversations").document(conversation.id)
            val safeName = displayName.trim().ifBlank { "Family Member" }.take(50)
            val updatedNames = conversation.typingNames.toMutableMap()
            if (isTyping) {
                updatedNames[uid] = safeName
            } else {
                updatedNames.remove(uid)
            }
            val updatedUids = if (isTyping) {
                (conversation.typingUids + uid).distinct().take(25)
            } else {
                conversation.typingUids.filter { it != uid }.take(25)
            }
            convRef.update(
                mapOf(
                    "typingUids" to updatedUids,
                    "typingNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        currentEmoji: String,
        newEmoji: String
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            requireUserId()
            val targetEmoji = if (currentEmoji == newEmoji) "" else newEmoji.take(16)
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "reactionEmoji" to targetEmoji,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
