package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AccountBillingRepository
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.CoinPackage
import com.example.model.HelpMessage
import com.example.model.HelpStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentRequest
import com.example.model.PaymentRequestType
import com.example.model.PaymentStatus
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import com.example.ui.theme.MyanmarScriptFamily
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SubscriptionBillingScreen(
    initialTab: Int = 0,
    quotaDeniedMessage: String? = null,
    userAccount: UserAccount?,
    appConfig: AppConfig,
    myPayments: List<PaymentRequest>,
    myHelpMessages: List<HelpMessage>,
    isSubmittingPayment: Boolean,
    isSubmittingHelp: Boolean,
    onSubmitPayment: (
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
    ) -> Unit,
    onSubmitHelp: (subject: String, message: String, onSuccess: () -> Unit) -> Unit,
    onNotify: (String) -> Unit
) {
    BillingAndHelpContentScaffold(
        initialTab = initialTab,
        quotaDeniedMessage = quotaDeniedMessage,
        userAccount = userAccount,
        appConfig = appConfig,
        myPayments = myPayments,
        myHelpMessages = myHelpMessages,
        isSubmittingPayment = isSubmittingPayment,
        isSubmittingHelp = isSubmittingHelp,
        onSubmitPayment = onSubmitPayment,
        onSubmitHelp = onSubmitHelp,
        onNotify = onNotify,
        onDismiss = null
    )
}

@Composable
fun BillingAndHelpDialog(
    initialTab: Int = 0,
    quotaDeniedMessage: String? = null,
    userAccount: UserAccount?,
    appConfig: AppConfig,
    myPayments: List<PaymentRequest>,
    myHelpMessages: List<HelpMessage>,
    isSubmittingPayment: Boolean,
    isSubmittingHelp: Boolean,
    onSubmitPayment: (
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
    ) -> Unit,
    onSubmitHelp: (subject: String, message: String, onSuccess: () -> Unit) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BillingAndHelpContentScaffold(
            initialTab = initialTab,
            quotaDeniedMessage = quotaDeniedMessage,
            userAccount = userAccount,
            appConfig = appConfig,
            myPayments = myPayments,
            myHelpMessages = myHelpMessages,
            isSubmittingPayment = isSubmittingPayment,
            isSubmittingHelp = isSubmittingHelp,
            onSubmitPayment = onSubmitPayment,
            onSubmitHelp = onSubmitHelp,
            onNotify = onNotify,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun BillingAndHelpContentScaffold(
    initialTab: Int,
    quotaDeniedMessage: String?,
    userAccount: UserAccount?,
    appConfig: AppConfig,
    myPayments: List<PaymentRequest>,
    myHelpMessages: List<HelpMessage>,
    isSubmittingPayment: Boolean,
    isSubmittingHelp: Boolean,
    onSubmitPayment: (
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
    ) -> Unit,
    onSubmitHelp: (subject: String, message: String, onSuccess: () -> Unit) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: (() -> Unit)?
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Rounded.WorkspacePremium else Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == 0) "Subscription & Billing" else "User Help Center",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        if (onDismiss != null) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("close_billing_dialog_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("VIP & Coins") },
                            modifier = Modifier.testTag("billing_tab_vip_coins")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Help (${myHelpMessages.size})") },
                            modifier = Modifier.testTag("billing_tab_help")
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (selectedTab == 0) {
                VipAndCoinsContent(
                    quotaDeniedMessage = quotaDeniedMessage,
                    userAccount = userAccount,
                    appConfig = appConfig,
                    myPayments = myPayments,
                    isSubmittingPayment = isSubmittingPayment,
                    onSubmitPayment = onSubmitPayment,
                    onNotify = onNotify
                )
            } else {
                UserHelpContent(
                    myHelpMessages = myHelpMessages,
                    isSubmittingHelp = isSubmittingHelp,
                    onSubmitHelp = onSubmitHelp
                )
            }
        }
    }
}

