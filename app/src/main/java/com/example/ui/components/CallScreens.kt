package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.call.WebRtcCallManager
import com.example.data.model.CallSession
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PineNightBackground
import com.example.ui.theme.PineNightSurface
import com.example.ui.theme.VerifiedGreen
import kotlinx.coroutines.delay
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun rememberCallPermissionStarter(
    onPermissionsGranted: (callType: String) -> Unit,
    onPermissionDenied: () -> Unit
): (callType: String) -> Unit {
    val context = LocalContext.current
    var pendingCallType by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val callType = pendingCallType ?: "audio"
        pendingCallType = null
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = callType != "video" ||
            results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micOk && camOk) {
            onPermissionsGranted(callType)
        } else {
            onPermissionDenied()
        }
    }

    return remember(context, launcher) {
        { callType ->
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }
            if (callType == "video" &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.CAMERA)
            }
            if (needed.isEmpty()) {
                onPermissionsGranted(callType)
            } else {
                pendingCallType = callType
                launcher.launch(needed.toTypedArray())
            }
        }
    }
}

@Composable
fun IncomingCallOverlay(
    call: CallSession,
    onAccept: (CallSession) -> Unit,
    onDecline: (CallSession) -> Unit,
    onPermissionDenied: () -> Unit
) {
    val isVideo = call.callType == "video"
    val startWithPermissions = rememberCallPermissionStarter(
        onPermissionsGranted = { onAccept(call) },
        onPermissionDenied = {
            onPermissionDenied()
            onDecline(call)
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(74.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = if (isVideo) "Incoming Family Video Call" else "Incoming Family Audio Call",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = call.callerName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "End-to-end encrypted peer call via Moinul Family",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = { onDecline(call) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CallEndRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("decline_incoming_call_button")
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Decline")
                    }

                    Button(
                        onClick = { startWithPermissions(call.callType) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerifiedGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("accept_incoming_call_button")
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Accept")
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlayScreen(
    call: CallSession,
    currentUserId: String,
    callManager: WebRtcCallManager?,
    webrtcState: PeerConnection.PeerConnectionState,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }

    val isVideoCall = call.callType == "video"
    val peerName = if (call.callerId == currentUserId) call.calleeName else call.callerName

    var elapsedSeconds by remember(call.id) { mutableLongStateOf(0L) }
    val isConnected = webrtcState == PeerConnection.PeerConnectionState.CONNECTED ||
        call.status == "accepted"

    LaunchedEffect(isConnected, call.id) {
        if (isConnected) {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1L
            }
        }
    }

    val statusLabel = when {
        webrtcState == PeerConnection.PeerConnectionState.CONNECTED -> formatCallDuration(elapsedSeconds)
        call.status == "accepted" -> "Connecting • ${formatCallDuration(elapsedSeconds)}"
        call.callerId == currentUserId -> "Ringing…"
        else -> "Connecting…"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PineNightBackground, PineNightSurface, EmeraldPrimary.copy(alpha = 0.45f))
                )
            )
    ) {
        // Remote Video Canvas (full screen when video call)
        if (isVideoCall && callManager != null) {
            var remoteViewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(callManager.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        remoteViewRef = this
                        callManager.attachRemoteRenderer(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(callManager) {
                onDispose {
                    remoteViewRef?.let { renderer ->
                        callManager.detachRemoteRenderer(renderer)
                        try {
                            renderer.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        // Top Call Status & Peer Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = if (isVideoCall) Alignment.Start else Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isVideoCall) "Video Call • $statusLabel" else "Audio Call • $statusLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            if (isVideoCall) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        // Local Video PiP (top-right)
        if (isVideoCall && isVideoEnabled && callManager != null) {
            var localViewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 18.dp, end = 16.dp)
                    .size(width = 112.dp, height = 156.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(callManager.eglBase.eglBaseContext, null)
                            setMirror(true)
                            setZOrderMediaOverlay(true)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            localViewRef = this
                            callManager.attachLocalRenderer(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DisposableEffect(callManager, isVideoEnabled) {
                onDispose {
                    localViewRef?.let { renderer ->
                        callManager.detachLocalRenderer(renderer)
                        try {
                            renderer.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        // Center Audio Call Avatar Card
        if (!isVideoCall) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peerName.take(1).uppercase().ifBlank { "F" },
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // Bottom Controls Bar
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlIconButton(
                    icon = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (isMicMuted) "Unmute microphone" else "Mute microphone",
                    active = isMicMuted,
                    onClick = onToggleMute,
                    tag = "call_toggle_mute"
                )
                CallControlIconButton(
                    icon = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                    contentDescription = if (isSpeakerOn) "Turn off speaker" else "Turn on speaker",
                    active = isSpeakerOn,
                    onClick = onToggleSpeaker,
                    tag = "call_toggle_speaker"
                )
                if (isVideoCall) {
                    CallControlIconButton(
                        icon = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        contentDescription = if (isVideoEnabled) "Turn camera off" else "Turn camera on",
                        active = !isVideoEnabled,
                        onClick = onToggleVideo,
                        tag = "call_toggle_video"
                    )
                    CallControlIconButton(
                        icon = Icons.Rounded.Cameraswitch,
                        contentDescription = "Switch camera",
                        active = false,
                        onClick = onSwitchCamera,
                        tag = "call_switch_camera"
                    )
                }
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CallEndRed)
                        .testTag("call_hangup_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.2f))
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) PineNightBackground else Color.White
        )
    }
}

private fun formatCallDuration(totalSeconds: Long): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
