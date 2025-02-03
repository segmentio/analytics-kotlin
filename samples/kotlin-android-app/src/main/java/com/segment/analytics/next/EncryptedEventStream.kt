package com.segment.analytics.next

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.android.utilities.AndroidKVS
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.utilities.FileEventStream
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class EncryptedEventStream(
    directory: File,
    val key: ByteArray
) : FileEventStream(directory) {

    private val ivSize = 16

    override fun write(content: String) {
        fs?.run {
            // generate a different iv for every content
            val iv = ByteArray(ivSize).apply {
                SecureRandom().nextBytes(this)
            }
            val cipher = getCipher(Cipher.ENCRYPT_MODE, iv, key)
            val encryptedContent = cipher.doFinal(content.toByteArray())

            write(iv)
            // write the size of the content, so decipher knows
            // the length of the content
            write(writeInt(encryptedContent.size))
            write(encryptedContent)
            flush()
        }
    }

    override fun readAsStream(source: String): InputStream? {
        val stream = super.readAsStream(source)
        return if (stream == null) {
            null
        } else {
            // the DecryptingInputStream decrypts the steam
            // and uses a LimitedInputStream to read the exact
            // bytes of a chunk of content
            DecryptingInputStream(stream)
        }
    }


    private fun getCipher(mode: Int, iv: ByteArray, key: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(mode, keySpec, ivSpec)
        return cipher
    }

    private fun writeInt(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }

    private fun readInt(input: InputStream): Int {
        val bytes = input.readNBytes(4)
        return (bytes[0].toInt() and 0xFF shl 24) or
                (bytes[1].toInt() and 0xFF shl 16) or
                (bytes[2].toInt() and 0xFF shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private inner class DecryptingInputStream(private val input: InputStream) : InputStream() {
        private var currentCipherInputStream: CipherInputStream? = null
        private var remainingBytes = 0
        private var endOfStream = false

        private fun setupNextBlock(): Boolean {
            if (endOfStream) return false

            try {
                // Read IV
                val iv = input.readNBytes(ivSize)
                if (iv.size < ivSize) {
                    endOfStream = true
                    return false
                }

                // Read content size
                remainingBytes = readInt(input)
                if (remainingBytes <= 0) {
                    endOfStream = true
                    return false
                }

                // Setup cipher
                val cipher = getCipher(Cipher.DECRYPT_MODE, iv, key)

                // Create new cipher stream
                currentCipherInputStream = CipherInputStream(
                    LimitedInputStream(input, remainingBytes.toLong()),
                    cipher
                )
                return true
            } catch (e: Exception) {
                endOfStream = true
                return false
            }
        }

        override fun read(): Int {
            if (currentCipherInputStream == null && !setupNextBlock()) {
                return -1
            }

            val byte = currentCipherInputStream?.read() ?: -1
            if (byte == -1) {
                currentCipherInputStream = null
                return read() // Try next block
            }
            return byte
        }

        override fun close() {
            currentCipherInputStream?.close()
            input.close()
        }
    }

    // Helper class to limit reading to current encrypted block
    private class LimitedInputStream(
        private val input: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val result = input.read()
            if (result >= 0) remaining--
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val result = input.read(b, off, minOf(len, remaining.toInt()))
            if (result >= 0) remaining -= result
            return result
        }

        override fun close() {
            // Don't close the underlying stream
        }
    }
}

class EncryptedStorageProvider(val key: ByteArray) : StorageProvider {

    override fun createStorage(vararg params: Any): Storage {

        if (params.size < 2 || params[0] !is Analytics || params[1] !is Context) {
            throw IllegalArgumentException("""
                Invalid parameters for EncryptedStorageProvider. 
                EncryptedStorageProvider requires at least 2 parameters.
                 The first argument has to be an instance of Analytics,
                 an the second argument has to be an instance of Context
            """.trimIndent())
        }

        val analytics = params[0] as Analytics
        val context = params[1] as Context
        val config = analytics.configuration

        val eventDirectory = context.getDir("segment-disk-queue", Context.MODE_PRIVATE)
        val fileIndexKey = "segment.events.file.index.${config.writeKey}"
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("analytics-android-${config.writeKey}", Context.MODE_PRIVATE)

        val propertiesFile = AndroidKVS(sharedPreferences)
        // use the key from constructor or get it from share preferences
        val eventStream = EncryptedEventStream(eventDirectory, key)
        return StorageImpl(propertiesFile, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }
}