package si.uni_lj.fri.pbd.miniapp2

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaPlayerService : Service() {
    companion object {
        private val TAG: String? = MediaPlayerService::class.simpleName
        const val ACTION_STOP = "stop_media_player_service"
        const val ACTION_START = "start_media_player_service"
        private const val channelID = "media_player"
        // static final int NOTIFICATION_ID
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_ELAPSED_TIME = "si.uni_lj.fri.pbd.miniapp2.action.UPDATE_ELAPSED_TIME"
        const val EXTRA_ELAPSED_TIME_MS = "si.uni_lj.fri.pbd.miniapp2.extra.ELAPSED_TIME_MS"
    }

    private lateinit var mediaPlayer : MediaPlayer
    private var isMediaPlayerInitialized = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    inner class MediaPlayerBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private var playerServiceBinder: Binder = MediaPlayerBinder()

    var currentTrack: Track? = null


    val track1 = Track("sample_music", 61, R.raw.sample_music)

    override fun onCreate() {

        Log.d(TAG, "creating media player service")
        // set the current track
        currentTrack = track1
        Log.d("MediaPlayerService", track1.name)
        mediaPlayer = MediaPlayer.create(applicationContext, currentTrack!!.resid)

        isMediaPlayerInitialized = true
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "binding media player service")
        return playerServiceBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
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
        startElapsedTimeUpdates()
    }

    fun pauseAudio() {
        mediaPlayer.pause()
        stopElapsedTimeUpdates()
    }

    fun handleStopCommand() {
        mediaPlayer.stop()
        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.sample_music)
        stopElapsedTimeUpdates()
    }

    fun handleExitCommand() {
        // stop music playback if currently playing
        pauseAudio()
        // Stop the service
        stopSelf()
    }

    fun retrieveCurrentTrack(): Track? {
        return currentTrack
    }

    private fun startElapsedTimeUpdates() {
        serviceScope.launch {
            while (isActive && mediaPlayer.isPlaying) {
                delay(1000) // update every second
                Log.d("MediaPlayerService", "updating time")
                // current position on the track in milliseconds
                val currentPosition = mediaPlayer.currentPosition
                Log.d("MediaPlayerService", "current position: "+currentPosition)
                // send elapsed time to UI using a broadcast
                val intent = Intent(ACTION_UPDATE_ELAPSED_TIME).apply {
                    putExtra(EXTRA_ELAPSED_TIME_MS, currentPosition)
                }
                sendBroadcast(intent)
            }
        }
    }


    private fun stopElapsedTimeUpdates() {
        // cancel the coroutine job when playback is paused or stopped
        serviceScope.coroutineContext.cancelChildren()
    }



}