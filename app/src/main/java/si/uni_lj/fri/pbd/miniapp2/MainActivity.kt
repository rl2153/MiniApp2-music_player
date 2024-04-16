package si.uni_lj.fri.pbd.miniapp2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import si.uni_lj.fri.pbd.miniapp2.MediaPlayerService.Companion.ACTION_START
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding
import android.Manifest
import android.media.AudioAttributes
import android.os.Environment
import android.view.View
import java.io.File

class MainActivity : AppCompatActivity() {

    private val INTERNET_PERMISSION_REQUEST_CODE = 101
    private val POST_NOTIFICATION_PERMISSION_REQUEST_CODE = 102
    private val FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 103

    private lateinit var binding: ActivityMainBinding
    private lateinit var context: Context

    // broadcast receiver to receive updates from MediaPlayerService
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == MediaPlayerService.ACTION_UPDATE_ELAPSED_TIME) {
                    val playedTimeMs = it.getIntExtra(MediaPlayerService.EXTRA_ELAPSED_TIME_MS, 0)
                    updateElapsedTime(playedTimeMs)
                    updateProgressBar(playedTimeMs)
                }
            }
        }
    }

    // vars for the media player service
    private var mediaPlayerService: MediaPlayerService? = null
    var mediaPlayerServiceBound: Boolean = false


    // progress bar
    private var progressBar: ProgressBar? = null
    // track duration text view
    private var trackDuration: TextView? = null
    // current track info
    private var currentTrack: Track? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        context = applicationContext
        setContentView(binding.root)

        progressBar = binding.progressBar
        trackDuration = binding.viewTrackDuration

        createNotificationChannel()
    }

    // connection to the media player service
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            //Log.d(TAG, "Service bound")
            val binder = iBinder as MediaPlayerService.MediaPlayerBinder
            mediaPlayerService = binder.service
            mediaPlayerServiceBound = true
            mediaPlayerService?.background()

        }
        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "Service disconnect")
            mediaPlayerServiceBound = false
        }
    }

    //@RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()

        // check for permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_REQUEST_CODE
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.FOREGROUND_SERVICE),
                FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                POST_NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }

        // start media player service
        Log.d(TAG, "Starting and binding service");
        val intent = Intent(this, MediaPlayerService::class.java)
        startService(intent)
        intent.action = ACTION_START
        bindService(intent, mConnection, 0);

        // register intent receiver
        registerReceiver(
            broadcastReceiver,
            IntentFilter(MediaPlayerService.ACTION_UPDATE_ELAPSED_TIME),
            RECEIVER_NOT_EXPORTED
        )

        // button listeners
        binding.btnPlay.setOnClickListener {
            mediaPlayerService?.playAudio()
            currentTrack = mediaPlayerService?.retrieveCurrentTrack()
            Log.d("MainActivity", "current track: "+currentTrack)
            // set current track title
            binding.viewTrackTitle.text = currentTrack!!.name
        }

        binding.btnPause.setOnClickListener {
            mediaPlayerService?.pauseAudio()
        }

        binding.btnStop.setOnClickListener {
            mediaPlayerService?.handleStopCommand()
            trackDuration?.text = 0.toString()
            progressBar?.progress = 0
        }

        binding.btnExit.setOnClickListener {
            mediaPlayerService?.handleExitCommand()
            finish()
        }

        // check if the hit file is already downloaded
        // if it is, hide the hits button
        if (isFilePresent(context)) {
            hideButton()
        }
        else {
            binding.btnHits.setOnClickListener {
                // create a work request
                val downloadWorkRequest = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                    .build()
                // schedule the work request
                WorkManager.getInstance(this).enqueue(downloadWorkRequest)
            }
        }
    }


    override fun onStop() {
        super.onStop()
        // if the Service is bound, unbind it
        if (mediaPlayerServiceBound) {
            unbindService(mConnection)
            mediaPlayerServiceBound = false
        }
    }

    // Helper function to format duration (e.g., convert milliseconds to a readable format)
    private fun formatDuration(durationSec: Int): String {
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateElapsedTime(playedTimeMs: Int) {
        val playedTimeSec = (playedTimeMs + 500) / 1000
        val durationFormatted = formatDuration(playedTimeSec)
        binding.viewTrackDuration.text = durationFormatted
        trackDuration?.text = playedTimeSec.toString()
    }

    private fun updateProgressBar(playedTimeMs: Int) {
        val totalDuration = currentTrack?.durationSec
        val playedTimeSec = (playedTimeMs + 500) / 1000
        var progressPercentage = 0
        if (totalDuration != null) {
            progressPercentage = (playedTimeSec.toFloat() / totalDuration.toFloat() * 100).toInt()
        }
        else {
            Log.d("MainActivity", "total duration is null!!!")
        }
        progressBar?.progress = progressPercentage
        Log.d("MainActivity", "prog bar percentage: "+progressPercentage)
        Log.d("MainActivity", "played time (sec): "+playedTimeSec)
        Log.d("MainActivity", "total duration: "+totalDuration)
    }

    private fun createNotificationChannel() {
        val channelId = "download_progress"
        val channelName = "Download Progress"
        val channelDescription = "Shows download progress notification"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
            //setSound(null, AudioAttributes.Builder().build())
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun isFilePresent(context: Context): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filePath = File(downloadsDir, "hit.mp3")
        return filePath.exists()
    }

    private fun hideButton() {
        binding.btnHits.visibility = View.GONE
    }

}