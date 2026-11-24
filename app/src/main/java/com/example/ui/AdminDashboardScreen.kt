package com.example.ui

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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AccountBillingRepository
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.HelpMessage
import com.example.model.HelpStatus
import com.example.model.PaymentRequest
import com.example.model.PaymentRequestType
import com.example.model.PaymentStatus
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AdminDashboardDialog(
    users: List<UserAccount>,
    payments: List<PaymentRequest>,
    helpMessages: List<HelpMessage>,
    appConfig: AppConfig,
    adminBusy: Boolean,
    onApprovePayment: (PaymentRequest, bonusCoins: Int, adminNote: String) -> Unit,
    onRejectPayment: (requestId: String, reason: String) -> Unit,
    onUpdateUserEntitlements: (UserAccount, newCoins: Int, newVipTier: VipTier, resetTrial: Boolean, extendIfSameTier: Boolean) -> Unit,
    onReplyHelp: (messageId: String, replyText: String, markResolved: Boolean) -> Unit,
    onSaveConfig: (AppConfig) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AdminDashboard(
            users = users,
            payments = payments,
            helpMessages = helpMessages,
            appConfig = appConfig,
            adminBusy = adminBusy,
            onApprovePayment = onApprovePayment,
            onRejectPayment = onRejectPayment,
            onUpdateUserEntitlements = onUpdateUserEntitlements,
            onReplyHelp = onReplyHelp,
            onSaveConfig = onSaveConfig,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun AdminDashboard(
    users: List<UserAccount>,
    payments: List<PaymentRequest>,
    helpMessages: List<HelpMessage>,
    appConfig: AppConfig,
    adminBusy: Boolean,
    onApprovePayment: (PaymentRequest, bonusCoins: Int, adminNote: String) -> Unit,
    onRejectPayment: (requestId: String, reason: String) -> Unit,
    onUpdateUserEntitlements: (UserAccount, newCoins: Int, newVipTier: VipTier, resetTrial: Boolean, extendIfSameTier: Boolean) -> Unit,
    onReplyHelp: (messageId: String, replyText: String, markResolved: Boolean) -> Unit,
    onSaveConfig: (AppConfig) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pendingPaymentsCount = payments.count { it.statusEnum() == PaymentStatus.PENDING }
    val openHelpCount = helpMessages.count { it.statusEnum() == HelpStatus.OPEN }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
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
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = BillingConstants.ADMIN_EMAIL,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        if (onDismiss != null) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("close_admin_dashboard_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close Admin")
                            }
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Users (${users.size})") },
                            modifier = Modifier.testTag("admin_tab_users")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Pay ($pendingPaymentsCount)") },
                            modifier = Modifier.testTag("admin_tab_payments")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Help ($openHelpCount)") },
                            modifier = Modifier.testTag("admin_tab_help")
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Update") },
                            modifier = Modifier.testTag("admin_tab_config")
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
            when (selectedTab) {
                0 -> AdminUsersTab(
                    users = users,
                    payments = payments,
                    adminBusy = adminBusy,
                    onUpdateUser = onUpdateUserEntitlements
                )
                1 -> AdminPaymentsTab(
                    payments = payments,
                    adminBusy = adminBusy,
                    onApprovePayment = onApprovePayment,
                    onRejectPayment = onRejectPayment
                )
                2 -> AdminHelpTab(
                    helpMessages = helpMessages,
                    adminBusy = adminBusy,
                    onReplyHelp = onReplyHelp
                )
                else -> AdminConfigAndUpdateTab(
                    appConfig = appConfig,
                    adminBusy = adminBusy,
                    onSaveConfig = onSaveConfig
                )
            }
        }
    }
}

