package si.uni_lj.fri.pbd.miniapp2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.random.Random


class MediaPlayerService : Service() {
    companion object {
        private val TAG: String? = MediaPlayerService::class.simpleName
        const val ACTION_STOP = "stop_media_player_service"
        const val ACTION_START = "start_media_player_service"
        private const val channel_ID = "media_player"
        // static final int NOTIFICATION_ID
        const val NOTIFICATION_ID = 2001

        // flags for updating music play time
        const val ACTION_UPDATE_ELAPSED_TIME = "UPDATE_ELAPSED_TIME"
        const val EXTRA_ELAPSED_TIME_MS = "ELAPSED_TIME_MS"
    }

    // media player variables
    private lateinit var mediaPlayer : MediaPlayer
    private var isMediaPlayerInitialized = false
    var isServiceActive = false

    // coroutine for updating music play time
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // service binder
    inner class MediaPlayerBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }
    private var playerServiceBinder: Binder = MediaPlayerBinder()

    // variable to hold currently playing track
    var currentTrack: Track? = null

    // create empty list for all tracks
    var tracks: MutableList<Track> = mutableListOf()
    // create objects for local tracks
    var track1 = Track("coldplay.mp3", 274)
    var track2 = Track("imagine_dragons.mp3", 250)
    var track3 = Track("sample_music.mp3", 61)

    override fun onCreate() {
        // add local tracks to the list
        tracks.add(track1)
        tracks.add(track2)
        tracks.add(track3)

        // add the downloaded song if it exists
        if (MainActivity.isFilePresent(applicationContext)) {
            val track4 = Track("hit.mp3", 187)
            tracks.add(track4)
        }

        Log.d(TAG, "creating media player service")

        // set up the media player
        setupMediaPlayer()
        // display media player notification
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //val notification = createNotification()
        //startForeground(NOTIFICATION_ID, notification)
        //createNotificationTwo()
        return START_STICKY
    }
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "binding media player service")
        return this.playerServiceBinder
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
            Log.d("MediaPlayerService", "media player is not initialized")
        }
        mediaPlayer.start()
        startElapsedTimeUpdates()
        isServiceActive = true
    }

    fun pauseAudio() {
        mediaPlayer.pause()
        stopElapsedTimeUpdates()
    }

    fun handleStopCommand() {
        mediaPlayer.stop()
        setupMediaPlayer()
        stopElapsedTimeUpdates()
        isServiceActive = false
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

    private fun getRandomSong(): Track {
        val numOfSongs = tracks.size
        return tracks.get(Random.nextInt(0, numOfSongs))
    }

    private fun setupMediaPlayer() {
        mediaPlayer = MediaPlayer()
        currentTrack = getRandomSong()
        val filePath = "/storage/emulated/0/Download/hit.mp3"
        /*
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filePath = File(downloadsDir, "hit.mp3")
         */
        // if picked track is the one that was downloaded, it is located in downloads folder
        if (currentTrack!!.name == "hit.mp3") {
            try {
                mediaPlayer.setDataSource(filePath)
                mediaPlayer.prepareAsync()
                isMediaPlayerInitialized = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        // if picked track is in the assets folder
        else {
            try {
                val assetManager: AssetManager = assets
                val descriptor: AssetFileDescriptor = assetManager.openFd(currentTrack!!.name)
                mediaPlayer.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                mediaPlayer.prepareAsync()
                isMediaPlayerInitialized = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channel_ID)
            .setContentTitle("MediaPlayerService")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setChannelId(channel_ID)
            .setContentIntent(pendingIntent)
            .build()



    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channel_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun foreground() {
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("MediaPlayerService", "notification should be displaying")
    }

    private fun createNotificationTwo(): Notification {
        // create a notification builder
        val builder = NotificationCompat.Builder(applicationContext, channel_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("music player")
            .setContentText("playing music")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        //.setContentIntent(pendinIntent)

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        return builder.build()
        //notificationManager.notify(NOTIFICATION_ID, builder.build())
    }


}