package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ApiKeyConfigState
import com.example.data.StudioBackgroundStyle
import com.example.data.UserStudioPreferences
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import com.example.ui.theme.MyanmarScriptFamily
import com.google.firebase.auth.FirebaseUser

@Composable
fun UserAccountSettingsDialog(
    user: FirebaseUser,
    userAccount: UserAccount,
    appConfig: AppConfig,
    apiKeyState: ApiKeyConfigState,
    studioPrefs: UserStudioPreferences,
    onSelectBackgroundStyle: (StudioBackgroundStyle) -> Unit,
    onSwarmAnimationChanged: (Boolean) -> Unit,
    onDefaultMuteChanged: (Boolean) -> Unit,
    onDefaultCopyrightChanged: (Boolean) -> Unit,
    onOpenVipStore: () -> Unit,
    onOpenBuyCoins: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onOpenHelpSupport: () -> Unit,
    onSignOut: () -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val activeVip = userAccount.effectiveVipTier()
    val isVip = activeVip != VipTier.NONE
    val isAdmin = userAccount.isAdminAccount()
    val trialStatus = QuotaPolicy.evaluateTrialStatus(userAccount)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "User Account & Settings",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_account_settings_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Settings")
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
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Account Identity + Free / VIP Status Hero Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(
                            width = 1.4.dp,
                            color = if (isVip || isAdmin) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_settings_profile_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (user.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = "User avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon_recap),
                                        contentDescription = "User avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userAccount.displayName.ifBlank { user.displayName ?: "Creator" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = userAccount.email.ifBlank { user.email.orEmpty() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val tierTagLabel = when {
                                        isAdmin -> "ADMIN • UNLIMITED"
                                        isVip -> "ACCOUNT VIP • ${activeVip.displayLabel}"
                                        else -> "ACCOUNT FREE • 3-DAY TRIAL"
                                    }
                                    Surface(
                                        color = if (isVip || isAdmin) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = tierTagLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVip || isAdmin) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(user.uid))
                                        onNotify("User ID copied for Admin support")
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .height(42.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("UID", fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider()

                            // Account Free vs VIP Status Detail Box
                            if (isVip || isAdmin) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isAdmin) {
                                            "Admin Access Active (${BillingConstants.ADMIN_EMAIL})"
                                        } else {
                                            "${activeVip.burmeseLabel} • Exp: ${QuotaPolicy.formatDateYangon(userAccount.vipExpiresAtMs)}"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MyanmarScriptFamily),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    listOf(
                                        "Unlimited AI Movie Recap Script & Full-Video Burmese Dub",
                                        "Zero Daily Video Limit • Priority Cloud & Offline Render",
                                        "Full Video Copyright Shield + Custom Fire RecapScript Watermark"
                                    ).forEach { perk ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = perk,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Free Account • 3-Day Trial (1 Video / Day)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${userAccount.coins} Coins",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    // Day 1 / Day 2 / Day 3 Free Trial Pills
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (day in 1..BillingConstants.MAX_FREE_TRIAL_DAYS) {
                                            val used = day <= trialStatus.daysUsedCount
                                            val expired = trialStatus.trialPermanentlyExpired && !used
                                            Surface(
                                                color = when {
                                                    used -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                    expired -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (used) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = if (used) "Day $day ✓" else if (expired) "Day $day ✕" else "Day $day Free",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (trialStatus.trialPermanentlyExpired) {
                                            "Free ၃ ရက်ပြည့်သွားပါပြီ။ ဆက်သုံးရန် VIP (V1/V2/V3) သို့မဟုတ် Video Coins (${appConfig.coinCostPerVideo} Coins/video) ဝယ်ယူပေးပါ။"
                                        } else {
                                            "Free Account ဖြင့် တစ်ရက် ၁ ပုဒ် (၃ ရက်အထိ) အခမဲ့သုံးနိုင်ပါသည်။ Limit မရှိ သုံးလိုပါက VIP ဝယ်ယူနိုင်ပါသည်။"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MyanmarScriptFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenVipStore()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_upgrade_vip_button")
                                ) {
                                    Icon(Icons.Rounded.WorkspacePremium, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isVip) "Renew VIP" else "Buy VIP", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenBuyCoins()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_buy_coins_button")
                                ) {
                                    Icon(Icons.Rounded.MonetizationOn, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buy Coins", maxLines = 1)
                                }
                            }
                        }
                    }

                    // 2. Interactive Particle Background & Animation Style Settings
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Studio Background & Swarm Animation",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Text(
                                text = "Interactive Background Grid Mode (${studioPrefs.backgroundStyle.title})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(StudioBackgroundStyle.entries) { style ->
                                    FilterChip(
                                        selected = studioPrefs.backgroundStyle == style,
                                        onClick = { onSelectBackgroundStyle(style) },
                                        label = { Text(style.title) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("bg_style_${style.id}")
                                    )
                                }
                            }
                            Text(
                                text = studioPrefs.backgroundStyle.burmeseHint,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MyanmarScriptFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider()

                            SettingSwitchRow(
                                title = "AI Swarm Video Agent Animation",
                                subtitle = "Show 5 moving Swarm AI editing agents + 0–100% progress ring while creating video",
                                checked = studioPrefs.swarmAnimationEnabled,
                                testTag = "setting_swarm_anim_switch",
                                onCheckedChange = onSwarmAnimationChanged
                            )
                            SettingSwitchRow(
                                title = "Auto-Mute Original English Audio",
                                subtitle = "Always strip 100% of source English speech in Video Dub & Export",
                                checked = studioPrefs.defaultMuteOriginalEnglish,
                                testTag = "setting_default_mute_switch",
                                onCheckedChange = onDefaultMuteChanged
                            )
                            SettingSwitchRow(
                                title = "Auto-Enable Video Copyright Shield",
                                subtitle = "Protect Recap videos with subtle frame crop, color shift & border guard",
                                checked = studioPrefs.defaultCopyrightShield,
                                testTag = "setting_default_copyright_switch",
                                onCheckedChange = onDefaultCopyrightChanged
                            )
                        }
                    }

                    // 3. API Key, Support & Account Actions
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenApiKeySettings()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_api_key_button")
                            ) {
                                Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (apiKeyState.hasEffectiveKey) {
                                        "Gemini API Key Settings (${apiKeyState.maskedPreview})"
                                    } else {
                                        "Configure Gemini API Key"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenHelpSupport()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_help_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Help & Admin Support (${BillingConstants.ADMIN_EMAIL})")
                            }

                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onSignOut()
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
