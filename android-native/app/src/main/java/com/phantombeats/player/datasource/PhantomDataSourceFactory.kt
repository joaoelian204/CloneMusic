package com.phantombeats.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import com.phantombeats.data.security.OfflineCryptoManager

class PhantomDataSourceFactory(
    private val context: Context,
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
    private val cryptoManager: OfflineCryptoManager
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val defaultDataSource = DefaultDataSource(context, httpDataSourceFactory.createDataSource())
        val encryptedDataSource = EncryptedFileDataSource(cryptoManager)
        return SwitchingDataSource(defaultDataSource, encryptedDataSource)
    }
}

private class SwitchingDataSource(
    private val defaultDataSource: DataSource,
    private val encryptedDataSource: DataSource
) : DataSource {

    private var activeDataSource: DataSource = defaultDataSource

    override fun addTransferListener(transferListener: TransferListener) {
        defaultDataSource.addTransferListener(transferListener)
        encryptedDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        activeDataSource = if (isEncryptedUri(dataSpec.uri)) encryptedDataSource else defaultDataSource
        return activeDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = activeDataSource.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeDataSource.responseHeaders
    }

    override fun close() {
        activeDataSource.close()
    }

    private fun isEncryptedUri(uri: Uri): Boolean {
        if (uri.scheme.equals("encfile", ignoreCase = true)) return true
        if (!uri.scheme.equals("file", ignoreCase = true)) return false
        return uri.path?.lowercase()?.endsWith(".pba") == true
    }
}