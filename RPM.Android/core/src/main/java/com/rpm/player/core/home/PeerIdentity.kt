package com.rpm.player.core.home

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * This TV's key on the home network: an ECDSA P-256 key that is created once and kept in the Android
 * Keystore, where the private half never leaves. Computers pair with its public half (SubjectPublicKeyInfo
 * DER). The device id is derived from the public half, in the same way as the desktop's `PeerIdentity`.
 */
class PeerIdentity private constructor(private val key: PrivateKey, val publicKey: ByteArray) {

    val deviceId: String = PeerWire.idFor(publicKey)

    /** Signs [data] with SHA-256, in the form the computer checks: r‖s, not DER. */
    fun sign(data: ByteArray): ByteArray {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(data)
            sign()
        }
        return PeerWire.derToP1363(der)
    }

    companion object {
        private const val ALIAS = "rpm.peer"

        /** The key. It is created on first use. */
        fun load(): PeerIdentity {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(ALIAS)) {
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
                    initialize(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .build(),
                    )
                    generateKeyPair()
                }
            }
            val entry = store.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
            return PeerIdentity(entry.privateKey, entry.certificate.publicKey.encoded)
        }
    }
}
