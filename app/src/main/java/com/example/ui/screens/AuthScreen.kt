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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.local.DeviceSessionStore
import com.example.data.local.PairedDeviceInfo
import com.example.data.model.LoginTab
import com.example.data.security.CryptoUtils
import com.example.data.security.ParsedQrAction
import com.example.ui.components.FamilyWelcomeArtwork
import com.example.ui.components.QrScannerSheet
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    sessionStore: DeviceSessionStore,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedTab by remember { mutableStateOf(LoginTab.GMAIL) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var pairedInfo by remember { mutableStateOf(PairedDeviceInfo()) }

    var phoneInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var numberOnlyMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showScannerSheet by remember { mutableStateOf(false) }
    var scannedCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        pairedInfo = sessionStore.getSnapshot()
        if (pairedInfo.pairedPhone.isNotBlank()) {
            phoneInput = pairedInfo.pairedPhone
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Interactive login stays visible */ },
            scope = scope
        )
    }

    fun launchGoogleSignIn() {
        errorMessage = null
        isLoading = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isLoading = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FamilyWelcomeArtwork()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Private messenger for your family circle. Sign in through Gmail verification, Family Number & Password, or QR Scanner.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == LoginTab.GMAIL,
                        onClick = { selectedTab = LoginTab.GMAIL; errorMessage = null },
                        text = { Text(stringResource(R.string.auth_tab_gmail)) },
                        modifier = Modifier.testTag("tab_login_gmail")
                    )
                    Tab(
                        selected = selectedTab == LoginTab.NUMBER_PASSWORD,
                        onClick = { selectedTab = LoginTab.NUMBER_PASSWORD; errorMessage = null },
                        text = { Text(stringResource(R.string.auth_tab_number)) },
                        modifier = Modifier.testTag("tab_login_number")
                    )
                    Tab(
                        selected = selectedTab == LoginTab.QR_SCANNER,
                        onClick = { selectedTab = LoginTab.QR_SCANNER; errorMessage = null },
                        text = { Text(stringResource(R.string.auth_tab_scanner)) },
                        modifier = Modifier.testTag("tab_login_scanner")
                    )
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (selectedTab) {
                            LoginTab.GMAIL -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Gmail Account & Verification",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Text(
                                    text = "Verify with your Google / Gmail account to sync private family chats and link your Family Number securely.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { launchGoogleSignIn() },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("google_sign_in_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.sign_in_with_google))
                                    }
                                }
                            }

                            LoginTab.NUMBER_PASSWORD -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = !numberOnlyMode,
                                        onClick = { numberOnlyMode = false; errorMessage = null },
                                        label = { Text("Number + Password") },
                                        modifier = Modifier.testTag("chip_number_password")
                                    )
                                    FilterChip(
                                        selected = numberOnlyMode,
                                        onClick = { numberOnlyMode = true; errorMessage = null },
                                        label = { Text("Number Only") },
                                        modifier = Modifier.testTag("chip_number_only")
                                    )
                                }

                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it; errorMessage = null },
                                    label = { Text("Family Phone Number") },
                                    placeholder = { Text("+880 17XX XXXXXX") },
                                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_phone_input")
                                )

                                if (!numberOnlyMode) {
                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it; errorMessage = null },
                                        label = { Text("Family Password") },
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_password_input")
                                    )
                                }

                                if (pairedInfo.pairedEmail.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Paired Gmail: ${pairedInfo.pairedEmail}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val normalized = CryptoUtils.normalizePhone(phoneInput)
                                        if (normalized.length < 6) {
                                            errorMessage = "Enter a valid phone number (at least 6 digits)."
                                            return@Button
                                        }
                                        if (!numberOnlyMode && passwordInput.length < 4) {
                                            errorMessage = "Enter your family password (at least 4 characters)."
                                            return@Button
                                        }
                                        if (pairedInfo.pairedPhone.isNotBlank() &&
                                            pairedInfo.pairedPhone != normalized
                                        ) {
                                            errorMessage = "Phone number does not match this device's paired Family Number. Verify with Gmail to switch accounts."
                                            return@Button
                                        }
                                        if (!numberOnlyMode && pairedInfo.passwordHash.isNotBlank()) {
                                            val valid = CryptoUtils.verifyPassword(
                                                passwordInput,
                                                pairedInfo.passwordSalt,
                                                pairedInfo.passwordHash
                                            )
                                            if (!valid) {
                                                errorMessage = "Incorrect password for $normalized."
                                                return@Button
                                            }
                                        }
                                        infoMessage = "Credentials checked. Confirming verified Gmail session…"
                                        launchGoogleSignIn()
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("number_login_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            if (numberOnlyMode) "Verify Number via Gmail"
                                            else "Sign In with Number & Password"
                                        )
                                    }
                                }
                            }

                            LoginTab.QR_SCANNER -> {
                                Text(
                                    text = "Scan a Family Invite QR code from another family member's device to join and sign in automatically after Gmail verification.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!scannedCode.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Scanned Family Code: $scannedCode",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Complete Gmail verification below to enter this family circle.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showScannerSheet = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("open_auth_scanner_button")
                                ) {
                                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open QR Camera Scanner")
                                }

                                Button(
                                    onClick = { launchGoogleSignIn() },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("scanner_continue_gmail_button")
                                ) {
                                    Text(
                                        if (scannedCode != null) "Verify Gmail & Join Family"
                                        else "Verify Gmail to Link Device"
                                    )
                                }
                            }
                        }

                        if (infoMessage != null) {
                            Text(
                                text = infoMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScannerSheet) {
        QrScannerSheet(
            title = "Scan Family QR Code",
            subtitle = "Point your camera at a Moinul Family QR code to connect with your family.",
            onQrScanned = { raw ->
                showScannerSheet = false
                val parsed = CryptoUtils.parseQrPayload(raw)
                when (parsed) {
                    is ParsedQrAction.JoinInvite -> {
                        scannedCode = parsed.inviteCode
                        scope.launch {
                            sessionStore.setPendingInviteCode(parsed.inviteCode)
                        }
                        infoMessage = if (parsed.familyName.isNotBlank()) {
                            "Detected invite for ${parsed.familyName}. Verifying with Gmail…"
                        } else {
                            "Detected code ${parsed.inviteCode}. Verifying with Gmail…"
                        }
                        launchGoogleSignIn()
                    }
                    is ParsedQrAction.ViewProfile -> {
                        errorMessage = "Please sign in with Gmail first, then scan this Profile QR or ID inside Chats to add them."
                    }
                    null -> {
                        errorMessage = "Unrecognized QR code. Please scan a valid Moinul Family QR code."
                    }
                }
            },
            onDismiss = { showScannerSheet = false }
        )
    }
}
