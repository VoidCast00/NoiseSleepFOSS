package io.github.voidcast00.noisesleep

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat

/** Quick Settings tile: start or stop the noise from the notification shade, no app needed. */
class NoiseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        Playback.init(this)
        if (Playback.state.value.playing) {
            NoiseService.pause(this)
        } else {
            try {
                NoiseService.play(this)
            } catch (e: Exception) {
                // Some Android versions refuse to start playback from the background.
                // Open the app instead, which starts playing as soon as it is shown.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e !is ForegroundServiceStartNotAllowedException) throw e
                openAppAndPlay()
            }
        }
    }

    private fun openAppAndPlay() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_PLAY, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val st = Playback.state.value
        tile.state = if (st.playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !st.playing -> getString(R.string.tile_off)
            st.timerEndsAt != null -> {
                val wall = System.currentTimeMillis() + (st.timerEndsAt - SystemClock.elapsedRealtime())
                getString(R.string.tile_until, DateFormat.getTimeFormat(this).format(wall))
            }
            else -> getString(R.string.notif_playing)
        }
        tile.updateTile()
    }

    companion object {
        /** Ask the system to redraw the tile, e.g. after play/pause. */
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, NoiseTileService::class.java))
        }
    }
}
