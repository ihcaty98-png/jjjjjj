package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.GenerationQuotaDecision
import com.example.model.HelpMessage
import com.example.model.HelpStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentRequest
import com.example.model.PaymentRequestType
import com.example.model.PaymentStatus
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

class AccountBillingRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    private fun isSignedInAdmin(): Boolean {
        return QuotaPolicy.isAdminEmail(auth.currentUser?.email)
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snap = docRef.get().await()
            val cleanEmail = user.email.orEmpty().trim().take(160)
            val cleanName = user.displayName.orEmpty().trim().ifBlank { "Creator" }.take(120)
            if (!snap.exists()) {
                val initial = UserAccount(
                    userId = user.uid,
                    email = cleanEmail,
                    displayName = cleanName,
                    trialStartDate = QuotaPolicy.todayDateKeyYangon()
                )
                docRef.set(initial.toCreateMap()).await()
            } else {
                val existing = UserAccount.fromSnapshot(snap)
                if (existing != null && (existing.email != cleanEmail || existing.displayName != cleanName)) {
                    docRef.update(
                        mapOf(
                            "email" to cleanEmail,
                            "displayName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            // Seed default AppConfig once if signed-in user is Admin and settings doc does not yet exist
            if (QuotaPolicy.isAdminEmail(cleanEmail)) {
                val configRef = db.collection(BillingConstants.CONFIG_COLLECTION)
                    .document(BillingConstants.CONFIG_DOC_ID)
                val cfgSnap = configRef.get().await()
                if (!cfgSnap.exists()) {
                    configRef.set(AppConfig().toWriteMap()).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users")
                .document(uid)
                .snapshots()
                .map { snap -> UserAccount.fromSnapshot(snap) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAppConfig(): Flow<AppConfig> = flow {
        requireUserId()
        val path = "${BillingConstants.CONFIG_COLLECTION}/${BillingConstants.CONFIG_DOC_ID}"
        emitAll(
            db.collection(BillingConstants.CONFIG_COLLECTION)
                .document(BillingConstants.CONFIG_DOC_ID)
                .snapshots()
                .map { snap -> AppConfig.fromSnapshot(snap) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    emit(AppConfig())
                }
        )
    }

    fun observeMyPaymentRequests(userId: String): Flow<List<PaymentRequest>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "paymentRequests"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { PaymentRequest.fromSnapshot(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyHelpMessages(userId: String): Flow<List<HelpMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "helpMessages"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { HelpMessage.fromSnapshot(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- ADMIN REALTIME STREAMS (firechatmyanmar@gmail.com only) ---

    fun observeAllUsersForAdmin(): Flow<List<UserAccount>> = flow {
        requireUserId()
        check(isSignedInAdmin()) { "Admin privileges required." }
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { UserAccount.fromSnapshot(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (isSignedInAdmin()) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllPaymentRequestsForAdmin(): Flow<List<PaymentRequest>> = flow {
        requireUserId()
        check(isSignedInAdmin()) { "Admin privileges required." }
        val path = "paymentRequests"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { PaymentRequest.fromSnapshot(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (isSignedInAdmin()) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllHelpMessagesForAdmin(): Flow<List<HelpMessage>> = flow {
        requireUserId()
        check(isSignedInAdmin()) { "Admin privileges required." }
        val path = "helpMessages"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.documents
                        .mapNotNull { HelpMessage.fromSnapshot(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (isSignedInAdmin()) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- QUOTA CONSUMPTION ---

    suspend fun consumeGenerationQuota(userId: String, config: AppConfig): Result<GenerationQuotaDecision> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val userRef = db.collection("users").document(uid)

            val decision = db.runTransaction { tx ->
                val snap = tx.get(userRef)
                val currentUser = auth.currentUser
                val account = UserAccount.fromSnapshot(snap) ?: UserAccount(
                    userId = uid,
                    email = currentUser?.email.orEmpty(),
                    displayName = currentUser?.displayName.orEmpty().ifBlank { "Creator" },
                    trialStartDate = QuotaPolicy.todayDateKeyYangon()
                )
                val evaluated = QuotaPolicy.evaluateGenerationAccess(account, config)
                when (evaluated) {
                    is GenerationQuotaDecision.AllowedAdmin,
                    is GenerationQuotaDecision.AllowedVip -> {
                        // Unlimited access; no deduction needed
                    }
                    is GenerationQuotaDecision.AllowedFreeTrial -> {
                        val updatedDates = (account.freeTrialDates + evaluated.dateKey)
                            .distinct()
                            .take(BillingConstants.MAX_FREE_TRIAL_DAYS)
                        tx.update(
                            userRef,
                            mapOf(
                                "freeTrialDates" to updatedDates,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    is GenerationQuotaDecision.AllowedCoins -> {
                        if (evaluated.costCoins > 0) {
                            tx.update(
                                userRef,
                                mapOf(
                                    "coins" to evaluated.remainingCoinsAfter,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    is GenerationQuotaDecision.Denied -> Unit
                }
                evaluated
            }.await()

            Result.success(decision)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- USER PAYMENT & HELP SUBMISSION ---

    suspend fun submitPaymentRequest(
        requestType: PaymentRequestType,
        vipTier: VipTier,
        requestedCoins: Int,
        amountMmk: Int,
        paymentMethod: PaymentMethod,
        payerPhone: String,
        transactionRef: String,
        note: String,
        receiptImageBase64: String?
    ): Result<String> {
        var docPath = "paymentRequests"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Please sign in with Google first.")
            val cleanPhone = payerPhone.trim().take(30)
            require(cleanPhone.length >= 5) { "Please enter your payment phone number." }
            require(amountMmk >= 100) { "Invalid payment amount." }

            val docRef = db.collection("paymentRequests").document()
            docPath = docRef.path
            val request = PaymentRequest(
                userId = user.uid,
                userEmail = user.email.orEmpty().trim().take(160),
                userName = user.displayName.orEmpty().trim().ifBlank { "Creator" }.take(120),
                requestType = requestType.firestoreValue,
                vipTier = vipTier.firestoreValue,
                requestedCoins = requestedCoins.coerceAtLeast(0),
                amountMmk = amountMmk,
                paymentMethod = paymentMethod.firestoreValue,
                payerPhone = cleanPhone,
                transactionRef = transactionRef.trim().take(80),
                note = note.trim().take(500),
                receiptImageBase64 = receiptImageBase64?.takeIf { it.isNotBlank() }?.take(280_000),
                status = PaymentStatus.PENDING.firestoreValue
            )
            docRef.set(request.toCreateMap(user.uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitHelpMessage(subject: String, message: String): Result<String> {
        var docPath = "helpMessages"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Please sign in with Google first.")
            val cleanSubject = subject.trim().take(140)
            val cleanMessage = message.trim().take(2000)
            require(cleanSubject.isNotEmpty()) { "Please enter a subject." }
            require(cleanMessage.isNotEmpty()) { "Please enter your message." }

            val docRef = db.collection("helpMessages").document()
            docPath = docRef.path
            val help = HelpMessage(
                userId = user.uid,
                userEmail = user.email.orEmpty().trim().take(160),
                userName = user.displayName.orEmpty().trim().ifBlank { "Creator" }.take(120),
                subject = cleanSubject,
                message = cleanMessage,
                status = HelpStatus.OPEN.firestoreValue
            )
            docRef.set(help.toCreateMap(user.uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    // --- ADMIN ACTIONS ---

    suspend fun approvePaymentRequest(
        request: PaymentRequest,
        bonusCoins: Int = 0,
        adminNote: String = "Approved by Admin"
    ): Result<Unit> {
        val reqPath = "paymentRequests/${request.id}"
        return try {
            require(isSignedInAdmin()) { "Admin privileges required." }
            val adminEmail = auth.currentUser?.email.orEmpty().ifBlank { BillingConstants.ADMIN_EMAIL }
            val reqRef = db.collection("paymentRequests").document(request.id)
            val userRef = db.collection("users").document(request.userId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val targetAccount = UserAccount.fromSnapshot(userSnap)
                    ?: throw IllegalStateException("Target user profile not found.")
                val now = System.currentTimeMillis()

                val userUpdates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val reqType = request.requestTypeEnum()
                if (reqType == PaymentRequestType.VIP && request.vipTierEnum() != VipTier.NONE) {
                    val tier = request.vipTierEnum()
                    val newExpiry = QuotaPolicy.computeExtendedVipExpiryMs(
                        existingExpiryMs = targetAccount.vipExpiresAtMs,
                        months = tier.durationMonths,
                        nowMs = now
                    )
                    userUpdates["vipTier"] = tier.firestoreValue
                    userUpdates["vipExpiresAtMs"] = newExpiry
                    if (bonusCoins > 0) {
                        userUpdates["coins"] = (targetAccount.coins + bonusCoins).coerceIn(0, 1_000_000)
                    }
                } else {
                    val addCoins = request.requestedCoins + bonusCoins.coerceAtLeast(0)
                    userUpdates["coins"] = (targetAccount.coins + addCoins).coerceIn(0, 1_000_000)
                }

                tx.update(userRef, userUpdates)
                tx.update(
                    reqRef,
                    mapOf(
                        "status" to PaymentStatus.APPROVED.firestoreValue,
                        "adminNote" to adminNote.trim().ifBlank { "Approved" }.take(500),
                        "reviewedBy" to adminEmail.take(160),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqPath)
            Result.failure(e)
        }
    }

    suspend fun rejectPaymentRequest(requestId: String, reason: String): Result<Unit> {
        val reqPath = "paymentRequests/$requestId"
        return try {
            require(isSignedInAdmin()) { "Admin privileges required." }
            val adminEmail = auth.currentUser?.email.orEmpty().ifBlank { BillingConstants.ADMIN_EMAIL }
            db.collection("paymentRequests").document(requestId).update(
                mapOf(
                    "status" to PaymentStatus.REJECTED.firestoreValue,
                    "adminNote" to reason.trim().ifBlank { "Payment could not be verified" }.take(500),
                    "reviewedBy" to adminEmail.take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqPath)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserEntitlements(
        targetUser: UserAccount,
        newCoins: Int,
        newVipTier: VipTier,
        resetTrialDates: Boolean = false,
        extendIfSameTier: Boolean = false
    ): Result<Unit> {
        val docPath = "users/${targetUser.userId}"
        return try {
            require(isSignedInAdmin()) { "Admin privileges required." }
            val now = System.currentTimeMillis()
            val newExpiry = when {
                newVipTier == VipTier.NONE -> 0L
                targetUser.vipTierEnum() == newVipTier && targetUser.hasActiveVip(now) && !extendIfSameTier -> {
                    targetUser.vipExpiresAtMs
                }
                else -> {
                    QuotaPolicy.computeExtendedVipExpiryMs(
                        existingExpiryMs = if (targetUser.vipTierEnum() == newVipTier) targetUser.vipExpiresAtMs else now,
                        months = newVipTier.durationMonths,
                        nowMs = now
                    )
                }
            }
            val updates = mutableMapOf<String, Any>(
                "coins" to newCoins.coerceIn(0, 1_000_000),
                "vipTier" to newVipTier.firestoreValue,
                "vipExpiresAtMs" to newExpiry,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (resetTrialDates) {
                updates["trialStartDate"] = QuotaPolicy.todayDateKeyYangon(now)
                updates["freeTrialDates"] = emptyList<String>()
            }
            db.collection("users").document(targetUser.userId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminReplyHelpMessage(messageId: String, replyText: String, markResolved: Boolean): Result<Unit> {
        val docPath = "helpMessages/$messageId"
        return try {
            require(isSignedInAdmin()) { "Admin privileges required." }
            val cleanReply = replyText.trim().take(2000)
            require(cleanReply.isNotEmpty()) { "Reply cannot be empty." }
            val adminEmail = auth.currentUser?.email.orEmpty().ifBlank { BillingConstants.ADMIN_EMAIL }
            db.collection("helpMessages").document(messageId).update(
                mapOf(
                    "status" to if (markResolved) HelpStatus.RESOLVED.firestoreValue else HelpStatus.REPLIED.firestoreValue,
                    "adminReply" to cleanReply,
                    "repliedBy" to adminEmail.take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminSaveAppConfig(config: AppConfig): Result<Unit> {
        val docPath = "${BillingConstants.CONFIG_COLLECTION}/${BillingConstants.CONFIG_DOC_ID}"
        return try {
            require(isSignedInAdmin()) { "Admin privileges required." }
            db.collection(BillingConstants.CONFIG_COLLECTION)
                .document(BillingConstants.CONFIG_DOC_ID)
                .set(config.toWriteMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    companion object {
        /**
         * Compresses a user-selected payment receipt photo from Android Photo Picker
         * into a crisp <= ~130 KB Base64 JPEG string suitable for Firestore storage.
         */
        suspend fun compressReceiptUriToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
            runCatching {
                val appCtx = context.applicationContext
                val rawBytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sampleSize = 1
                while (maxDim / sampleSize > 960) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return@runCatching null
                val scaled = if (max(decoded.width, decoded.height) > 720) {
                    val ratio = 720f / max(decoded.width, decoded.height).toFloat()
                    val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
                    val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, w, h, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
                var quality = 72
                var encoded: String
                do {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    quality -= 12
                } while (encoded.length > 220_000 && quality >= 30)
                scaled.recycle()
                encoded.takeIf { it.length <= 260_000 }
            }.getOrNull()
        }

        fun decodeBase64Bitmap(base64: String?): Bitmap? {
            if (base64.isNullOrBlank()) return null
            return runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
}
