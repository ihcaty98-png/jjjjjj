package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.R
import com.example.ai.RecapAiRepository
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.AccountBillingRepository
import com.example.data.ApiKeyStore
import com.example.data.RecapRepository
import com.example.data.UserStudioPreferencesStore
import com.example.data.VoiceCloneStore
import com.example.model.GenerationQuotaDecision
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.ui.theme.MyanmarScriptFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_STUDIO = "studio"
const val ROUTE_DUB = "dub"
const val ROUTE_VAULT = "vault"
const val ROUTE_BILLING = "subscription_billing"
const val ROUTE_SCRIPT_DETAIL = "script_detail/{scriptId}"

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        key(user.uid) {
            AuthenticatedAppShell(user = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedAppShell(
    user: FirebaseUser
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: RecapViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RecapViewModel(
                    repository = RecapRepository(db),
                    aiRepository = RecapAiRepository(),
                    currentUserId = user.uid
                )
            }
        }
    )

    val dubViewModel: VideoDubViewModel = viewModel(
        key = "dub_${user.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                VideoDubViewModel(voiceCloneStore = VoiceCloneStore(app))
            }
        }
    )

    val billingViewModel: BillingViewModel = viewModel(
        key = "billing_${user.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                BillingViewModel(
                    repository = AccountBillingRepository(app),
                    currentUser = user
                )
            }
        }
    )

    LaunchedEffect(context) {
        ApiKeyStore.init(context.applicationContext)
        UserStudioPreferencesStore.init(context.applicationContext)
        val initialPrefs = UserStudioPreferencesStore.state.value
        dubViewModel.setKeepSoftOriginalBgm(!initialPrefs.defaultMuteOriginalEnglish)
        dubViewModel.setCopyrightShieldEnabled(initialPrefs.defaultCopyrightShield)
    }

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val dubState by dubViewModel.uiState.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val apiKeyState by ApiKeyStore.state.collectAsStateWithLifecycle()
    val studioPrefs by UserStudioPreferencesStore.state.collectAsStateWithLifecycle()
    val userAccount by billingViewModel.userAccount.collectAsStateWithLifecycle()
    val appConfig by billingViewModel.appConfig.collectAsStateWithLifecycle()
    val myPayments by billingViewModel.myPaymentRequests.collectAsStateWithLifecycle()
    val myHelpMessages by billingViewModel.myHelpMessages.collectAsStateWithLifecycle()
    val isSubmittingPayment by billingViewModel.isSubmittingPayment.collectAsStateWithLifecycle()
    val isSubmittingHelp by billingViewModel.isSubmittingHelp.collectAsStateWithLifecycle()
    val adminUsers by billingViewModel.adminUsers.collectAsStateWithLifecycle()
    val adminPayments by billingViewModel.adminPayments.collectAsStateWithLifecycle()
    val adminHelpMessages by billingViewModel.adminHelpMessages.collectAsStateWithLifecycle()
    val adminBusy by billingViewModel.adminBusy.collectAsStateWithLifecycle()
    val dismissedAnnouncementSig by billingViewModel.dismissedAnnouncementSignature.collectAsStateWithLifecycle()

    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val dubSnackbarMessage by dubViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val billingSnackbarMessage by billingViewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }
    LaunchedEffect(dubSnackbarMessage) {
        val msg = dubSnackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            dubViewModel.consumeSnackbar()
        }
    }
    LaunchedEffect(billingSnackbarMessage) {
        val msg = billingSnackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            billingViewModel.consumeSnackbar()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetailRoute = currentRoute?.startsWith("script_detail/") == true

    var showAccountMenu by remember { mutableStateOf(false) }
    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showBillingDialog by remember { mutableStateOf(false) }
    var billingInitialTab by remember { mutableIntStateOf(0) }
    var quotaDeniedMessage by remember { mutableStateOf<String?>(null) }
    var showAdminDialog by remember { mutableStateOf(false) }

    val effectiveAccount = userAccount ?: UserAccount(
        userId = user.uid,
        email = user.email.orEmpty(),
        displayName = user.displayName.orEmpty().ifBlank { "Creator" },
        trialStartDate = QuotaPolicy.todayDateKeyYangon()
    )
    val quotaDecision = remember(effectiveAccount, appConfig) {
        QuotaPolicy.evaluateGenerationAccess(effectiveAccount, appConfig)
    }
    val accessBadgeText = remember(quotaDecision, effectiveAccount) {
        when (quotaDecision) {
            is GenerationQuotaDecision.AllowedAdmin ->
                "Admin Mode • Unlimited (${user.email ?: "firechatmyanmar@gmail.com"})"
            is GenerationQuotaDecision.AllowedVip ->
                "${quotaDecision.tier.displayLabel} Active • Unlimited (${QuotaPolicy.formatDateYangon(quotaDecision.expiresAtMs)} ထိ)"
            is GenerationQuotaDecision.AllowedFreeTrial ->
                "Free 3-Day Trial (Day ${quotaDecision.dayNumber}/3 • 1 Video/Day) • Coins: ${effectiveAccount.coins}"
            is GenerationQuotaDecision.AllowedCoins ->
                "Coins Balance: ${effectiveAccount.coins} Coins • Cost: ${quotaDecision.costCoins} Coins/Video"
            is GenerationQuotaDecision.Denied ->
                quotaDecision.reasonBurmese
        }
    }

    val runWithQuotaGate: ((suspend () -> Unit) -> Unit) -> Unit = { proceed ->
        val latest = billingViewModel.evaluateCurrentQuota()
        if (latest is GenerationQuotaDecision.Denied) {
            quotaDeniedMessage = latest.reasonBurmese
            billingInitialTab = 0
            showBillingDialog = true
            billingViewModel.showMessage(latest.reasonEnglish)
        } else {
            proceed { billingViewModel.consumeQuotaAfterSuccess() }
        }
    }

    val announcementSignature = remember(appConfig) {
        "${appConfig.announcementVersion}|${appConfig.announcementTitle}|${appConfig.announcementMessage}"
    }
    val shouldShowAnnouncement = appConfig.announcementActive &&
        appConfig.announcementMessage.isNotBlank() &&
        dismissedAnnouncementSig != announcementSignature

    if (shouldShowAnnouncement && !showAdminDialog && !showBillingDialog && !showApiKeyDialog && !showAccountSettingsDialog) {
        AlertDialog(
            onDismissRequest = { billingViewModel.dismissAnnouncement(appConfig) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = {
                Text(
                    text = appConfig.announcementTitle.ifBlank { "App Update / Announcement" },
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MyanmarScriptFamily)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (appConfig.announcementVersion.isNotBlank()) {
                        Text(
                            text = "Version: ${appConfig.announcementVersion}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = appConfig.announcementMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MyanmarScriptFamily)
                    )
                }
            },
            confirmButton = {
                if (appConfig.updateUrl.isNotBlank()) {
                    Button(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appConfig.updateUrl))
                                context.startActivity(intent)
                            }
                            billingViewModel.dismissAnnouncement(appConfig)
                        }
                    ) {
                        Text("Update Now")
                    }
                } else {
                    Button(onClick = { billingViewModel.dismissAnnouncement(appConfig) }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { billingViewModel.dismissAnnouncement(appConfig) }) {
                    Text("Close")
                }
            }
        )
    }

    if (showApiKeyDialog) {
        ApiKeySettingsDialog(
            apiKeyState = apiKeyState,
            onSaved = { msg -> viewModel.showMessage(msg) },
            onCleared = { viewModel.showMessage("Custom API key cleared") },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    if (showBillingDialog) {
        BillingAndHelpDialog(
            initialTab = billingInitialTab,
            quotaDeniedMessage = quotaDeniedMessage,
            userAccount = effectiveAccount,
            appConfig = appConfig,
            myPayments = myPayments,
            myHelpMessages = myHelpMessages,
            isSubmittingPayment = isSubmittingPayment,
            isSubmittingHelp = isSubmittingHelp,
            onSubmitPayment = { type, vipTier, coins, amountMmk, method, phone, txRef, note, uri, onDone ->
                billingViewModel.submitPaymentRequest(
                    context = context,
                    requestType = type,
                    vipTier = vipTier,
                    requestedCoins = coins,
                    amountMmk = amountMmk,
                    paymentMethod = method,
                    payerPhone = phone,
                    transactionRef = txRef,
                    note = note,
                    receiptUri = uri,
                    onSuccess = onDone
                )
            },
            onSubmitHelp = { subject, message, onDone ->
                billingViewModel.submitHelpMessage(subject, message, onDone)
            },
            onNotify = { msg -> billingViewModel.showMessage(msg) },
            onDismiss = {
                showBillingDialog = false
                quotaDeniedMessage = null
            }
        )
    }

    if (showAdminDialog && billingViewModel.isAdmin) {
        AdminDashboardDialog(
            users = adminUsers,
            payments = adminPayments,
            helpMessages = adminHelpMessages,
            appConfig = appConfig,
            adminBusy = adminBusy,
            onApprovePayment = { req, bonus, note ->
                billingViewModel.approvePayment(req, bonus, note)
            },
            onRejectPayment = { reqId, reason ->
                billingViewModel.rejectPayment(reqId, reason)
            },
            onUpdateUserEntitlements = { targetUser, coins, vipTier, resetTrial, extendSameTier ->
                billingViewModel.updateUserEntitlements(targetUser, coins, vipTier, resetTrial, extendSameTier)
            },
            onReplyHelp = { msgId, reply, resolved ->
                billingViewModel.replyHelpMessage(msgId, reply, resolved)
            },
            onSaveConfig = { cfg ->
                billingViewModel.saveAppConfig(cfg)
            },
            onDismiss = { showAdminDialog = false }
        )
    }

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (showAccountSettingsDialog) {
        UserAccountSettingsDialog(
            user = user,
            userAccount = effectiveAccount,
            appConfig = appConfig,
            apiKeyState = apiKeyState,
            studioPrefs = studioPrefs,
            onSelectBackgroundStyle = UserStudioPreferencesStore::setBackgroundStyle,
            onSwarmAnimationChanged = UserStudioPreferencesStore::setSwarmAnimationEnabled,
            onDefaultMuteChanged = { mute ->
                UserStudioPreferencesStore.setDefaultMuteOriginalEnglish(mute)
                dubViewModel.setKeepSoftOriginalBgm(!mute)
            },
            onDefaultCopyrightChanged = { enabled ->
                UserStudioPreferencesStore.setDefaultCopyrightShield(enabled)
                dubViewModel.setCopyrightShieldEnabled(enabled)
            },
            onOpenVipStore = {
                quotaDeniedMessage = null
                navigateTab(ROUTE_BILLING)
            },
            onOpenBuyCoins = {
                quotaDeniedMessage = null
                billingInitialTab = 0
                showBillingDialog = true
            },
            onOpenApiKeySettings = { showApiKeyDialog = true },
            onOpenHelpSupport = {
                quotaDeniedMessage = null
                billingInitialTab = 1
                showBillingDialog = true
            },
            onSignOut = { signOut(credentialManager, scope) },
            onNotify = { msg -> viewModel.showMessage(msg) },
            onDismiss = { showAccountSettingsDialog = false }
        )
    }

    val isAnyAiProcessing = studioState.generationState is StudioGenerationState.Processing ||
        dubState.pipelineState is DubPipelineState.Translating ||
        dubState.pipelineState is DubPipelineState.SynthesizingVoice ||
        dubState.pipelineState is DubPipelineState.RenderingVideo

    ReactiveStudioBackground(
        style = studioPrefs.backgroundStyle,
        isProcessingActive = isAnyAiProcessing,
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!isDetailRoute) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon_recap),
                                        contentDescription = "Fire RecapScript Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                            val hasVip = effectiveAccount.hasActiveVip()
                            val headerPillText = when {
                                billingViewModel.isAdmin -> "ADMIN"
                                hasVip -> effectiveAccount.effectiveVipTier().displayLabel
                                else -> "${effectiveAccount.coins}c"
                            }
                            val pillColor = if (hasVip || billingViewModel.isAdmin) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            OutlinedButton(
                                onClick = {
                                    quotaDeniedMessage = null
                                    navigateTab(ROUTE_BILLING)
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .height(38.dp)
                                    .padding(end = 4.dp)
                                    .testTag("header_billing_button")
                            ) {
                                Icon(
                                    imageVector = if (hasVip) Icons.Rounded.WorkspacePremium else Icons.Rounded.MonetizationOn,
                                    contentDescription = "Coins and VIP Store",
                                    tint = pillColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = headerPillText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = pillColor,
                                    maxLines = 1
                                )
                            }

                            if (billingViewModel.isAdmin) {
                                IconButton(
                                    onClick = { showAdminDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("header_admin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AdminPanelSettings,
                                        contentDescription = "Admin Control Center",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            val keyConfigured = apiKeyState.hasEffectiveKey
                            val keyBadgeColor = if (keyConfigured) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                            IconButton(
                                onClick = { showApiKeyDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("api_key_settings_button")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Rounded.Key,
                                        contentDescription = "Open API Key Settings",
                                        tint = keyBadgeColor
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(keyBadgeColor)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showAccountSettingsDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("header_account_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Account and Studio Settings",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showAccountMenu = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("account_menu_button")
                                ) {
                                    if (user.photoUrl != null) {
                                        AsyncImage(
                                            model = user.photoUrl,
                                            contentDescription = "Google account profile",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = (user.displayName?.firstOrNull() ?: user.email?.firstOrNull() ?: 'U').uppercase(),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = showAccountMenu,
                                    onDismissRequest = { showAccountMenu = false }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = user.displayName ?: "Creator",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (!user.email.isNullOrBlank()) {
                                            Text(
                                                text = user.email!!,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Account & Studio Settings") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            showAccountSettingsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_account_settings_item")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.coins_and_vip)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null)
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            quotaDeniedMessage = null
                                            navigateTab(ROUTE_BILLING)
                                        },
                                        modifier = Modifier.testTag("menu_billing_item")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.help_and_support)) },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null)
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            quotaDeniedMessage = null
                                            billingInitialTab = 1
                                            showBillingDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_help_item")
                                    )
                                    if (billingViewModel.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.admin_control_center)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.AdminPanelSettings,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                showAdminDialog = true
                                            },
                                            modifier = Modifier.testTag("menu_admin_item")
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sign_out)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            signOut(credentialManager, scope)
                                        },
                                        modifier = Modifier.testTag("sign_out_menu_item")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.86f)
                        )
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail && !isDetailRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_STUDIO,
                            onClick = { navigateTab(ROUTE_STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_STUDIO) Icons.Rounded.MovieCreation else Icons.Outlined.MovieCreation,
                                    contentDescription = "Recap Studio"
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            modifier = Modifier.testTag("nav_studio_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_DUB,
                            onClick = { navigateTab(ROUTE_DUB) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_DUB) Icons.Rounded.Subtitles else Icons.Outlined.Subtitles,
                                    contentDescription = "Video Dub Editor"
                                )
                            },
                            label = { Text(stringResource(R.string.nav_dub)) },
                            modifier = Modifier.testTag("nav_dub_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_VAULT,
                            onClick = { navigateTab(ROUTE_VAULT) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_VAULT) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks,
                                    contentDescription = "Saved Scripts"
                                )
                            },
                            label = { Text(stringResource(R.string.nav_vault)) },
                            modifier = Modifier.testTag("nav_vault_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_BILLING,
                            onClick = { navigateTab(ROUTE_BILLING) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_BILLING) Icons.Rounded.WorkspacePremium else Icons.Outlined.WorkspacePremium,
                                    contentDescription = "Subscription & Billing"
                                )
                            },
                            label = { Text(stringResource(R.string.nav_billing)) },
                            modifier = Modifier.testTag("nav_billing_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (appConfig.announcementActive && appConfig.announcementMessage.isNotBlank() && !isDetailRoute) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appConfig.announcementTitle.ifBlank { "App Update Notice" },
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MyanmarScriptFamily),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appConfig.announcementMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MyanmarScriptFamily),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { billingViewModel.dismissAnnouncement(appConfig) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss announcement",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (useNavigationRail && !isDetailRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.weight(0.1f))
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_STUDIO,
                                onClick = { navigateTab(ROUTE_STUDIO) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_STUDIO) Icons.Rounded.MovieCreation else Icons.Outlined.MovieCreation,
                                        contentDescription = "Recap Studio"
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_studio)) },
                                modifier = Modifier.testTag("rail_studio_tab")
                            )
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_DUB,
                                onClick = { navigateTab(ROUTE_DUB) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_DUB) Icons.Rounded.Subtitles else Icons.Outlined.Subtitles,
                                        contentDescription = "Video Dub Editor"
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_dub)) },
                                modifier = Modifier.testTag("rail_dub_tab")
                            )
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_VAULT,
                                onClick = { navigateTab(ROUTE_VAULT) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_VAULT) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks,
                                        contentDescription = "Saved Scripts"
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_vault)) },
                                modifier = Modifier.testTag("rail_vault_tab")
                            )
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_BILLING,
                                onClick = { navigateTab(ROUTE_BILLING) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_BILLING) Icons.Rounded.WorkspacePremium else Icons.Outlined.WorkspacePremium,
                                        contentDescription = "Subscription & Billing"
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_billing)) },
                                modifier = Modifier.testTag("rail_billing_tab")
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                uiState = studioState,
                                onVideoPicked = { uri -> viewModel.onVideoSelected(context, uri) },
                                onClearVideo = viewModel::clearSelectedVideo,
                                onPlatformSelected = viewModel::selectPlatform,
                                onGenreSelected = viewModel::selectGenre,
                                onNotesChanged = viewModel::updateDirectorNotes,
                                onGenerateClick = {
                                    runWithQuotaGate { onSuccess ->
                                        viewModel.generateScript(context, onSuccess)
                                    }
                                },
                                onSaveClick = viewModel::saveGeneratedScriptToVault,
                                onNotifyCopied = viewModel::showMessage,
                                onOpenApiKeySettings = { showApiKeyDialog = true },
                                onGenerateOfflineFallback = {
                                    runWithQuotaGate { onSuccess ->
                                        viewModel.generateOfflineFallbackScript(onSuccess)
                                    }
                                },
                                onSendToVideoDub = { pickedUri ->
                                    if (pickedUri != null) {
                                        dubViewModel.onVideoSelected(context, pickedUri)
                                    }
                                    navigateTab(ROUTE_DUB)
                                },
                                onOpenBilling = { navigateTab(ROUTE_BILLING) },
                                showSwarmAnimation = studioPrefs.swarmAnimationEnabled,
                                accessBadgeText = accessBadgeText
                            )
                        }
                        composable(ROUTE_DUB) {
                            VideoDubScreen(
                                uiState = dubState,
                                onVideoPicked = { uri -> dubViewModel.onVideoSelected(context, uri) },
                                onClearVideo = dubViewModel::clearVideo,
                                onSelectVoiceCategory = dubViewModel::selectVoiceCategory,
                                onSelectBuiltInVoice = dubViewModel::selectBuiltInVoice,
                                onSelectClonedVoice = dubViewModel::selectClonedVoice,
                                onDeleteClonedVoice = dubViewModel::deleteClonedVoice,
                                onCreateVoiceCloneFromUri = { uri, name, onDone ->
                                    dubViewModel.createVoiceCloneFromUri(context, uri, name, onDone)
                                },
                                onCreateVoiceCloneFromRecordedFile = { file, name, onDone ->
                                    dubViewModel.createVoiceCloneFromRecordedFile(file, name, onDone)
                                },
                                onClearVoiceCloneError = dubViewModel::clearVoiceCloneError,
                                onAuditionVoice = { customText -> dubViewModel.previewVoice(context, customText) },
                                onAuditionSpecificVoice = { selection ->
                                    dubViewModel.previewVoice(context, customText = null, overrideVoice = selection)
                                },
                                onSpeedChanged = dubViewModel::setSpeakingSpeed,
                                onPitchChanged = dubViewModel::setPitchFineTune,
                                onVoiceBoostChanged = dubViewModel::setVoiceVolumeBoost,
                                onConciseSubtitlesChanged = dubViewModel::setConciseSubtitles,
                                onBurnSubtitlesChanged = dubViewModel::setBurnSubtitles,
                                onSubtitleStyleSelected = dubViewModel::selectSubtitleStyle,
                                onSubtitleSizeSelected = dubViewModel::selectSubtitleSize,
                                onSubtitlePositionSelected = dubViewModel::selectSubtitlePosition,
                                onWatermarkEnabledChanged = dubViewModel::setWatermarkEnabled,
                                onWatermarkTextChanged = dubViewModel::setWatermarkText,
                                onWatermarkMotionSelected = dubViewModel::setWatermarkMotionStyle,
                                onWatermarkPresetSelected = dubViewModel::setWatermarkPositionPreset,
                                onWatermarkCustomAnchorChanged = dubViewModel::setWatermarkCustomAnchor,
                                onWatermarkBadgeStyleSelected = dubViewModel::setWatermarkBadgeStyle,
                                onWatermarkScaleChanged = dubViewModel::setWatermarkScale,
                                onWatermarkOpacityChanged = dubViewModel::setWatermarkOpacity,
                                onKeepSoftBgmChanged = dubViewModel::setKeepSoftOriginalBgm,
                                onCopyrightEnabledChanged = dubViewModel::setCopyrightShieldEnabled,
                                onCopyrightPresetSelected = dubViewModel::selectCopyrightShieldPreset,
                                onCopyrightZoomChanged = dubViewModel::setCopyrightZoomScale,
                                onCopyrightMirrorChanged = dubViewModel::setCopyrightMirrorFlip,
                                onCopyrightColorChanged = dubViewModel::setCopyrightColorBoost,
                                onCopyrightBorderChanged = dubViewModel::setCopyrightCinemaBorder,
                                onContinuousFullVideoChanged = dubViewModel::setContinuousFullVideoDub,
                                onNarrationStyleSelected = dubViewModel::selectNarrationStyle,
                                onSegmentTextEdited = dubViewModel::updateSegmentBurmeseText,
                                onNudgeSegment = dubViewModel::nudgeSegmentTiming,
                                onAutoFillGaps = dubViewModel::autoFillTimelineGaps,
                                onDeleteSegment = dubViewModel::deleteSegment,
                                onAddSegment = dubViewModel::addSegmentAtEnd,
                                onStartAutoDub = {
                                    runWithQuotaGate { onSuccess ->
                                        dubViewModel.startFullAutoDub(context, onSuccess)
                                    }
                                },
                                onMuteOriginalOnly = {
                                    runWithQuotaGate { onSuccess ->
                                        dubViewModel.muteOriginalAudioAndExport(context, onSuccess)
                                    }
                                },
                                onRerenderClick = { dubViewModel.rerenderWithEditedSegments(context) },
                                onDownloadClick = { dubViewModel.downloadToGallery(context) },
                                onNotify = dubViewModel::showMessage,
                                onOpenApiKeySettings = { showApiKeyDialog = true },
                                onOpenBilling = { navigateTab(ROUTE_BILLING) },
                                showSwarmAnimation = studioPrefs.swarmAnimationEnabled,
                                accessBadgeText = accessBadgeText
                            )
                        }
                        composable(ROUTE_VAULT) {
                            VaultScreen(
                                vaultState = vaultState,
                                onScriptClick = { script ->
                                    navController.navigate("script_detail/${script.id}")
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDeleteScript = { script ->
                                    viewModel.deleteSavedScript(script.id)
                                },
                                onGoToStudio = { navigateTab(ROUTE_STUDIO) }
                            )
                        }
                        composable(ROUTE_BILLING) {
                            SubscriptionBillingScreen(
                                initialTab = 0,
                                quotaDeniedMessage = null,
                                userAccount = effectiveAccount,
                                appConfig = appConfig,
                                myPayments = myPayments,
                                myHelpMessages = myHelpMessages,
                                isSubmittingPayment = isSubmittingPayment,
                                isSubmittingHelp = isSubmittingHelp,
                                onSubmitPayment = { type, vipTier, coins, amountMmk, method, phone, txRef, note, uri, onDone ->
                                    billingViewModel.submitPaymentRequest(
                                        context = context,
                                        requestType = type,
                                        vipTier = vipTier,
                                        requestedCoins = coins,
                                        amountMmk = amountMmk,
                                        paymentMethod = method,
                                        payerPhone = phone,
                                        transactionRef = txRef,
                                        note = note,
                                        receiptUri = uri,
                                        onSuccess = onDone
                                    )
                                },
                                onSubmitHelp = { subject, message, onDone ->
                                    billingViewModel.submitHelpMessage(subject, message, onDone)
                                },
                                onNotify = { msg -> billingViewModel.showMessage(msg) }
                            )
                        }
                        composable(
                            route = ROUTE_SCRIPT_DETAIL,
                            arguments = listOf(navArgument("scriptId") { type = NavType.StringType })
                        ) { entry ->
                            val scriptId = entry.arguments?.getString("scriptId").orEmpty()
                            val script = (vaultState as? VaultUiState.Success)
                                ?.scripts
                                ?.firstOrNull { it.id == scriptId }

                            ScriptDetailScreen(
                                script = script,
                                onBack = { navController.popBackStack() },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onSaveEdits = { id, newTitle, newHook, newOutro, newNotes ->
                                    if (script != null) {
                                        viewModel.updateSavedScript(
                                            scriptId = id,
                                            sourceTitle = newTitle,
                                            openingHook = newHook,
                                            outro = newOutro,
                                            directorNotes = newNotes,
                                            viralTitles = script.viralTitles,
                                            scenes = script.scenes,
                                            onSuccess = {}
                                        )
                                    }
                                },
                                onNotify = viewModel::showMessage
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
