package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountBillingRepository
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.GenerationQuotaDecision
import com.example.model.HelpMessage
import com.example.model.PaymentMethod
import com.example.model.PaymentRequest
import com.example.model.PaymentRequestType
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BillingViewModel(
    private val repository: AccountBillingRepository,
    private val currentUser: FirebaseUser
) : ViewModel() {

    val isAdmin: Boolean = QuotaPolicy.isAdminEmail(currentUser.email)

    val userAccount: StateFlow<UserAccount?> = repository.observeUserAccount(currentUser.uid)
        .catch { err ->
            val fsErr = generateSequence(err) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing userAccount", err)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserAccount(
                userId = currentUser.uid,
                email = currentUser.email.orEmpty(),
                displayName = currentUser.displayName.orEmpty().ifBlank { "Creator" },
                trialStartDate = QuotaPolicy.todayDateKeyYangon()
            )
        )

    val appConfig: StateFlow<AppConfig> = repository.observeAppConfig()
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing appConfig", err)
            emit(AppConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppConfig()
        )

    val myPaymentRequests: StateFlow<List<PaymentRequest>> = repository.observeMyPaymentRequests(currentUser.uid)
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing my payment requests", err)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val myHelpMessages: StateFlow<List<HelpMessage>> = repository.observeMyHelpMessages(currentUser.uid)
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing my help messages", err)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val adminUsers: StateFlow<List<UserAccount>> = (if (isAdmin) {
        repository.observeAllUsersForAdmin()
    } else {
        flowOf(emptyList())
    })
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing all users for admin", err)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val adminPayments: StateFlow<List<PaymentRequest>> = (if (isAdmin) {
        repository.observeAllPaymentRequestsForAdmin()
    } else {
        flowOf(emptyList())
    })
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing all payments for admin", err)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val adminHelpMessages: StateFlow<List<HelpMessage>> = (if (isAdmin) {
        repository.observeAllHelpMessagesForAdmin()
    } else {
        flowOf(emptyList())
    })
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Error observing all help messages for admin", err)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _isSubmittingPayment = MutableStateFlow(false)
    val isSubmittingPayment: StateFlow<Boolean> = _isSubmittingPayment.asStateFlow()

    private val _isSubmittingHelp = MutableStateFlow(false)
    val isSubmittingHelp: StateFlow<Boolean> = _isSubmittingHelp.asStateFlow()

    private val _adminBusy = MutableStateFlow(false)
    val adminBusy: StateFlow<Boolean> = _adminBusy.asStateFlow()

    private val _dismissedAnnouncementSignature = MutableStateFlow<String?>(null)
    val dismissedAnnouncementSignature: StateFlow<String?> = _dismissedAnnouncementSignature.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUser)
        }
    }

    fun evaluateCurrentQuota(): GenerationQuotaDecision {
        val account = userAccount.value ?: UserAccount(
            userId = currentUser.uid,
            email = currentUser.email.orEmpty(),
            displayName = currentUser.displayName.orEmpty().ifBlank { "Creator" },
            trialStartDate = QuotaPolicy.todayDateKeyYangon()
        )
        return QuotaPolicy.evaluateGenerationAccess(account, appConfig.value)
    }

    suspend fun consumeQuotaAfterSuccess(): Result<GenerationQuotaDecision> {
        return repository.consumeGenerationQuota(currentUser.uid, appConfig.value)
    }

    fun dismissAnnouncement(config: AppConfig) {
        _dismissedAnnouncementSignature.value = "${config.announcementVersion}|${config.announcementTitle}|${config.announcementMessage}"
    }

    fun submitPaymentRequest(
        context: Context,
        requestType: PaymentRequestType,
        vipTier: VipTier,
        requestedCoins: Int,
        amountMmk: Int,
        paymentMethod: PaymentMethod,
        payerPhone: String,
        transactionRef: String,
        note: String,
        receiptUri: Uri?,
        onSuccess: () -> Unit
    ) {
        if (_isSubmittingPayment.value) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isSubmittingPayment.value = true
            try {
                val receiptBase64 = if (receiptUri != null) {
                    AccountBillingRepository.compressReceiptUriToBase64(appContext, receiptUri)
                } else {
                    null
                }
                val result = repository.submitPaymentRequest(
                    requestType = requestType,
                    vipTier = vipTier,
                    requestedCoins = requestedCoins,
                    amountMmk = amountMmk,
                    paymentMethod = paymentMethod,
                    payerPhone = payerPhone,
                    transactionRef = transactionRef,
                    note = note,
                    receiptImageBase64 = receiptBase64
                )
                result.onSuccess {
                    _snackbarMessage.value = "ငွေလွှဲအထောက်အထား ပေးပို့ပြီးပါပြီ (Admin စစ်ဆေးအတည်ပြုပေးပါမည်)"
                    onSuccess()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to submit payment."
                }
            } finally {
                _isSubmittingPayment.value = false
            }
        }
    }

    fun submitHelpMessage(
        subject: String,
        message: String,
        onSuccess: () -> Unit
    ) {
        if (_isSubmittingHelp.value) return
        viewModelScope.launch {
            _isSubmittingHelp.value = true
            try {
                val result = repository.submitHelpMessage(subject, message)
                result.onSuccess {
                    _snackbarMessage.value = "Help message sent to Admin (${BillingConstants.ADMIN_EMAIL})"
                    onSuccess()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to send help message."
                }
            } finally {
                _isSubmittingHelp.value = false
            }
        }
    }

    fun approvePayment(
        request: PaymentRequest,
        bonusCoins: Int,
        adminNote: String,
        onDone: () -> Unit = {}
    ) {
        if (!isAdmin || _adminBusy.value) return
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                val result = repository.approvePaymentRequest(request, bonusCoins, adminNote)
                result.onSuccess {
                    _snackbarMessage.value = "Approved for ${request.userEmail.ifBlank { request.userName }}"
                    onDone()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Approval failed."
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun rejectPayment(requestId: String, reason: String) {
        if (!isAdmin || _adminBusy.value) return
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                val result = repository.rejectPaymentRequest(requestId, reason)
                result.onSuccess {
                    _snackbarMessage.value = "Payment request rejected"
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Rejection failed."
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun updateUserEntitlements(
        targetUser: UserAccount,
        newCoins: Int,
        newVipTier: VipTier,
        resetTrial: Boolean,
        extendIfSameTier: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        if (!isAdmin || _adminBusy.value) return
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                val result = repository.adminUpdateUserEntitlements(
                    targetUser = targetUser,
                    newCoins = newCoins,
                    newVipTier = newVipTier,
                    resetTrialDates = resetTrial,
                    extendIfSameTier = extendIfSameTier
                )
                result.onSuccess {
                    _snackbarMessage.value = "Updated ${targetUser.email.ifBlank { targetUser.displayName }}"
                    onDone()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to update user."
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun replyHelpMessage(
        messageId: String,
        replyText: String,
        markResolved: Boolean,
        onDone: () -> Unit = {}
    ) {
        if (!isAdmin || _adminBusy.value) return
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                val result = repository.adminReplyHelpMessage(messageId, replyText, markResolved)
                result.onSuccess {
                    _snackbarMessage.value = "Reply sent to user"
                    onDone()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to reply."
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun saveAppConfig(config: AppConfig) {
        if (!isAdmin || _adminBusy.value) return
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                val result = repository.adminSaveAppConfig(config)
                result.onSuccess {
                    _snackbarMessage.value = "App Settings & Update Broadcast Saved!"
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to save settings."
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    companion object {
        private const val TAG = "BillingViewModel"
    }
}
