package com.phantombeats.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.phantombeats.data.security.OfflineCryptoManager
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.crypto.CipherInputStream

class EncryptedFileDataSource(
    private val cryptoManager: OfflineCryptoManager
) : BaseDataSource(false) {

    private var uri: Uri? = null
    private var fileInputStream: FileInputStream? = null
    private var cipherInputStream: CipherInputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val targetUri = dataSpec.uri
        this.uri = targetUri
        transferInitializing(dataSpec)

        val targetFile = resolveFile(targetUri)
        if (!targetFile.exists()) {
            throw IOException("Archivo cifrado no encontrado: ${targetFile.absolutePath}")
        }

        val fis = FileInputStream(targetFile)
        val iv = ByteArray(OfflineCryptoManager.IV_LENGTH_BYTES)
        val read = fis.read(iv)
        if (read != OfflineCryptoManager.IV_LENGTH_BYTES) {
            fis.close()
            throw EOFException("No se pudo leer IV del archivo cifrado.")
        }

        val cis = CipherInputStream(fis, cryptoManager.createDecryptCipher(iv))
        skipFully(cis, dataSpec.position)

        fileInputStream = fis
        cipherInputStream = cis
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val input = cipherInputStream ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }

        val bytesRead = input.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            cipherInputStream?.close()
        } finally {
            cipherInputStream = null
            fileInputStream?.close()
            fileInputStream = null
            uri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun resolveFile(uri: Uri): File {
        val explicitPath = uri.path
        if (!explicitPath.isNullOrBlank()) return File(explicitPath)

        val schemePart = uri.schemeSpecificPart
            ?.removePrefix("//")
            ?.trim()
            .orEmpty()

        if (schemePart.isBlank()) {
            throw IOException("URI inválida para archivo cifrado: $uri")
        }

        return File(schemePart)
    }

    private fun skipFully(input: CipherInputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }

            val singleByte = input.read()
            if (singleByte == -1) break
            remaining--
        }
    }
}

class EncryptedFileDataSourceFactory(
    private val cryptoManager: OfflineCryptoManager
) : DataSource.Factory {
    override fun createDataSource(): DataSource = EncryptedFileDataSource(cryptoManager)
}