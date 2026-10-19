package com.rpm.player.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rpm.player.core.net.WireJson
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where a session is kept while the app is closed. */
interface SessionStore {
    fun read(): Session?
    fun write(session: Session)
    fun clear()
}

/**
 * The session as one file in `noBackupFilesDir`, encrypted with an AES key that the Android Keystore
 * holds and never gives out. A backup does not include the file, and a copy of the file is useless
 * without the device that wrote it.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val file = File(context.noBackupFilesDir, "session.bin")

    override fun read(): Session? = runCatching {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
        WireJson.decodeFromString(Session.serializer(), plain.toString(Charsets.UTF_8))
    }.getOrNull()

    override fun write(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(WireJson.encodeToString(Session.serializer(), session).toByteArray())
        // Written to a new file and then renamed over the old one, so a crash during the write keeps the old
        // session and not half a file.
        val next = File(file.parentFile, file.name + ".new")
        next.writeBytes(sealed)
        if (!next.renameTo(file)) {
            file.delete()
            next.renameTo(file)
        }
    }

    override fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rpm.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