@Composable
private fun VipAndCoinsContent(
    quotaDeniedMessage: String?,
    userAccount: UserAccount?,
    appConfig: AppConfig,
    myPayments: List<PaymentRequest>,
    isSubmittingPayment: Boolean,
    onSubmitPayment: (
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
    ) -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val mmkFormat = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val account = userAccount ?: UserAccount()
    val trialStatus = remember(account) { QuotaPolicy.evaluateTrialStatus(account) }
    val activeVip = remember(account) { account.effectiveVipTier() }

    var purchaseMode by remember { mutableStateOf(PaymentRequestType.VIP) }
    var selectedVip by remember { mutableStateOf(VipTier.VIP_V1) }
    var selectedCoinPack by remember { mutableStateOf(BillingConstants.DEFAULT_COIN_PACKAGES.first()) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.KBZPAY) }
    var payerPhone by remember { mutableStateOf("") }
    var txnRef by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    val receiptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) receiptUri = uri }
    )

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!quotaDeniedMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Free Trial / Coin Limit Reached",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = quotaDeniedMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Current Account Summary Card
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = account.email.ifBlank { "Signed-in Creator" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    val statusBadgeLabel = when {
                        account.isAdminAccount() -> "ADMIN"
                        activeVip != VipTier.NONE -> activeVip.displayLabel.uppercase()
                        else -> "FREE"
                    }
                    val badgeContainerColor = when {
                        account.isAdminAccount() || activeVip != VipTier.NONE -> MaterialTheme.colorScheme.secondaryContainer
                        trialStatus.trialPermanentlyExpired -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val badgeContentColor = when {
                        account.isAdminAccount() || activeVip != VipTier.NONE -> MaterialTheme.colorScheme.onSecondaryContainer
                        trialStatus.trialPermanentlyExpired -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = badgeContainerColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("user_status_badge")
                    ) {
                        Text(
                            text = statusBadgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeContentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Video Coins", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${account.coins} Coins",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${appConfig.coinCostPerVideo} Coins / video",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Plan Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (account.isAdminAccount()) {
                                Text("ADMIN", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("Unlimited Control", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (activeVip != VipTier.NONE) {
                                Text(activeVip.displayLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("Exp: ${QuotaPolicy.formatDateYangon(account.vipExpiresAtMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Free Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                val trialLabel = when {
                                    trialStatus.trialPermanentlyExpired -> "3-Day Trial Ended"
                                    trialStatus.usedToday -> "Today 0/1 left (${trialStatus.daysUsedCount}/3 days)"
                                    else -> "Today 1/1 left (${trialStatus.daysRemainingCount}/3 days)"
                                }
                                Text(trialLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
                Text(
                    text = "Free Account စည်းမျဉ်း: ၁ ရက်လျှင် Video ၁ ပုဒ်နှုန်းဖြင့် ၃ ရက်အထိသာ အခမဲ့သုံးနိုင်ပါမည်။ ၃ ရက်ပြည့်ပါက Video Coins သို့မဟုတ် VIP ဝယ်ယူမှသာ ဆက်သုံးနိုင်ပါမည်။",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MyanmarScriptFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Account Free vs Account VIP Comparison Matrix
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("free_vs_vip_comparison_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Account Free vs Account VIP (အခမဲ့ နှင့် VIP ကွာခြားချက်)",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MyanmarScriptFamily),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ACCOUNT FREE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("• 1 Video / Day", style = MaterialTheme.typography.bodySmall)
                            Text("• 3 Days Trial Max", style = MaterialTheme.typography.bodySmall)
                            Text("• Basic Recap & Dub", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ACCOUNT VIP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text("• Unlimited Videos", style = MaterialTheme.typography.bodySmall)
                            Text("• Full Start→End Dub", style = MaterialTheme.typography.bodySmall)
                            Text("• Copyright Shield Pro", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Purchase Type Toggle: VIP vs Video Coins
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = purchaseMode == PaymentRequestType.VIP,
                onClick = { purchaseMode = PaymentRequestType.VIP },
                label = { Text("VIP Packages (V1 / V2 / V3)") },
                leadingIcon = { Icon(Icons.Rounded.WorkspacePremium, null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("select_vip_mode_chip")
            )
            FilterChip(
                selected = purchaseMode == PaymentRequestType.COINS,
                onClick = { purchaseMode = PaymentRequestType.COINS },
                label = { Text("Video Coins") },
                leadingIcon = { Icon(Icons.Rounded.MonetizationOn, null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("select_coins_mode_chip")
            )
        }

        if (purchaseMode == PaymentRequestType.VIP) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(VipTier.VIP_V1, VipTier.VIP_V2, VipTier.VIP_V3).forEach { tier ->
                    val price = appConfig.priceForVip(tier)
                    val isSelected = selectedVip == tier
                    Surface(
                        onClick = { selectedVip = tier },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vip_tier_${tier.firestoreValue.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tier.burmeseLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Unlimited AI Scripts & Video Dubbing for ${tier.durationMonths} month(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${mmkFormat.format(price)} MMK",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("vip_buy_now_${tier.firestoreValue.lowercase()}")
                                ) {
                                    Text(
                                        text = if (isSelected) "Selected ✓" else "Select & Pay",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BillingConstants.DEFAULT_COIN_PACKAGES.forEach { pack: CoinPackage ->
                    val isSelected = selectedCoinPack.id == pack.id
                    val estVideos = if (appConfig.coinCostPerVideo > 0) pack.coins / appConfig.coinCostPerVideo else pack.coins
                    Surface(
                        onClick = { selectedCoinPack = pack },
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coin_pack_${pack.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pack.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "~$estVideos videos (${appConfig.coinCostPerVideo} Coins/video)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${mmkFormat.format(pack.priceMmk)} MMK",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("coin_buy_now_${pack.id}")
                                ) {
                                    Text(
                                        text = if (isSelected) "Selected ✓" else "Buy Pack",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step-by-step Instructions & KBZPay / WavePay Receiver Accounts Card
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payment_instructions_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to Pay via KBZPay / WavePay",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "1. Select a VIP plan (V1, V2, V3) or Video Coins package above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "2. Copy the KBZPay or WavePay phone number below and transfer the exact MMK amount.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "3. Enter your sender phone number, optional Transaction ID & payment slip photo below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "4. Submit proof — Admin (${BillingConstants.ADMIN_EMAIL}) verifies and upgrades your account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "ငွေလွှဲရန် ဖုန်းနံပါတ်များ",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // KBZPay Row
                val kbzPhone = appConfig.kbzPayNumber.ifBlank { BillingConstants.DEFAULT_KBZPAY_NUMBER }
                PaymentNumberRow(
                    methodLabel = "KBZPay",
                    phoneNumber = kbzPhone,
                    selected = selectedMethod == PaymentMethod.KBZPAY,
                    onSelect = { selectedMethod = PaymentMethod.KBZPAY },
                    onCopy = {
                        clipboard.setText(AnnotatedString(kbzPhone))
                        onNotify("KBZPay $kbzPhone copied")
                    }
                )

                // WavePay Row
                val wavePhone = appConfig.wavePayNumber.trim()
                PaymentNumberRow(
                    methodLabel = "WavePay",
                    phoneNumber = wavePhone.ifBlank { "Admin ထံ ဆက်သွယ်ပါ (Not set yet)" },
                    selected = selectedMethod == PaymentMethod.WAVEPAY,
                    onSelect = { selectedMethod = PaymentMethod.WAVEPAY },
                    onCopy = {
                        if (wavePhone.isNotBlank()) {
                            clipboard.setText(AnnotatedString(wavePhone))
                            onNotify("WavePay $wavePhone copied")
                        } else {
                            onNotify("Admin has not set WavePay number yet; use KBZPay $kbzPhone")
                        }
                    }
                )
            }
        }

        // Payment Proof Form
        val targetAmount = if (purchaseMode == PaymentRequestType.VIP) {
            appConfig.priceForVip(selectedVip)
        } else {
            selectedCoinPack.priceMmk
        }
        val targetCoins = if (purchaseMode == PaymentRequestType.VIP) 0 else selectedCoinPack.coins
        val targetVip = if (purchaseMode == PaymentRequestType.VIP) selectedVip else VipTier.NONE
        val selectedCheckoutLabel = if (purchaseMode == PaymentRequestType.VIP) {
            selectedVip.burmeseLabel
        } else {
            selectedCoinPack.label
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("checkout_summary_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected Checkout",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = selectedCheckoutLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${mmkFormat.format(targetAmount)} MMK",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = "ငွေလွှဲအထောက်အထား ပေးပို့ရန် (${mmkFormat.format(targetAmount)} MMK)",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily)
                )

                OutlinedTextField(
                    value = payerPhone,
                    onValueChange = { payerPhone = it.take(30) },
                    label = { Text("သင်ငွေလွှဲခဲ့သော ဖုန်းနံပါတ် (Your Phone)") },
                    placeholder = { Text("09xxxxxxxxx") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payer_phone_input")
                )

                OutlinedTextField(
                    value = txnRef,
                    onValueChange = { txnRef = it.take(80) },
                    label = { Text("Transaction ID / ဂဏန်းနောက်ဆုံး ၆ လုံး (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("txn_ref_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("မှတ်ချက် / နာမည် (Optional Note)") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        receiptPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_receipt_photo_button")
                ) {
                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (receiptUri != null) "Receipt Photo Attached (Change)" else "Attach Payment Slip Photo (Optional)")
                }

                if (receiptUri != null) {
                    AsyncImage(
                        model = receiptUri,
                        contentDescription = "Selected payment receipt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Button(
                    onClick = {
                        if (payerPhone.trim().length < 5) {
                            onNotify("ကျေးဇူးပြု၍ သင့်ဖုန်းနံပါတ် မှန်ကန်စွာ ထည့်သွင်းပါ")
                            return@Button
                        }
                        onSubmitPayment(
                            purchaseMode,
                            targetVip,
                            targetCoins,
                            targetAmount,
                            selectedMethod,
                            payerPhone,
                            txnRef,
                            note,
                            receiptUri
                        ) {
                            payerPhone = ""
                            txnRef = ""
                            note = ""
                            receiptUri = null
                        }
                    },
                    enabled = !isSubmittingPayment,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_payment_button")
                ) {
                    if (isSubmittingPayment) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting...")
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Payment Proof")
                    }
                }
            }
        }

        // My Payment Requests History
        if (myPayments.isNotEmpty()) {
            Text("My Payment Submissions", style = MaterialTheme.typography.titleMedium)
            myPayments.forEach { req ->
                val statusColor = when (req.statusEnum()) {
                    PaymentStatus.APPROVED -> MaterialTheme.colorScheme.secondary
                    PaymentStatus.REJECTED -> MaterialTheme.colorScheme.error
                    PaymentStatus.PENDING -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val itemTitle = if (req.requestTypeEnum() == PaymentRequestType.VIP) {
                                req.vipTierEnum().displayLabel
                            } else {
                                "${req.requestedCoins} Coins"
                            }
                            Text(
                                text = "$itemTitle • ${mmkFormat.format(req.amountMmk)} MMK",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = req.statusEnum().label,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${req.paymentMethodEnum().label} • From: ${req.payerPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        req.adminNote?.takeIf { it.isNotBlank() }?.let { aNote ->
                            Text(
                                text = "Admin: $aNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PaymentNumberRow(
    methodLabel: String,
    phoneNumber: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(methodLabel, style = MaterialTheme.typography.labelLarge)
                    Text(phoneNumber, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            OutlinedButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun UserHelpContent(
    myHelpMessages: List<HelpMessage>,
    isSubmittingHelp: Boolean,
    onSubmitHelp: (subject: String, message: String, onSuccess: () -> Unit) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Admin ထံ အကူအညီ / မေးမြန်းစာ ပေးပို့ရန်",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MyanmarScriptFamily)
                )
                Text(
                    text = "Admin Gmail: ${BillingConstants.ADMIN_EMAIL}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(140) },
                    label = { Text("ခေါင်းစဉ် (Subject)") },
                    placeholder = { Text("ဥပမာ - VIP V1 ဝယ်ယူမှု / Error မေးမြန်းချက်") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("help_subject_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(2000) },
                    label = { Text("မေးမြန်းလိုသော အကြောင်းအရာ (Message)") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("help_message_input")
                )
                Button(
                    onClick = {
                        onSubmitHelp(subject, message) {
                            subject = ""
                            message = ""
                        }
                    },
                    enabled = !isSubmittingHelp && subject.isNotBlank() && message.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_help_message_button")
                ) {
                    if (isSubmittingHelp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Message to Admin")
                    }
                }
            }
        }

        Text("My Help Messages (${myHelpMessages.size})", style = MaterialTheme.typography.titleMedium)

        if (myHelpMessages.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No help messages sent yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            myHelpMessages.forEach { msg ->
                val statusColor = when (msg.statusEnum()) {
                    HelpStatus.REPLIED, HelpStatus.RESOLVED -> MaterialTheme.colorScheme.secondary
                    HelpStatus.OPEN -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(msg.subject, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                text = msg.statusEnum().label,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                        msg.adminReply?.takeIf { it.isNotBlank() }?.let { reply ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Admin Reply:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(reply, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
