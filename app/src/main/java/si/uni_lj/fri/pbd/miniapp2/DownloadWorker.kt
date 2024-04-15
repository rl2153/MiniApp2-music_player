package si.uni_lj.fri.pbd.miniapp2

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {


    override suspend fun doWork(): Result {

        if (isInternetConnected(applicationContext)) {
            var downloadSuccess = false
                val url = "https://lrss.fri.uni-lj.si/Veljko/downloads/hit.mp3"
                val fileName = "hit.mp3"
                withContext(Dispatchers.IO) {
                    try {
                        val urlConnection = URL(url).openConnection()
                        urlConnection.connect()
                        val inputStream = BufferedInputStream(
                            urlConnection.getInputStream()
                        )
                        // Get the Downloads directory path
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                        val file = File(downloadsDir, fileName)
                        val outputStream: OutputStream = FileOutputStream(file)
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        val fileSize: Long = urlConnection.contentLength.toLong()
                        var totalBytesRead: Long = 0
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val progress =
                                ((totalBytesRead.toFloat() / fileSize.toFloat()) * 100).toInt()
                            showDownloadProgressNotification(applicationContext, progress)

                        }
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()

                        downloadSuccess = true

                    } catch (e: IOException) {
                        e.printStackTrace()
                        // Handle error, notify user if needed
                        Log.d("MAIN", "download error")
                        downloadSuccess = false
                        //Toast.makeText(this@MainActivity, "download failed", Toast.LENGTH_SHORT).show()
                    }
                }
                return if (downloadSuccess) {
                    Result.success()
                } else {
                    Result.failure()
                }
            } else {
                Log.d("DownloadWorker", "no internet!")
                return Result.failure()
            }
    }

    private fun isInternetConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    private fun showDownloadProgressNotification(context: Context, progress: Int) {
        val channelId = "download_progress"
        val notificationId = 1

        // create a notification builder
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("Downloading...")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            //.setContentIntent(pendinIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationId, builder.build())
        //Log.d("DownloadWorker", "notification added")
    }
}