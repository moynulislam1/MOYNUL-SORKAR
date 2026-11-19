package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.ui.components.OnlineBadgeAvatar
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyProfileScreen(
    profile: UserProfile?,
    isBusy: Boolean,
    onOpenMyProfileEditor: () -> Unit,
    onSaveCredentials: (displayName: String, familyRole: String, phoneNumber: String, newPassword: String?) -> Unit,
    onUpdatePrivacy: (lockEnabled: Boolean, hidePreviews: Boolean, readReceipts: Boolean, showOnline: Boolean) -> Unit,
    onLockNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("") }
    var familyRole by remember { mutableStateOf("Member") }
    var phoneNumber by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (profile != null) {
            displayName = profile.displayName
            familyRole = profile.familyRole
            phoneNumber = profile.phoneNumber
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Privacy & Account", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Gmail verification, number & password lock",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. My Profile, Bio, Air & Personal QR Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMyProfileEditor)
                    .testTag("privacy_open_profile_editor_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OnlineBadgeAvatar(
                            displayName = profile?.displayName ?: "Me",
                            avatarColorHex = profile?.avatarColorHex ?: "#0F5C4D",
                            avatarBadge = profile?.avatarBadge ?: "🏡",
                            isOnline = profile?.showOnlineStatus ?: true,
                            size = 54.dp,
                            avatarPhotoBase64 = profile?.avatarPhotoBase64.orEmpty()
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = profile?.displayName?.ifBlank { "My Profile" } ?: "My Profile",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = profile?.moodTag?.ifBlank { "✨ Available" } ?: "✨ Available",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!profile?.profileCode.isNullOrBlank()) {
                                Text(
                                    text = "Profile Code: ${profile!!.profileCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = "My QR Code",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = profile?.bio?.ifBlank { "Tap to write about yourself, give air/mood, and share your Personal QR." }
                            ?: "Tap to write about yourself, give air/mood, and share your Personal QR.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = onOpenMyProfileEditor,
                        modifier = Modifier.fillMaxWidth().testTag("privacy_edit_bio_air_qr_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit About Me, Air & Show QR")
                    }
                }
            }

            // 1. Gmail Verification Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = profile?.email?.ifBlank { "Verified Gmail Account" } ?: "Loading Gmail…",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verified",
                                tint = VerifiedGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (profile?.emailVerified != false) "Verified via Gmail" else "Gmail verification active",
                                style = MaterialTheme.typography.labelMedium,
                                color = VerifiedGreen
                            )
                        }
                    }
                }
            }

            // 2. Number & Password Credentials Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Family Number & Password",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Link your phone number and privacy password to unlock Moinul Family or sign back in with Number & Password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = familyRole,
                        onValueChange = { familyRole = it },
                        label = { Text("Family Role (e.g. Father, Mother, Brother)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_role_input")
                    )

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Family Phone Number") },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = {
                            Text(
                                if (profile?.hasPassword == true) "New Privacy Password (leave blank to keep)"
                                else "Set Privacy Password (min 4 chars)"
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_password_input")
                    )

                    Button(
                        onClick = {
                            onSaveCredentials(
                                displayName,
                                familyRole,
                                phoneNumber,
                                newPassword.takeIf { it.length >= 4 }
                            )
                            if (newPassword.length >= 4) {
                                newPassword = ""
                            }
                        },
                        enabled = !isBusy && displayName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("save_credentials_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Number & Password")
                    }
                }
            }

            // 3. Privacy Vault Toggles
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy Vault Controls", style = MaterialTheme.typography.titleLarge)
                    }

                    val lockEnabled = profile?.privacyLockEnabled ?: false
                    val hidePreviews = profile?.hideMessagePreviews ?: false
                    val readReceipts = profile?.readReceiptsEnabled ?: true
                    val showOnline = profile?.showOnlineStatus ?: true

                    PrivacyToggleRow(
                        title = "App Lock on Launch",
                        subtitle = "Require Number & Password when opening Moinul Family",
                        checked = lockEnabled,
                        onCheckedChange = {
                            onUpdatePrivacy(it, hidePreviews, readReceipts, showOnline)
                        },
                        tag = "toggle_app_lock"
                    )
                    PrivacyToggleRow(
                        title = "Hide Message Previews",
                        subtitle = "Mask message snippets in the chat list",
                        checked = hidePreviews,
                        onCheckedChange = {
                            onUpdatePrivacy(lockEnabled, it, readReceipts, showOnline)
                        },
                        tag = "toggle_hide_previews"
                    )
                    PrivacyToggleRow(
                        title = "Read Receipts",
                        subtitle = "Let family members see when you read messages",
                        checked = readReceipts,
                        onCheckedChange = {
                            onUpdatePrivacy(lockEnabled, hidePreviews, it, showOnline)
                        },
                        tag = "toggle_read_receipts"
                    )
                    PrivacyToggleRow(
                        title = "Share Active Status",
                        subtitle = "Show your check-in presence to your family circle",
                        checked = showOnline,
                        onCheckedChange = {
                            onUpdatePrivacy(lockEnabled, hidePreviews, readReceipts, it)
                        },
                        tag = "toggle_online_status"
                    )

                    if (profile?.hasPassword == true) {
                        OutlinedButton(
                            onClick = onLockNow,
                            modifier = Modifier.fillMaxWidth().testTag("privacy_lock_now_button")
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lock Messenger Now")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
