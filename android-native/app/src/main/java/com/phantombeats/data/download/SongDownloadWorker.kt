package com.phantombeats.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.phantombeats.data.local.dao.SongDao
import com.phantombeats.domain.repository.StreamResolver
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class SongDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val songDao: SongDao,
    private val streamResolver: StreamResolver
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "song_id"
        private const val PENDING_DOWNLOAD_PATH = "__PENDING__"
    }

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID) ?: return Result.failure()

        return try {
            val song = songDao.getSongById(songId) ?: return Result.failure()

            if (song.localPath != null &&
                !song.localPath.startsWith(PENDING_DOWNLOAD_PATH) &&
                File(song.localPath).exists()
            ) {
                return Result.success()
            }

            setForeground(createForegroundInfo(song.title))

            val streamResult = streamResolver.getStreamUrl(songId)
            val streamUrl = streamResult.getOrThrow()

            val outDir = File(applicationContext.filesDir, "offline_audio")
            if (!outDir.exists()) {
                outDir.mkdirs()
            }

            val extension = if (streamUrl.contains(".m4a")) "m4a" else "mp3"
            val outFile = File(outDir, "$songId.$extension")

            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return Result.retry()
            }

            connection.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            songDao.updateLocalPath(songId, outFile.absolutePath)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val channelId = "song_downloads"
        val notificationId = title.hashCode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Descargas de Canciones",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Descargando Música")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
