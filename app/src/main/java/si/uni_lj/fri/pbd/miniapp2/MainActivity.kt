package si.uni_lj.fri.pbd.miniapp2

import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import si.uni_lj.fri.pbd.miniapp2.MediaPlayerService.Companion.ACTION_START
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var mediaPlayerService: MediaPlayerService? = null
    var mediaPlayerServiceBound: Boolean = false

    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressBar = binding.progressBar


    }


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

        // TODO: uncomment this and write the code to the Service
        Log.d(TAG, "Starting and binding service");
        val intent = Intent(this, MediaPlayerService::class.java)
        startService(intent)
        intent.action = ACTION_START

        // TODO: then uncomment this to bind the Service
        bindService(intent, mConnection, 0);

        // button listeners
        binding.btnPlay.setOnClickListener {
            mediaPlayerService?.playAudio()
        }

        binding.btnPause.setOnClickListener {
            mediaPlayerService?.pauseAudio()
        }
    }


    override fun onStop() {
        super.onStop()
        // TODO: if the Service is bound, unbind it
        if (mediaPlayerServiceBound) {
            unbindService(mConnection)
            mediaPlayerServiceBound = false
        }
    }


}