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
import com.phantombeats.data.security.OfflineCryptoManager
import com.phantombeats.domain.repository.StreamResolver
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.crypto.CipherOutputStream
import kotlinx.coroutines.CancellationException
import androidx.work.workDataOf

@HiltWorker
class SongDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val songDao: SongDao,
    private val streamResolver: StreamResolver,
    private val offlineCryptoManager: OfflineCryptoManager
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_PROGRESS_BYTES = "progress_bytes"
        const val KEY_PROGRESS_TOTAL_BYTES = "progress_total_bytes"
        private const val PENDING_DOWNLOAD_PATH = "__PENDING__"
    }

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID) ?: return Result.failure()
        var outFile: File? = null

        return try {
            val song = songDao.getSongById(songId) ?: return Result.failure()

            if (song.localPath != null &&
                !song.localPath.startsWith(PENDING_DOWNLOAD_PATH) &&
                File(song.localPath).exists()
            ) {
                return Result.success()
            }

            setForeground(createForegroundInfo(song.title))

            val streamResult = if (song.provider.equals("YouTube", ignoreCase = true)) {
                streamResolver.getStreamUrl(songId)
            } else {
                streamResolver.resolveBySearch("${song.title} - ${song.artist}")
            }
            val streamUrl = streamResult.getOrThrow()

            val outDir = File(applicationContext.filesDir, "offline_audio")
            if (!outDir.exists()) {
                outDir.mkdirs()
            }

            outFile = File(outDir, "$songId.pba")

            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                val code = connection.responseCode
                connection.disconnect()
                if (code in 500..599 && runAttemptCount < 3) {
                    return Result.retry()
                }
                songDao.updateLocalPath(songId, null)
                return Result.failure()
            }

            if (isStopped) {
                connection.disconnect()
                songDao.updateLocalPath(songId, null)
                outFile.delete()
                return Result.failure()
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            var downloadedBytes = 0L
            var bytesSinceLastProgress = 0L

            connection.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val cipher = offlineCryptoManager.createEncryptCipher()
                    val iv = cipher.iv
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOutput ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            if (isStopped) {
                                throw CancellationException("Download cancelled")
                            }

                            val read = input.read(buffer)
                            if (read <= 0) break
                            cipherOutput.write(buffer, 0, read)
                            downloadedBytes += read
                            bytesSinceLastProgress += read

                            if (bytesSinceLastProgress >= 256 * 1024) {
                                setProgress(
                                    workDataOf(
                                        KEY_PROGRESS_BYTES to downloadedBytes,
                                        KEY_PROGRESS_TOTAL_BYTES to totalBytes
                                    )
                                )
                                bytesSinceLastProgress = 0L
                            }
                        }
                    }
                }
            }
            connection.disconnect()

            setProgress(
                workDataOf(
                    KEY_PROGRESS_BYTES to downloadedBytes,
                    KEY_PROGRESS_TOTAL_BYTES to totalBytes
                )
            )

            songDao.updateLocalPath(songId, outFile.absolutePath)
            Result.success()
        } catch (_: CancellationException) {
            outFile?.takeIf { it.exists() }?.delete()
            songDao.updateLocalPath(songId, null)
            Result.failure()
        } catch (e: Exception) {
            val isTransient = e is SocketTimeoutException ||
                e is UnknownHostException ||
                e is ConnectException

            if (isTransient && runAttemptCount < 3) {
                Result.retry()
            } else {
                outFile?.takeIf { it.exists() }?.delete()
                songDao.updateLocalPath(songId, null)
                Result.failure()
            }
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
