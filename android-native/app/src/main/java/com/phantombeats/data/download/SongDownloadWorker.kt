package com.phantombeats.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phantombeats.data.local.AppDatabase
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SongDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_STREAM_URL = "stream_url"
        private const val PENDING_DOWNLOAD_PATH = "__PENDING__"
    }

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID) ?: return Result.failure()
        val streamUrl = inputData.getString(KEY_STREAM_URL) ?: return Result.failure()

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val songDao = db.songDao()
            val song = songDao.getSongById(songId) ?: return Result.failure()

            if (song.localPath != null &&
                !song.localPath.startsWith(PENDING_DOWNLOAD_PATH) &&
                File(song.localPath).exists()
            ) {
                return Result.success()
            }

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

}
