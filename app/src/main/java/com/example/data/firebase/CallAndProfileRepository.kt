package com.example.data.firebase

import com.example.data.model.CallSession
import com.example.data.model.Conversation
import com.example.data.model.IceCandidateDoc
import com.example.data.model.ProfileCard
import com.example.data.model.ProfileCode
import com.example.data.model.SavedContact
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

class CallAndProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun handleStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
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

    suspend fun ensureProfileCardAndCode(
        uid: String,
        displayName: String,
        familyRole: String,
        existingCode: String,
        bio: String,
        moodTag: String,
        avatarBadge: String,
        avatarColorHex: String,
        avatarPhotoBase64: String,
        showOnlineStatus: Boolean
    ): Result<String> {
        var path = "profile_cards/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val code = existingCode.ifBlank { CryptoUtils.generateProfileCode() }
            val safeName = displayName.trim().ifBlank { "Family Member" }.take(50)
            val safeRole = familyRole.trim().ifBlank { "Member" }.take(30)

            val cardRef = db.collection("profile_cards").document(uid)
            val cardSnap = cardRef.get().await()
            if (!cardSnap.exists()) {
                val cardPayload = mapOf(
                    "userId" to uid,
                    "profileCode" to code,
                    "displayName" to safeName,
                    "familyRole" to safeRole,
                    "bio" to bio.take(300),
                    "moodTag" to moodTag.ifBlank { "✨ Available" }.take(40),
                    "avatarBadge" to avatarBadge.ifBlank { "🏡" }.take(16),
                    "avatarColorHex" to avatarColorHex.ifBlank { "#0F5C4D" }.take(9),
                    "avatarPhotoBase64" to avatarPhotoBase64.take(59500),
                    "onlineActive" to showOnlineStatus,
                    "showOnlineStatus" to showOnlineStatus,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                cardRef.set(cardPayload).await()
            }

            path = "profile_codes/$code"
            val codeRef = db.collection("profile_codes").document(code)
            val codeSnap = codeRef.get().await()
            if (!codeSnap.exists()) {
                codeRef.set(
                    mapOf(
                        "profileCode" to code,
                        "targetUid" to uid,
                        "displayName" to safeName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Also ensure user doc stores profileCode if empty
            if (existingCode.isBlank()) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "profileCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun lookupProfileByQrOrCode(
        targetUid: String?,
        profileCode: String?
    ): Result<ProfileCard> {
        var path = "profile_cards"
        return try {
            requireUserId()
            val resolvedUid = if (!targetUid.isNullOrBlank()) {
                targetUid.trim()
            } else {
                val cleanCode = profileCode.orEmpty().trim().uppercase()
                require(cleanCode.length in 6..20) { "Invalid profile code" }
                path = "profile_codes/$cleanCode"
                val codeSnap = db.collection("profile_codes").document(cleanCode).get().await()
                if (!codeSnap.exists()) {
                    return Result.failure(IllegalArgumentException("No member found for code $cleanCode"))
                }
                val parsed = codeSnap.toObject(
                    ProfileCode::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                parsed?.targetUid ?: return Result.failure(IllegalStateException("Invalid profile code data"))
            }

            path = "profile_cards/$resolvedUid"
            val cardSnap = db.collection("profile_cards").document(resolvedUid).get().await()
            if (!cardSnap.exists()) {
                return Result.failure(IllegalArgumentException("Profile not found"))
            }
            val card = cardSnap.toObject(
                ProfileCard::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Could not decode profile card"))
            Result.success(card)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeProfileCard(targetUid: String, viewerUid: String): Flow<ProfileCard?> = flow {
        val path = "profile_cards/$targetUid"
        emitAll(
            db.collection("profile_cards").document(targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ProfileCard::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, viewerUid) }
        )
    }

    suspend fun updateProfileBioAndMood(
        uid: String,
        displayName: String,
        familyRole: String,
        bio: String,
        moodTag: String,
        avatarBadge: String,
        avatarColorHex: String,
        avatarPhotoBase64: String,
        familyIds: List<String>
    ): Result<Unit> {
        var path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val safeName = displayName.trim().ifBlank { "Family Member" }.take(50)
            val safeRole = familyRole.trim().ifBlank { "Member" }.take(30)
            val safeBio = bio.trim().take(300)
            val safeMood = moodTag.trim().ifBlank { "✨ Available" }.take(40)
            val safeBadge = avatarBadge.trim().ifBlank { "🏡" }.take(16)
            val safeColor = avatarColorHex.trim().ifBlank { "#0F5C4D" }.take(9)
            val safePhoto = avatarPhotoBase64.take(59500)

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to safeName,
                    "familyRole" to safeRole,
                    "bio" to safeBio,
                    "moodTag" to safeMood,
                    "avatarBadge" to safeBadge,
                    "avatarColorHex" to safeColor,
                    "avatarPhotoBase64" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            path = "profile_cards/$uid"
            db.collection("profile_cards").document(uid).update(
                mapOf(
                    "displayName" to safeName,
                    "familyRole" to safeRole,
                    "bio" to safeBio,
                    "moodTag" to safeMood,
                    "avatarBadge" to safeBadge,
                    "avatarColorHex" to safeColor,
                    "avatarPhotoBase64" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            familyIds.distinct().forEach { famId ->
                try {
                    db.collection("families").document(famId)
                        .collection("members").document(uid)
                        .update(
                            mapOf(
                                "displayName" to safeName,
                                "familyRole" to safeRole,
                                "bio" to safeBio,
                                "moodTag" to safeMood,
                                "avatarBadge" to safeBadge,
                                "avatarColorHex" to safeColor,
                                "avatarPhotoBase64" to safePhoto,
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

    fun observeSavedContacts(uid: String): Flow<List<SavedContact>> = flow {
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedContact::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun saveContactCard(uid: String, card: ProfileCard): Result<Unit> {
        val peerUid = card.userId.ifBlank { card.id }
        val path = "users/$uid/contacts/$peerUid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            require(peerUid.isNotBlank() && peerUid != uid) { "Invalid contact UID" }

            val docRef = db.collection("users").document(uid).collection("contacts").document(peerUid)
            val existing = docRef.get().await()
            val safeName = card.displayName.trim().ifBlank { "Family Member" }.take(50)
            val safeRole = card.familyRole.trim().ifBlank { "Member" }.take(30)

            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "profileCode" to card.profileCode.take(20),
                        "displayName" to safeName,
                        "familyRole" to safeRole,
                        "bio" to card.bio.take(300),
                        "moodTag" to card.moodTag.ifBlank { "✨ Available" }.take(40),
                        "avatarBadge" to card.avatarBadge.ifBlank { "🏡" }.take(16),
                        "avatarColorHex" to card.avatarColorHex.ifBlank { "#0F5C4D" }.take(9),
                        "avatarPhotoBase64" to card.avatarPhotoBase64.take(59500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "peerUid" to peerUid,
                        "profileCode" to card.profileCode.take(20),
                        "displayName" to safeName,
                        "familyRole" to safeRole,
                        "bio" to card.bio.take(300),
                        "moodTag" to card.moodTag.ifBlank { "✨ Available" }.take(40),
                        "avatarBadge" to card.avatarBadge.ifBlank { "🏡" }.take(16),
                        "avatarColorHex" to card.avatarColorHex.ifBlank { "#0F5C4D" }.take(9),
                        "avatarPhotoBase64" to card.avatarPhotoBase64.take(59500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setOnlinePresence(
        uid: String,
        online: Boolean,
        showOnlineStatus: Boolean,
        familyIds: List<String>
    ): Result<Unit> {
        val effectiveOnline = online && showOnlineStatus
        val path = "profile_cards/$uid"
        return try {
            val activeUid = auth.currentUser?.uid ?: return Result.success(Unit)
            if (activeUid != uid) return Result.success(Unit)
            val cardRef = db.collection("profile_cards").document(uid)
            val cardSnap = cardRef.get().await()
            if (cardSnap.exists()) {
                cardRef.update(
                    mapOf(
                        "onlineActive" to effectiveOnline,
                        "showOnlineStatus" to showOnlineStatus,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            familyIds.distinct().forEach { famId ->
                try {
                    db.collection("families").document(famId)
                        .collection("members").document(uid)
                        .update(
                            mapOf(
                                "onlineActive" to effectiveOnline,
                                "lastSeenAt" to FieldValue.serverTimestamp(),
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

    suspend fun createOrOpenDirectChatFromCard(
        uid: String,
        myName: String,
        peerCard: ProfileCard
    ): Result<Conversation> {
        val peerUid = peerCard.userId.ifBlank { peerCard.id }
        val sortedPair = listOf(uid, peerUid).sorted()
        val convId = "dm_${sortedPair[0].take(20)}_${sortedPair[1].take(20)}"
        val path = "conversations/$convId"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            require(peerUid.isNotBlank()) { "Invalid member ID" }
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
            val safePeerName = peerCard.displayName.trim().ifBlank { "Family Member" }.take(50)
            val title = if (uid == peerUid) "$safeMyName (Personal Vault)" else safePeerName
            val memberUids = if (uid == peerUid) listOf(uid) else sortedPair
            val memberNames = if (uid == peerUid) {
                mapOf(uid to safeMyName)
            } else {
                mapOf(uid to safeMyName, peerUid to safePeerName)
            }
            val payload = mapOf(
                "title" to title.take(60),
                "type" to "direct",
                "createdBy" to uid,
                "memberUids" to memberUids,
                "memberNames" to memberNames,
                "lastMessageText" to "Connected via Profile QR",
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

    // --- WebRTC Audio & Video Call Signaling ---

    fun observeActiveCalls(uid: String): Flow<List<CallSession>> = flow {
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.status == "ringing" || it.status == "accepted" }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeCall(callId: String, uid: String): Flow<CallSession?> = flow {
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    suspend fun createCallSession(
        callerId: String,
        callerName: String,
        calleeId: String,
        calleeName: String,
        conversationId: String?,
        callType: String,
        offerSdp: String
    ): Result<CallSession> {
        val callRef = db.collection("calls").document()
        val path = callRef.path
        return try {
            val activeUid = requireUserId()
            check(activeUid == callerId)
            val safeCaller = callerName.trim().ifBlank { "Family Member" }.take(50)
            val safeCallee = calleeName.trim().ifBlank { "Family Member" }.take(50)
            val participants = listOf(callerId, calleeId)

            val payload = mutableMapOf<String, Any?>(
                "callerId" to callerId,
                "callerName" to safeCaller,
                "calleeId" to calleeId,
                "calleeName" to safeCallee,
                "conversationId" to conversationId?.take(128),
                "callType" to callType,
                "status" to "ringing",
                "participantUids" to participants,
                "offerSdp" to offerSdp.take(20000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            callRef.set(payload).await()
            Result.success(
                CallSession(
                    id = callRef.id,
                    callerId = callerId,
                    callerName = safeCaller,
                    calleeId = calleeId,
                    calleeName = safeCallee,
                    conversationId = conversationId.orEmpty(),
                    callType = callType,
                    status = "ringing",
                    participantUids = participants,
                    offerSdp = offerSdp
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptCallSession(callId: String, answerSdp: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to "accepted",
                    "answerSdp" to answerSdp.take(20000),
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

    suspend fun endOrDeclineCall(callId: String, terminalStatus: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val status = if (terminalStatus == "declined") "declined" else "ended"
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
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

    suspend fun addIceCandidate(
        callId: String,
        isCaller: Boolean,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdpCandidate: String,
        participantUids: List<String>
    ): Result<Unit> {
        val sub = if (isCaller) "caller_candidates" else "callee_candidates"
        val docRef = db.collection("calls").document(callId).collection(sub).document()
        val path = docRef.path
        return try {
            val uid = requireUserId()
            docRef.set(
                mapOf(
                    "senderId" to uid,
                    "sdpMid" to sdpMid.take(64),
                    "sdpMLineIndex" to sdpMLineIndex.toLong(),
                    "sdpCandidate" to sdpCandidate.take(2000),
                    "participantUids" to participantUids.take(2),
                    "createdAt" to FieldValue.serverTimestamp()
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

    fun observeIceCandidates(
        callId: String,
        fromCallerCollection: Boolean,
        uid: String
    ): Flow<List<IceCandidateDoc>> = flow {
        val sub = if (fromCallerCollection) "caller_candidates" else "callee_candidates"
        val path = "calls/$callId/$sub"
        emitAll(
            db.collection("calls").document(callId).collection(sub)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IceCandidateDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }
}
