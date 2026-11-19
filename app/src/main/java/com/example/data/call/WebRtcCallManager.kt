package com.example.data.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallManager(
    private val context: Context,
    private val onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.PeerConnectionState) -> Unit
) {
    private val appContext = context.applicationContext
    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isInitialized = false
    @Volatile
    private var hasRemoteDescription = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    fun initialize(enableVideo: Boolean) {
        if (isInitialized) return
        isInitialized = true

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        onLocalIceCandidate(
                            candidate.sdpMid ?: "0",
                            candidate.sdpMLineIndex,
                            candidate.sdp
                        )
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    if (newState != null) {
                        onConnectionStateChanged(newState)
                    }
                }

                override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            track.addSink(renderer)
                        }
                    }
                }

                override fun onAddStream(stream: MediaStream?) {
                    val vTrack = stream?.videoTracks?.firstOrNull()
                    if (vTrack != null) {
                        remoteVideoTrack = vTrack
                        remoteRenderer?.let { renderer ->
                            vTrack.addSink(renderer)
                        }
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            }
        )

        // Audio Track
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("ARDAMS"))
        }

        // Video Track
        if (enableVideo) {
            setupLocalVideo()
        }

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = enableVideo
        } catch (e: Exception) {
            Log.w(TAG, "AudioManager setup warning: ${e.message}")
        }
    }

    private fun setupLocalVideo() {
        try {
            val enumerator = Camera2Enumerator(appContext)
            val deviceNames = enumerator.deviceNames
            val frontCam = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull()
            if (frontCam == null) {
                Log.w(TAG, "No camera device found on this hardware")
                return
            }
            val capturer = enumerator.createCapturer(frontCam, null) ?: return
            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val vSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast) ?: return
            videoSource = vSource
            capturer.initialize(surfaceTextureHelper, appContext, vSource.capturerObserver)
            capturer.startCapture(720, 1280, 30)

            val vTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", vSource)
            localVideoTrack = vTrack
            if (vTrack != null) {
                peerConnection?.addTrack(vTrack, listOf("ARDAMS"))
                localRenderer?.let { renderer ->
                    vTrack.addSink(renderer)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Video capturer initialization warning: ${e.message}")
        }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (localRenderer == renderer) {
            localVideoTrack?.removeSink(renderer)
            localRenderer = null
        }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (remoteRenderer == renderer) {
            remoteVideoTrack?.removeSink(renderer)
            remoteRenderer = null
        }
    }

    fun createOffer(onOfferReady: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                    onOfferReady(desc.description)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createOffer failed: $err")
            }

            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    fun setRemoteOfferAndCreateAnswer(offerSdp: String, onAnswerReady: (String) -> Unit) {
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                drainPendingRemoteCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc != null) {
                            peerConnection?.setLocalDescription(SimpleSdpObserver(), answerDesc)
                            onAnswerReady(answerDesc.description)
                        }
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "createAnswer failed: $err")
                    }

                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "setRemoteDescription offer failed: $err")
            }
        }, remoteDesc)
    }

    fun setRemoteAnswer(answerSdp: String) {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                drainPendingRemoteCandidates()
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdpCandidate: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
        synchronized(pendingRemoteCandidates) {
            if (!hasRemoteDescription) {
                pendingRemoteCandidates.add(candidate)
                return
            }
        }
        peerConnection?.addIceCandidate(candidate)
    }

    private fun drainPendingRemoteCandidates() {
        val toAdd = synchronized(pendingRemoteCandidates) {
            hasRemoteDescription = true
            pendingRemoteCandidates.toList().also { pendingRemoteCandidates.clear() }
        }
        toAdd.forEach { candidate ->
            peerConnection?.addIceCandidate(candidate)
        }
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun setSpeakerphone(enabled: Boolean) {
        try {
            audioManager?.isSpeakerphoneOn = enabled
        } catch (_: Exception) {
        }
    }

    fun switchCamera() {
        try {
            videoCapturer?.switchCamera(null)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            localRenderer?.let { localVideoTrack?.removeSink(it) }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
            localRenderer = null
            remoteRenderer = null
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            localVideoTrack?.dispose()
            localAudioTrack?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            eglBase.release()
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Release warning: ${e.message}")
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    private companion object {
        const val TAG = "WebRtcCallManager"
    }
}
