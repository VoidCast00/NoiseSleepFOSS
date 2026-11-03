package io.github.voidcast00.noisesleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the noise playing with the screen off, runs the sleep timer, and shows the
 * play/pause notification. Pauses for phone calls and when headphones are unplugged.
 */
class NoiseService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var engine: NoiseEngine
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var noisyRegistered = false

    private val timerDone = Runnable { stopEverything() }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (Playback.state.value.playing) {
                resumeOnFocusGain = true
                pause(transient = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                play()
            }
        }
    }

    /** Headphones unplugged: stop before the noise comes out of the phone speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Playback.init(this)
        audio = getSystemService(AudioManager::class.java)
        engine = NoiseEngine(
            settings = { Playback.settings.value },
            timerGain = ::timerGain,
            onTimerSilent = { main.post { stopEverything() } },
        )
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TIMER -> restartTimer()
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine.stop()
        unregisterNoisy()
        abandonFocus()
        main.removeCallbacksAndMessages(null)
        Playback.setState(PlaybackState())
        super.onDestroy()
    }

    private fun play() {
        // Started with startForegroundService, so this must happen whatever comes next
        goForeground()
        if (!requestFocus()) {
            stopEverything()
            return
        }
        val end = Playback.state.value.timerEndsAt ?: newTimerEnd()
        Playback.setState(PlaybackState(playing = true, timerEndsAt = end))
        scheduleTimer(end)
        engine.start()
        registerNoisy()
        goForeground()
    }

    /** [transient]: a phone call or similar. Keep focus and the timer, resume afterwards. */
    private fun pause(transient: Boolean = false) {
        engine.stop()
        unregisterNoisy()
        if (transient) {
            Playback.setState(Playback.state.value.copy(playing = false))
        } else {
            main.removeCallbacks(timerDone)
            abandonFocus()
            Playback.setState(PlaybackState())
        }
        notifyUpdate()
    }

    private fun stopEverything() {
        resumeOnFocusGain = false
        pause()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restartTimer() {
        val st = Playback.state.value
        if (!st.playing) return
        val end = newTimerEnd()
        Playback.setState(st.copy(timerEndsAt = end))
        scheduleTimer(end)
        notifyUpdate()
    }

    private fun newTimerEnd(): Long? {
        val s = Playback.settings.value
        return if (!s.timerEnabled) null else SystemClock.elapsedRealtime() + s.timerMinutes * 60_000L
    }

    /** Backup for [NoiseEngine.onTimerSilent], which normally stops playback first. */
    private fun scheduleTimer(end: Long?) {
        main.removeCallbacks(timerDone)
        if (end != null) main.postDelayed(timerDone, end - SystemClock.elapsedRealtime() + 1_000)
    }

    /** Read live by the audio thread, so changing the fade length applies straight away. */
    private fun timerGain(): Float {
        val end = Playback.state.value.timerEndsAt ?: return 1f
        return fadeLevel(end - SystemClock.elapsedRealtime(), Playback.settings.value.fadeMinutes)
    }

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(NoiseEngine.AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
            .also { focusRequest = it }
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun notifyUpdate() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val st = Playback.state.value
        val text = when {
            st.timerEndsAt != null -> {
                val wallClock = System.currentTimeMillis() + (st.timerEndsAt - SystemClock.elapsedRealtime())
                getString(R.string.notif_stops_at, DateFormat.getTimeFormat(this).format(wallClock))
            }
            st.playing -> getString(R.string.notif_playing)
            else -> getString(R.string.notif_paused)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = if (st.playing) {
            NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.pause), serviceIntent(ACTION_PAUSE))
        } else {
            NotificationCompat.Action(R.drawable.ic_play, getString(R.string.play), serviceIntent(ACTION_PLAY))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggle)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), serviceIntent(ACTION_STOP))
            .build()
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, NoiseService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val code = action.hashCode()
        return if (action == ACTION_PLAY) {
            PendingIntent.getForegroundService(this, code, intent, flags)
        } else {
            PendingIntent.getService(this, code, intent, flags)
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_PLAY = "io.github.voidcast00.noisesleep.PLAY"
        private const val ACTION_PAUSE = "io.github.voidcast00.noisesleep.PAUSE"
        private const val ACTION_STOP = "io.github.voidcast00.noisesleep.STOP"
        private const val ACTION_TIMER = "io.github.voidcast00.noisesleep.TIMER"

        private fun intent(context: Context, action: String) =
            Intent(context, NoiseService::class.java).setAction(action)

        /**
         * Loudness (0..1) with [leftMs] to go and a [fadeMinutes] fade. Sounds like a straight
         * slope: amplitude is squared because the ear hears loudness roughly as amplitude^0.6.
         * "No fade" still takes 3 seconds so it does not cut off with a jolt.
         */
        fun fadeLevel(leftMs: Long, fadeMinutes: Int): Float {
            val fadeMs = maxOf(fadeMinutes * 60_000L, 3_000L)
            val f = (leftMs.toFloat() / fadeMs).coerceIn(0f, 1f)
            return f * f
        }

        fun play(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_PLAY))
        }

        fun pause(context: Context) {
            if (Playback.state.value.playing) context.startService(intent(context, ACTION_PAUSE))
        }

        /** The timer length changed: restart the countdown from now if playing. */
        fun timerChanged(context: Context) {
            if (Playback.state.value.playing) context.startService(intent(context, ACTION_TIMER))
        }
    }
}
