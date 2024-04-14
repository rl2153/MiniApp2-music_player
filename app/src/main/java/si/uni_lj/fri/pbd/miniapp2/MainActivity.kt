package si.uni_lj.fri.pbd.miniapp2

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import si.uni_lj.fri.pbd.miniapp2.MediaPlayerService.Companion.ACTION_START
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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
        setContentView(binding.root)

        progressBar = binding.progressBar
        trackDuration = binding.viewTrackDuration

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

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "Starting and binding service");
        val intent = Intent(this, MediaPlayerService::class.java)
        startService(intent)
        intent.action = ACTION_START
        bindService(intent, mConnection, 0);

        registerReceiver(broadcastReceiver, IntentFilter(MediaPlayerService.ACTION_UPDATE_ELAPSED_TIME))

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


}