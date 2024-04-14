package si.uni_lj.fri.pbd.miniapp2

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log

class MediaPlayerService : Service() {
    companion object {
        private val TAG: String? = MediaPlayerService::class.simpleName
        const val ACTION_STOP = "stop_media_player_service"
        const val ACTION_START = "start_media_player_service"
        private const val channelID = "media_player"
        // TODO: define a static final int NOTIFICATION_ID
        const val NOTIFICATION_ID = 1001
    }

    private lateinit var mediaPlayer : MediaPlayer
    private var isMediaPlayerInitialized = false

    inner class MediaPlayerBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private var playerServiceBinder: Binder = MediaPlayerBinder()

    override fun onCreate() {

        Log.d(TAG, "creating media player service")
        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.sample_music)

        isMediaPlayerInitialized = true
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "binding media player service")
        return playerServiceBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }

    fun background() {
        stopForeground(true)
    }

    fun playAudio() {
        if (!isMediaPlayerInitialized) {
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.sample_music)
            isMediaPlayerInitialized = true
        }
        mediaPlayer.start()
    }

    fun pauseAudio() {
        mediaPlayer.pause()
    }



}