package com.segment.analytics.next

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Storage
import com.segment.analytics.kotlin.core.StorageProvider
import com.segment.analytics.kotlin.core.utilities.FileEventStream
import com.segment.analytics.kotlin.core.utilities.PropertiesFile
import com.segment.analytics.kotlin.core.utilities.StorageImpl
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator

class EncryptedEventStream(
    directory: File,
    private val key: Key
) : FileEventStream(directory) {

    private var cipherOutputStream: CipherOutputStream? = null

    private val encryptedCipher = EncryptionUtil.getCipher(key, Cipher.ENCRYPT_MODE)

    override var fs: FileOutputStream?
        get() = super.fs
        set(value) {
            if (value == null) {
                cipherOutputStream = null
            }
            else {
                cipherOutputStream = CipherOutputStream(value, encryptedCipher)
            }
        }

    override fun write(content: String) {
        cipherOutputStream?.run {
            write(content.toByteArray())
            flush()
        }
    }

    override fun close() {
        cipherOutputStream?.close()
        super.close()
    }

    override fun readAsStream(source: String): InputStream? {
        val stream = super.readAsStream(source)
        return if (stream == null) {
            null
        } else {
            val cipher = EncryptionUtil.getCipher(key, Cipher.DECRYPT_MODE)
            CipherInputStream(super.readAsStream(source), cipher)
        }
    }
}

object EncryptedStorageProvider : StorageProvider {

    override fun createStorage(vararg params: Any): Storage {
        if (params.isEmpty() || params[0] !is Analytics || params[1] !is Key) {
            throw IllegalArgumentException("Invalid parameters for ConcreteStorageProvider. ConcreteStorageProvider requires at least 1 parameter and the first argument has to be an instance of Analytics")
        }

        val analytics = params[0] as Analytics
        val key = params[1] as Key
        val config = analytics.configuration

        val directory = File("/tmp/analytics-kotlin/${config.writeKey}")
        val eventDirectory = File(directory, "events")
        val fileIndexKey = "segment.events.file.index.${config.writeKey}"
        val userPrefs = File(directory, "analytics-kotlin-${config.writeKey}.properties")

        val propertiesFile = PropertiesFile(userPrefs)
        val eventStream = EncryptedEventStream(eventDirectory, key)
        return StorageImpl(propertiesFile, eventStream, analytics.store, config.writeKey, fileIndexKey, analytics.fileIODispatcher)
    }
}

object EncryptionUtil {
    private const val ALGORITHM = "AES"

    fun generateKey(): Key {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(128) // AES 128-bit key
        return keyGen.generateKey()
    }

    fun getCipher(key: Key, mode: Int): Cipher {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(mode, key)
        return cipher
    }
}