@Composable
private fun AdminPaymentsTab(
    payments: List<PaymentRequest>,
    adminBusy: Boolean,
    onApprovePayment: (PaymentRequest, Int, String) -> Unit,
    onRejectPayment: (String, String) -> Unit
) {
    val mmkFormat = remember { NumberFormat.getIntegerInstance(Locale.US) }
    var inspectReceiptRequest by remember { mutableStateOf<PaymentRequest?>(null) }
    val bonusCoinsById = remember { mutableStateMapOf<String, String>() }
    var statusFilter by remember { mutableStateOf<PaymentStatus?>(null) }
    var rejectTarget by remember { mutableStateOf<PaymentRequest?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    if (inspectReceiptRequest != null) {
        val req = inspectReceiptRequest!!
        val bmp = remember(req.receiptImageBase64) {
            AccountBillingRepository.decodeBase64Bitmap(req.receiptImageBase64)
        }
        AlertDialog(
            onDismissRequest = { inspectReceiptRequest = null },
            title = { Text("Receipt Proof • ${req.userEmail}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone: ${req.payerPhone} • ${req.paymentMethodEnum().label}")
                    Text("Amount: ${mmkFormat.format(req.amountMmk)} MMK")
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Uploaded receipt screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text("No receipt image attached.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectReceiptRequest = null }) { Text("Close") }
            }
        )
    }

    if (rejectTarget != null) {
        val target = rejectTarget!!
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Reject Payment Request") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason for user") },
                    placeholder = { Text("e.g., Transfer not found") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectPayment(target.id, rejectReason)
                        rejectTarget = null
                        rejectReason = ""
                    }
                ) { Text("Confirm Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
            }
        )
    }

    val verifiedRevenueMmk = remember(payments) {
        payments.filter { it.statusEnum() == PaymentStatus.APPROVED }.sumOf { it.amountMmk }
    }
    val filteredPayments = remember(payments, statusFilter) {
        if (statusFilter == null) payments else payments.filter { it.statusEnum() == statusFilter }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pending: ${payments.count { it.statusEnum() == PaymentStatus.PENDING }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Verified: ${mmkFormat.format(verifiedRevenueMmk)} MMK",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All (${payments.size})") }
            )
            PaymentStatus.entries.forEach { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text(st.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        if (filteredPayments.isEmpty()) {
            Text("No payment requests in this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        filteredPayments.forEach { req ->
            val statusColor = when (req.statusEnum()) {
                PaymentStatus.APPROVED -> MaterialTheme.colorScheme.secondary
                PaymentStatus.REJECTED -> MaterialTheme.colorScheme.error
                PaymentStatus.PENDING -> MaterialTheme.colorScheme.primary
            }
            val rowBonus = bonusCoinsById[req.id] ?: "0"
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val title = if (req.requestTypeEnum() == PaymentRequestType.VIP) {
                            "${req.vipTierEnum().displayLabel} (${req.vipTierEnum().durationMonths} mo)"
                        } else {
                            "${req.requestedCoins} Video Coins"
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(req.statusEnum().label, color = statusColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Gmail: ${req.userEmail.ifBlank { req.userId }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Name: ${req.userName} • Payer Phone: ${req.payerPhone}", style = MaterialTheme.typography.bodySmall)
                    Text("Method: ${req.paymentMethodEnum().label} • Amount: ${mmkFormat.format(req.amountMmk)} MMK", style = MaterialTheme.typography.bodySmall)
                    if (req.transactionRef.isNotBlank()) {
                        Text("Txn Ref: ${req.transactionRef}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (req.note.isNotBlank()) {
                        Text("Note: ${req.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val thumbBmp = remember(req.receiptImageBase64) {
                        AccountBillingRepository.decodeBase64Bitmap(req.receiptImageBase64)
                    }
                    if (thumbBmp != null) {
                        OutlinedButton(
                            onClick = { inspectReceiptRequest = req },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("View Attached Receipt Image")
                        }
                    }

                    if (req.statusEnum() == PaymentStatus.PENDING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = rowBonus,
                                onValueChange = { bonusCoinsById[req.id] = it.filter { ch -> ch.isDigit() }.take(5) },
                                label = { Text("+Bonus Coins") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.75f)
                            )
                            Button(
                                onClick = {
                                    val bonus = rowBonus.toIntOrNull() ?: 0
                                    onApprovePayment(req, bonus, "Approved (${req.paymentMethodEnum().label})")
                                },
                                enabled = !adminBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("approve_payment_${req.id}")
                            ) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve")
                            }
                            OutlinedButton(
                                onClick = { rejectTarget = req },
                                enabled = !adminBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("reject_payment_${req.id}")
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<UserAccount>,
    payments: List<PaymentRequest> = emptyList(),
    adminBusy: Boolean,
    onUpdateUser: (UserAccount, newCoins: Int, newVipTier: VipTier, resetTrial: Boolean, extendIfSameTier: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var tierFilter by remember { mutableStateOf<String>("ALL") }
    var editingUser by remember { mutableStateOf<UserAccount?>(null) }

    val pendingByUserId = remember(payments) {
        payments
            .filter { it.statusEnum() == PaymentStatus.PENDING }
            .groupBy { it.userId }
    }

    if (editingUser != null) {
        val target = editingUser!!
        var coinsText by remember(target.userId) { mutableStateOf(target.coins.toString()) }
        var selectedTier by remember(target.userId) { mutableStateOf(target.effectiveVipTier()) }
        var resetTrial by remember(target.userId) { mutableStateOf(false) }
        var extendSameTier by remember(target.userId) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Upgrade / Manage ${target.email.ifBlank { target.displayName }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current Tier: ${target.effectiveVipTier().displayLabel} • Coins: ${target.coins}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = coinsText,
                        onValueChange = { coinsText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Video Coins Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Subscription Tier:", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VipTier.entries.forEach { tier ->
                            FilterChip(
                                selected = selectedTier == tier,
                                onClick = { selectedTier = tier },
                                label = {
                                    val suffix = if (tier.durationMonths > 0) " (${tier.durationMonths} mo)" else " (3-Day Trial)"
                                    Text("${tier.displayLabel}$suffix")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_select_tier_${tier.firestoreValue.lowercase()}")
                            )
                        }
                    }
                    if (selectedTier != VipTier.NONE && selectedTier == target.vipTierEnum() && target.hasActiveVip()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Extend +${selectedTier.durationMonths} mo",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(checked = extendSameTier, onCheckedChange = { extendSameTier = it })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reset 3-Day Free Trial", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = resetTrial, onCheckedChange = { resetTrial = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUser(
                            target,
                            coinsText.toIntOrNull() ?: target.coins,
                            selectedTier,
                            resetTrial,
                            extendSameTier
                        )
                        editingUser = null
                    },
                    enabled = !adminBusy,
                    modifier = Modifier.testTag("confirm_user_upgrade_button")
                ) { Text("Confirm Upgrade") }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Cancel") }
            }
        )
    }

    val vipCount = remember(users) { users.count { it.effectiveVipTier() != VipTier.NONE } }
    val freeCount = remember(users, vipCount) { (users.size - vipCount).coerceAtLeast(0) }

    val filtered = remember(users, searchQuery, tierFilter) {
        val q = searchQuery.trim().lowercase()
        users.filter { user ->
            val matchesQuery = q.isEmpty() ||
                user.email.lowercase().contains(q) ||
                user.displayName.lowercase().contains(q) ||
                user.userId.lowercase().contains(q)
            val isVip = user.effectiveVipTier() != VipTier.NONE
            val matchesTier = when (tierFilter) {
                "VIP" -> isVip
                "FREE" -> !isVip
                else -> true
            }
            matchesQuery && matchesTier
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Users: ${users.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "VIP: $vipCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Free: $freeCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = tierFilter == "ALL",
                onClick = { tierFilter = "ALL" },
                label = { Text("All (${users.size})") }
            )
            FilterChip(
                selected = tierFilter == "FREE",
                onClick = { tierFilter = "FREE" },
                label = { Text("Free ($freeCount)") }
            )
            FilterChip(
                selected = tierFilter == "VIP",
                onClick = { tierFilter = "VIP" },
                label = { Text("VIP ($vipCount)") }
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search user by Gmail or name") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            Text(
                text = "No users found in Firestore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        filtered.forEach { user ->
            val trial = QuotaPolicy.evaluateTrialStatus(user)
            val activeVip = user.effectiveVipTier()
            val userPendingPayments = pendingByUserId[user.userId].orEmpty()
            val statusBadgeLabel = when {
                user.isAdminAccount() -> "ADMIN"
                activeVip != VipTier.NONE -> activeVip.displayLabel.uppercase()
                else -> "FREE"
            }
            val badgeContainerColor = when {
                user.isAdminAccount() || activeVip != VipTier.NONE -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val badgeContentColor = when {
                user.isAdminAccount() || activeVip != VipTier.NONE -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (userPendingPayments.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_card_${user.userId}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeContainerColor,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeContentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (userPendingPayments.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${userPendingPayments.size} Pending Pay",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(user.email.ifBlank { user.userId }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(user.displayName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Coins: ${user.coins} • Tier: ${activeVip.displayLabel} • Trial: ${trial.daysUsedCount}/3 days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activeVip != VipTier.NONE) {
                            Text(
                                text = "VIP Exp: ${QuotaPolicy.formatDateYangon(user.vipExpiresAtMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { editingUser = user },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(44.dp)
                            .testTag("upgrade_user_${user.userId}")
                    ) {
                        Text("Upgrade")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminHelpTab(
    helpMessages: List<HelpMessage>,
    adminBusy: Boolean,
    onReplyHelp: (messageId: String, replyText: String, markResolved: Boolean) -> Unit
) {
    var replyingTarget by remember { mutableStateOf<HelpMessage?>(null) }
    var replyText by remember { mutableStateOf("") }

    if (replyingTarget != null) {
        val target = replyingTarget!!
        AlertDialog(
            onDismissRequest = { replyingTarget = null },
            title = { Text("Reply to ${target.userEmail}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.subject, style = MaterialTheme.typography.titleSmall)
                    Text(target.message, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it.take(2000) },
                        label = { Text("Your Reply") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReplyHelp(target.id, replyText, false)
                        replyingTarget = null
                        replyText = ""
                    },
                    enabled = !adminBusy && replyText.isNotBlank()
                ) { Text("Send Reply") }
            },
            dismissButton = {
                TextButton(onClick = { replyingTarget = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (helpMessages.isEmpty()) {
            Text("No user help messages yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        helpMessages.forEach { msg ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(msg.userEmail.ifBlank { msg.userName }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(msg.statusEnum().label, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(msg.subject, style = MaterialTheme.typography.titleSmall)
                    Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                    msg.adminReply?.takeIf { it.isNotBlank() }?.let { prev ->
                        Text("Replied: $prev", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    OutlinedButton(
                        onClick = {
                            replyText = msg.adminReply.orEmpty()
                            replyingTarget = msg
                        },
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(if (msg.adminReply.isNullOrBlank()) "Reply to User" else "Edit Reply")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminConfigAndUpdateTab(
    appConfig: AppConfig,
    adminBusy: Boolean,
    onSaveConfig: (AppConfig) -> Unit
) {
    var coinCost by remember(appConfig) { mutableStateOf(appConfig.coinCostPerVideo.toString()) }
    var kbzNumber by remember(appConfig) { mutableStateOf(appConfig.kbzPayNumber) }
    var waveNumber by remember(appConfig) { mutableStateOf(appConfig.wavePayNumber) }
    var v1Price by remember(appConfig) { mutableStateOf(appConfig.vipV1Price.toString()) }
    var v2Price by remember(appConfig) { mutableStateOf(appConfig.vipV2Price.toString()) }
    var v3Price by remember(appConfig) { mutableStateOf(appConfig.vipV3Price.toString()) }

    var annActive by remember(appConfig) { mutableStateOf(appConfig.announcementActive) }
    var annTitle by remember(appConfig) { mutableStateOf(appConfig.announcementTitle) }
    var annMessage by remember(appConfig) { mutableStateOf(appConfig.announcementMessage) }
    var annVersion by remember(appConfig) { mutableStateOf(appConfig.announcementVersion) }
    var updateUrl by remember(appConfig) { mutableStateOf(appConfig.updateUrl) }

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
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Coins & Payment Settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = coinCost,
                    onValueChange = { coinCost = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Coin Cost Per Video") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kbzNumber,
                    onValueChange = { kbzNumber = it.take(40) },
                    label = { Text("KBZPay Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = waveNumber,
                    onValueChange = { waveNumber = it.take(40) },
                    label = { Text("WavePay Phone Number (Editable)") },
                    placeholder = { Text("Enter your WavePay phone number") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_wavepay_number_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = v1Price,
                        onValueChange = { v1Price = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("VIP V1 (MMK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = v2Price,
                        onValueChange = { v2Price = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("VIP V2 (MMK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = v3Price,
                        onValueChange = { v3Price = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("VIP V3 (MMK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Mess App Update Broadcast", style = MaterialTheme.typography.titleMedium)
                            Text("Displays on every user's screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = annActive,
                        onCheckedChange = { annActive = it },
                        modifier = Modifier.testTag("admin_announcement_switch")
                    )
                }
                OutlinedTextField(
                    value = annVersion,
                    onValueChange = { annVersion = it.take(40) },
                    label = { Text("Version Tag (e.g., v2.1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = annTitle,
                    onValueChange = { annTitle = it.take(160) },
                    label = { Text("Update Popup Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = annMessage,
                    onValueChange = { annMessage = it.take(2000) },
                    label = { Text("On-Screen Update Message") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_announcement_message_input")
                )
                OutlinedTextField(
                    value = updateUrl,
                    onValueChange = { updateUrl = it.take(500) },
                    label = { Text("Optional Download / Telegram Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                onSaveConfig(
                    AppConfig(
                        coinCostPerVideo = coinCost.toIntOrNull() ?: BillingConstants.DEFAULT_COIN_COST_PER_VIDEO,
                        kbzPayNumber = kbzNumber.trim().ifBlank { BillingConstants.DEFAULT_KBZPAY_NUMBER },
                        wavePayNumber = waveNumber.trim(),
                        vipV1Price = v1Price.toIntOrNull() ?: BillingConstants.VIP_V1_DEFAULT_MMK,
                        vipV2Price = v2Price.toIntOrNull() ?: BillingConstants.VIP_V2_DEFAULT_MMK,
                        vipV3Price = v3Price.toIntOrNull() ?: BillingConstants.VIP_V3_DEFAULT_MMK,
                        announcementActive = annActive,
                        announcementTitle = annTitle.trim(),
                        announcementMessage = annMessage.trim(),
                        announcementVersion = annVersion.trim(),
                        updateUrl = updateUrl.trim()
                    )
                )
            },
            enabled = !adminBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_admin_config_button")
        ) {
            Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Settings & Publish Update")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
