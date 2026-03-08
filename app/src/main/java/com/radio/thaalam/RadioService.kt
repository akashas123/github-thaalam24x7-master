package com.radio.thaalam


import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.AudioAttributes
import com.radio.thaalam.PlayerState.isPlaying
import com.radio.thaalam.PlayerState.refreshTrigger
import com.radio.thaalam.RadioPlayerHolder.player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi


class RadioService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var audioDeviceCallback: AudioDeviceCallback
    private var metadataJob: Job? = null
    object StreamConfig {
        const val STREAM_URL = "https://radio.thaalam24x7.in/listen/thaalam_24x7/live"
    }

    private val focusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->

            when (focusChange) {

                AudioManager.AUDIOFOCUS_LOSS -> {
                    player?.pause()
                    isPlaying.value = false
                    updatePlaybackState(false)
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    player?.pause()
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    player?.play()
                }
            }
        }




    private fun restartPlayer() {
        // Fully release old player
        player?.stop()
        player?.release()
        player = null

        // Create new instance
        player = ExoPlayer.Builder(this).build()

        val mediaItem = MediaItem.fromUri(StreamConfig.STREAM_URL)
        player!!.setMediaItem(mediaItem)
        player!!.prepare()
        player!!.playWhenReady = true
    }





    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        audioDeviceCallback = object : AudioDeviceCallback() {

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                for (device in removedDevices) {

                    if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {

                        player?.pause()
                        isPlaying.value = false
                        refreshTrigger.value = false
                        updatePlaybackState(false)
                    }
                }
            }
        }

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {

                AudioManager.AUDIOFOCUS_LOSS -> {
                    player?.pause()
                    isPlaying.value=false
                    refreshTrigger.value=false
                    updatePlaybackState(false)
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    player?.pause()
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    player?.play()
                    player?.seekToDefaultPosition()
                    isPlaying.value=true
                    refreshTrigger.value=true
                    updatePlaybackState(true)
                }
            }
        }

        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()

        audioManager.requestAudioFocus(audioFocusRequest)


        if (player != null) return

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build().apply {

                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(sessionId: Int) {
                        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {

                            audioSessionId=sessionId

                            Log.e("RadioService", "Audio session created: $sessionId")

                        }
                    }
                })

                setMediaItem(MediaItem.fromUri(StreamConfig.STREAM_URL))
                prepare()
            }


        mediaSession = MediaSessionCompat(this, "RadioSession")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                player?.play()
                player?.seekToDefaultPosition()
                isPlaying.value=true
                refreshTrigger.value=true
                updatePlaybackState(true)

            }

            override fun onPause() {
                player?.pause()
                isPlaying.value=false
                refreshTrigger.value=false
                updatePlaybackState(false)

            }
        })

        mediaSession.isActive = true


        createNotificationChannel()
        //startMetadataUpdates()

    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {



        when (intent?.action) {

            "OPEN_APP" -> {
                // No action needed, app will open via PendingIntent
            }

            "ACTION_PLAY" -> {
                player?.play()
                player?.seekToDefaultPosition()
                restartPlayer()
                isPlaying.value=true
                refreshTrigger.value=true
                updatePlaybackState(true)
            }

            "ACTION_PAUSE" -> {
                player?.pause()
                isPlaying.value=false
                refreshTrigger.value=false
                updatePlaybackState(false)
            }

        }
        CoroutineScope(Dispatchers.IO).launch {


            val title = intent?.getStringExtra("TITLE")
            val artist = intent?.getStringExtra("ARTIST")
            val artUrl = intent?.getStringExtra("ART")

            if (title != null || artist != null) {
                updateNotification(title, artist, artUrl)
            }
        }
        return START_STICKY


    }

    private fun updateNotification(title: String?, artist: String?, artUrl: String?) {

       val bitmap = try {
            artUrl?.let {
                BitmapFactory.decodeStream(java.net.URL(it).openStream())
            }
        }

       catch (e: Exception) {
            null
        }


      val openAppIntent = Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

      val pendingIntent = PendingIntent.getActivity(
          this,
          0,
          openAppIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )


        fun updateMediaMetadata(title: String?, artist: String?, bitmap: Bitmap?) {
            val metadata = android.support.v4.media.MediaMetadataCompat.Builder()

                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .build()

            mediaSession.setMetadata(metadata)
        }


        val notification = NotificationCompat.Builder(this, "radio_channel")
            .setSmallIcon(R.drawable.ic_thaalam)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )


            .build()

        startForeground(1, notification)
        updateMediaMetadata(title, artist, bitmap)

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio_channel",
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }


    override fun onDestroy() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        metadataJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent:Intent?): IBinder?{
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Stop audio
        player?.stop()
        player?.release()
        player = null
        val intent = Intent("ACTION_STOP_APP")
        sendBroadcast(intent)
        // Stop foreground & kill service
        stopForeground(true)
        super.onTaskRemoved(rootIntent)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

}
