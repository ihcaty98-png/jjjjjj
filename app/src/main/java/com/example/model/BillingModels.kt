package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BillingConstants {
    const val ADMIN_EMAIL = "firechatmyanmar@gmail.com"
    const val DEFAULT_KBZPAY_NUMBER = "09672804206"
    const val DEFAULT_WAVEPAY_NUMBER = ""
    const val DEFAULT_COIN_COST_PER_VIDEO = 10
    const val MAX_FREE_TRIAL_DAYS = 3
    const val VIP_V1_DEFAULT_MMK = 45_000
    const val VIP_V2_DEFAULT_MMK = 56_000
    const val VIP_V3_DEFAULT_MMK = 150_000
    const val CONFIG_COLLECTION = "appConfig"
    const val CONFIG_DOC_ID = "settings"

    val DEFAULT_COIN_PACKAGES = listOf(
        CoinPackage("pack_50", coins = 50, priceMmk = 5_000, label = "Starter • 50 Coins"),
        CoinPackage("pack_120", coins = 120, priceMmk = 10_000, label = "Creator • 120 Coins"),
        CoinPackage("pack_350", coins = 350, priceMmk = 25_000, label = "Studio Pro • 350 Coins")
    )
}

data class CoinPackage(
    val id: String,
    val coins: Int,
    val priceMmk: Int,
    val label: String
)

enum class VipTier(
    val firestoreValue: String,
    val displayLabel: String,
    val burmeseLabel: String,
    val durationMonths: Int,
    val defaultPriceMmk: Int
) {
    NONE("NONE", "Free Account", "Free 3-Day Trial (1/Day)", 0, 0),
    VIP_V1("VIP_V1", "VIP V1", "VIP V1 • ၁ လ (1 Month)", 1, BillingConstants.VIP_V1_DEFAULT_MMK),
    VIP_V2("VIP_V2", "VIP V2", "VIP V2 • ၂ လ (2 Months)", 2, BillingConstants.VIP_V2_DEFAULT_MMK),
    VIP_V3("VIP_V3", "VIP V3", "VIP V3 • ၇ လ (7 Months)", 7, BillingConstants.VIP_V3_DEFAULT_MMK);

    companion object {
        fun fromRaw(raw: String?): VipTier =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: NONE
    }
}

enum class PaymentMethod(val firestoreValue: String, val label: String) {
    KBZPAY("KBZPAY", "KBZPay"),
    WAVEPAY("WAVEPAY", "WavePay");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: KBZPAY
    }
}

enum class PaymentRequestType(val firestoreValue: String) {
    VIP("VIP"),
    COINS("COINS");

    companion object {
        fun fromRaw(raw: String?): PaymentRequestType =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: VIP
    }
}

enum class PaymentStatus(val firestoreValue: String, val label: String) {
    PENDING("PENDING", "Pending Review"),
    APPROVED("APPROVED", "Approved"),
    REJECTED("REJECTED", "Rejected");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: PENDING
    }
}

enum class HelpStatus(val firestoreValue: String, val label: String) {
    OPEN("OPEN", "Waiting Reply"),
    REPLIED("REPLIED", "Admin Replied"),
    RESOLVED("RESOLVED", "Resolved");

    companion object {
        fun fromRaw(raw: String?): HelpStatus =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val coins: Int = 0,
    val trialStartDate: String = "",
    val freeTrialDates: List<String> = emptyList(),
    val vipTier: String = VipTier.NONE.firestoreValue,
    val vipExpiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun vipTierEnum(): VipTier = VipTier.fromRaw(vipTier)

    fun isAdminAccount(): Boolean =
        email.trim().equals(BillingConstants.ADMIN_EMAIL, ignoreCase = true)

    fun hasActiveVip(nowMs: Long = System.currentTimeMillis()): Boolean {
        return vipTierEnum() != VipTier.NONE && vipExpiresAtMs > nowMs
    }

    fun effectiveVipTier(nowMs: Long = System.currentTimeMillis()): VipTier {
        return if (hasActiveVip(nowMs)) vipTierEnum() else VipTier.NONE
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email.trim().take(160),
        "displayName" to displayName.trim().ifBlank { "Creator" }.take(120),
        "coins" to 0,
        "trialStartDate" to trialStartDate.ifBlank { QuotaPolicy.todayDateKeyYangon() }.take(10),
        "freeTrialDates" to emptyList<String>(),
        "vipTier" to VipTier.NONE.firestoreValue,
        "vipExpiresAtMs" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): UserAccount? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val rawDates = (data["freeTrialDates"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.length == 10 }
                ?.take(BillingConstants.MAX_FREE_TRIAL_DAYS)
                .orEmpty()
            return UserAccount(
                id = doc.id,
                userId = (data["userId"] as? String).orEmpty().ifBlank { doc.id },
                email = (data["email"] as? String).orEmpty(),
                displayName = (data["displayName"] as? String).orEmpty().ifBlank { "Creator" },
                coins = (data["coins"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                trialStartDate = (data["trialStartDate"] as? String).orEmpty(),
                freeTrialDates = rawDates,
                vipTier = (data["vipTier"] as? String).orEmpty().ifBlank { VipTier.NONE.firestoreValue },
                vipExpiresAtMs = (data["vipExpiresAtMs"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AppConfig(
    val coinCostPerVideo: Int = BillingConstants.DEFAULT_COIN_COST_PER_VIDEO,
    val kbzPayNumber: String = BillingConstants.DEFAULT_KBZPAY_NUMBER,
    val wavePayNumber: String = BillingConstants.DEFAULT_WAVEPAY_NUMBER,
    val vipV1Price: Int = BillingConstants.VIP_V1_DEFAULT_MMK,
    val vipV2Price: Int = BillingConstants.VIP_V2_DEFAULT_MMK,
    val vipV3Price: Int = BillingConstants.VIP_V3_DEFAULT_MMK,
    val announcementActive: Boolean = false,
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val announcementVersion: String = "",
    val updateUrl: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priceForVip(tier: VipTier): Int = when (tier) {
        VipTier.NONE -> 0
        VipTier.VIP_V1 -> vipV1Price
        VipTier.VIP_V2 -> vipV2Price
        VipTier.VIP_V3 -> vipV3Price
    }

    fun toWriteMap(): Map<String, Any> = mapOf(
        "coinCostPerVideo" to coinCostPerVideo.coerceIn(0, 10000),
        "kbzPayNumber" to kbzPayNumber.trim().take(40),
        "wavePayNumber" to wavePayNumber.trim().take(40),
        "vipV1Price" to vipV1Price.coerceIn(0, 10_000_000),
        "vipV2Price" to vipV2Price.coerceIn(0, 10_000_000),
        "vipV3Price" to vipV3Price.coerceIn(0, 10_000_000),
        "announcementActive" to announcementActive,
        "announcementTitle" to announcementTitle.trim().take(160),
        "announcementMessage" to announcementMessage.trim().take(2000),
        "announcementVersion" to announcementVersion.trim().take(40),
        "updateUrl" to updateUrl.trim().take(500),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): AppConfig {
            if (!doc.exists()) return AppConfig()
            val data = doc.data ?: return AppConfig()
            return AppConfig(
                coinCostPerVideo = (data["coinCostPerVideo"] as? Number)?.toInt()?.coerceIn(0, 10000)
                    ?: BillingConstants.DEFAULT_COIN_COST_PER_VIDEO,
                kbzPayNumber = (data["kbzPayNumber"] as? String)?.takeIf { it.isNotBlank() }
                    ?: BillingConstants.DEFAULT_KBZPAY_NUMBER,
                wavePayNumber = (data["wavePayNumber"] as? String).orEmpty(),
                vipV1Price = (data["vipV1Price"] as? Number)?.toInt()?.coerceAtLeast(0)
                    ?: BillingConstants.VIP_V1_DEFAULT_MMK,
                vipV2Price = (data["vipV2Price"] as? Number)?.toInt()?.coerceAtLeast(0)
                    ?: BillingConstants.VIP_V2_DEFAULT_MMK,
                vipV3Price = (data["vipV3Price"] as? Number)?.toInt()?.coerceAtLeast(0)
                    ?: BillingConstants.VIP_V3_DEFAULT_MMK,
                announcementActive = data["announcementActive"] as? Boolean ?: false,
                announcementTitle = (data["announcementTitle"] as? String).orEmpty(),
                announcementMessage = (data["announcementMessage"] as? String).orEmpty(),
                announcementVersion = (data["announcementVersion"] as? String).orEmpty(),
                updateUrl = (data["updateUrl"] as? String).orEmpty(),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PaymentRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val requestType: String = PaymentRequestType.VIP.firestoreValue,
    val vipTier: String = VipTier.NONE.firestoreValue,
    val requestedCoins: Int = 0,
    val amountMmk: Int = 0,
    val paymentMethod: String = PaymentMethod.KBZPAY.firestoreValue,
    val payerPhone: String = "",
    val transactionRef: String = "",
    val note: String = "",
    val receiptImageBase64: String? = null,
    val status: String = PaymentStatus.PENDING.firestoreValue,
    val adminNote: String? = null,
    val reviewedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun requestTypeEnum(): PaymentRequestType = PaymentRequestType.fromRaw(requestType)
    fun vipTierEnum(): VipTier = VipTier.fromRaw(vipTier)
    fun paymentMethodEnum(): PaymentMethod = PaymentMethod.fromRaw(paymentMethod)
    fun statusEnum(): PaymentStatus = PaymentStatus.fromRaw(status)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "userEmail" to userEmail.trim().take(160),
        "userName" to userName.trim().ifBlank { "Creator" }.take(120),
        "requestType" to requestTypeEnum().firestoreValue,
        "vipTier" to vipTierEnum().firestoreValue,
        "requestedCoins" to requestedCoins.coerceIn(0, 100_000),
        "amountMmk" to amountMmk.coerceIn(100, 50_000_000),
        "paymentMethod" to paymentMethodEnum().firestoreValue,
        "payerPhone" to payerPhone.trim().take(30),
        "transactionRef" to transactionRef.trim().take(80),
        "note" to note.trim().take(500),
        "receiptImageBase64" to receiptImageBase64?.takeIf { it.isNotBlank() }?.take(280_000),
        "status" to PaymentStatus.PENDING.firestoreValue,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): PaymentRequest? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return PaymentRequest(
                id = doc.id,
                userId = (data["userId"] as? String).orEmpty(),
                userEmail = (data["userEmail"] as? String).orEmpty(),
                userName = (data["userName"] as? String).orEmpty().ifBlank { "Creator" },
                requestType = (data["requestType"] as? String).orEmpty(),
                vipTier = (data["vipTier"] as? String).orEmpty(),
                requestedCoins = (data["requestedCoins"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                amountMmk = (data["amountMmk"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                paymentMethod = (data["paymentMethod"] as? String).orEmpty(),
                payerPhone = (data["payerPhone"] as? String).orEmpty(),
                transactionRef = (data["transactionRef"] as? String).orEmpty(),
                note = (data["note"] as? String).orEmpty(),
                receiptImageBase64 = data["receiptImageBase64"] as? String,
                status = (data["status"] as? String).orEmpty(),
                adminNote = data["adminNote"] as? String,
                reviewedBy = data["reviewedBy"] as? String,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class HelpMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val subject: String = "",
    val message: String = "",
    val status: String = HelpStatus.OPEN.firestoreValue,
    val adminReply: String? = null,
    val repliedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): HelpStatus = HelpStatus.fromRaw(status)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "userEmail" to userEmail.trim().take(160),
        "userName" to userName.trim().ifBlank { "Creator" }.take(120),
        "subject" to subject.trim().take(140),
        "message" to message.trim().take(2000),
        "status" to HelpStatus.OPEN.firestoreValue,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): HelpMessage? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return HelpMessage(
                id = doc.id,
                userId = (data["userId"] as? String).orEmpty(),
                userEmail = (data["userEmail"] as? String).orEmpty(),
                userName = (data["userName"] as? String).orEmpty().ifBlank { "Creator" },
                subject = (data["subject"] as? String).orEmpty(),
                message = (data["message"] as? String).orEmpty(),
                status = (data["status"] as? String).orEmpty(),
                adminReply = data["adminReply"] as? String,
                repliedBy = data["repliedBy"] as? String,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class FreeTrialStatus(
    val todayDateKey: String,
    val daysUsedCount: Int,
    val daysRemainingCount: Int,
    val calendarDayIndex: Int,
    val withinThreeDayWindow: Boolean,
    val usedToday: Boolean,
    val canUseFreeToday: Boolean,
    val trialPermanentlyExpired: Boolean
)

sealed interface GenerationQuotaDecision {
    data object AllowedAdmin : GenerationQuotaDecision
    data class AllowedVip(val tier: VipTier, val expiresAtMs: Long) : GenerationQuotaDecision
    data class AllowedFreeTrial(val dateKey: String, val dayNumber: Int) : GenerationQuotaDecision
    data class AllowedCoins(val costCoins: Int, val remainingCoinsAfter: Int) : GenerationQuotaDecision
    data class Denied(
        val reasonBurmese: String,
        val reasonEnglish: String,
        val isTrialExpired: Boolean
    ) : GenerationQuotaDecision
}

/**
 * Pure JVM quota, 3-day (1 video/day) free trial, Video Coins, and VIP V1/V2/V3 math.
 * Uses Calendar + GMT+06:30 (Asia/Yangon) so it works safely on Android API 24+ and JVM tests.
 */
object QuotaPolicy {
    private val YANGON_TZ: TimeZone = TimeZone.getTimeZone("GMT+06:30")
    private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

    fun isAdminEmail(email: String?): Boolean =
        email?.trim().equals(BillingConstants.ADMIN_EMAIL, ignoreCase = true)

    fun todayDateKeyYangon(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = YANGON_TZ
        }
        return sdf.format(Date(nowMs))
    }

    fun formatDateTimeYangon(epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply {
            timeZone = YANGON_TZ
        }
        return sdf.format(Date(epochMs))
    }

    fun formatDateYangon(epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
            timeZone = YANGON_TZ
        }
        return sdf.format(Date(epochMs))
    }

    fun daysBetweenCalendarDates(startDateKey: String, currentDateKey: String): Int {
        val startMs = parseDateStartMs(startDateKey) ?: return 0
        val currentMs = parseDateStartMs(currentDateKey) ?: return 0
        return ((currentMs - startMs) / DAY_MS).toInt()
    }

    private fun parseDateStartMs(dateKey: String): Long? {
        val parts = dateKey.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        val cal = Calendar.getInstance(YANGON_TZ, Locale.US).apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, d.coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun evaluateTrialStatus(
        account: UserAccount,
        nowMs: Long = System.currentTimeMillis()
    ): FreeTrialStatus {
        val todayKey = todayDateKeyYangon(nowMs)
        val distinctUsed = account.freeTrialDates.distinct()
        val daysUsedCount = distinctUsed.size
        val startKey = account.trialStartDate.takeIf { it.length == 10 } ?: todayKey
        val dayIndex = daysBetweenCalendarDates(startKey, todayKey).coerceAtLeast(0)
        val withinWindow = dayIndex < BillingConstants.MAX_FREE_TRIAL_DAYS
        val usedToday = todayKey in distinctUsed
        val canUseToday = withinWindow && daysUsedCount < BillingConstants.MAX_FREE_TRIAL_DAYS && !usedToday
        val permanentlyExpired = !withinWindow || daysUsedCount >= BillingConstants.MAX_FREE_TRIAL_DAYS
        val remainingDays = if (permanentlyExpired) {
            0
        } else {
            (BillingConstants.MAX_FREE_TRIAL_DAYS - daysUsedCount)
                .coerceAtMost(BillingConstants.MAX_FREE_TRIAL_DAYS - dayIndex)
                .coerceAtLeast(0)
        }
        return FreeTrialStatus(
            todayDateKey = todayKey,
            daysUsedCount = daysUsedCount,
            daysRemainingCount = remainingDays,
            calendarDayIndex = dayIndex,
            withinThreeDayWindow = withinWindow,
            usedToday = usedToday,
            canUseFreeToday = canUseToday,
            trialPermanentlyExpired = permanentlyExpired
        )
    }

    fun evaluateGenerationAccess(
        account: UserAccount,
        config: AppConfig,
        nowMs: Long = System.currentTimeMillis()
    ): GenerationQuotaDecision {
        if (account.isAdminAccount()) {
            return GenerationQuotaDecision.AllowedAdmin
        }
        val activeVip = account.effectiveVipTier(nowMs)
        if (activeVip != VipTier.NONE) {
            return GenerationQuotaDecision.AllowedVip(activeVip, account.vipExpiresAtMs)
        }
        val trial = evaluateTrialStatus(account, nowMs)
        if (trial.canUseFreeToday) {
            return GenerationQuotaDecision.AllowedFreeTrial(
                dateKey = trial.todayDateKey,
                dayNumber = trial.daysUsedCount + 1
            )
        }
        val cost = config.coinCostPerVideo.coerceAtLeast(0)
        if (cost == 0 || account.coins >= cost) {
            return GenerationQuotaDecision.AllowedCoins(
                costCoins = cost,
                remainingCoinsAfter = (account.coins - cost).coerceAtLeast(0)
            )
        }
        return if (trial.trialPermanentlyExpired) {
            GenerationQuotaDecision.Denied(
                reasonBurmese = "Free ၃ ရက် အစမ်းသုံးခွင့် ကုန်ဆုံးသွားပါပြီ (1 video/day, 3 days max)။ ဆက်လက်အသုံးပြုရန် Video Coins (${cost} Coins/video) သို့မဟုတ် VIP (V1/V2/V3) ဝယ်ယူပေးပါ။",
                reasonEnglish = "Your 3-day free trial has ended. Buy Video Coins ($cost Coins/video) or upgrade to VIP V1/V2/V3 to continue.",
                isTrialExpired = true
            )
        } else {
            GenerationQuotaDecision.Denied(
                reasonBurmese = "ယနေ့အတွက် Free ၁ ပုဒ် သုံးပြီးပါပြီ (1 video/day)။ ယနေ့ ထပ်သုံးလိုပါက Video Coins (${cost} Coins/video) သို့မဟုတ် VIP ဝယ်ယူအသုံးပြုနိုင်ပါသည်။",
                reasonEnglish = "Daily free limit (1 video/day) reached for today. Buy Video Coins ($cost Coins/video) or upgrade to VIP for unlimited videos.",
                isTrialExpired = false
            )
        }
    }

    fun computeExtendedVipExpiryMs(
        existingExpiryMs: Long,
        months: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        if (months <= 0) return 0L
        val baseMs = maxOf(existingExpiryMs, nowMs)
        val cal = Calendar.getInstance(YANGON_TZ, Locale.US).apply {
            timeInMillis = baseMs
            add(Calendar.MONTH, months)
        }
        return cal.timeInMillis
    }
}
