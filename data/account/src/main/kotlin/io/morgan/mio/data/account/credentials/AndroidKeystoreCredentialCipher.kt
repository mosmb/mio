package io.morgan.mio.data.account.credentials

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.zacsweers.metro.Inject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidKeystoreCredentialCipher internal constructor(
    private val keyAlias: String,
) : CredentialCipher {
    @Inject
    internal constructor() : this(KEY_ALIAS)

    override fun encrypt(
        providerStorageKey: String,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(providerStorageKey.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size <= UByte.MAX_VALUE.toInt())
        return byteArrayOf(ENVELOPE_VERSION, iv.size.toByte()) + iv + ciphertext
    }

    override fun decrypt(
        providerStorageKey: String,
        envelope: ByteArray,
    ): ByteArray {
        require(envelope.size >= ENVELOPE_HEADER_SIZE)
        require(envelope[0].toInt() == ENVELOPE_VERSION.toInt())
        val ivLength = envelope[1].toInt() and 0xFF
        require(ivLength in MIN_IV_BYTES..MAX_IV_BYTES)
        val ciphertextOffset = ENVELOPE_HEADER_SIZE + ivLength
        require(envelope.size > ciphertextOffset)
        val iv = envelope.copyOfRange(ENVELOPE_HEADER_SIZE, ciphertextOffset)
        val ciphertext = envelope.copyOfRange(ciphertextOffset, envelope.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(providerStorageKey.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    override fun deleteKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        synchronized(this) {
            loadKeyStore().getKey(keyAlias, null)?.let { key -> return key as SecretKey }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(
                KeyGenParameterSpec
                    .Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setKeySize(AES_KEY_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            return keyGenerator.generateKey()
        }
    }

    private fun loadKey(): SecretKey =
        loadKeyStore().getKey(keyAlias, null) as? SecretKey
            ?: throw IllegalStateException("Credential key is missing")

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

    private companion object {
        const val KEYSTORE_PROVIDER: String = "AndroidKeyStore"
        const val KEY_ALIAS: String = "io.morgan.mio.credentials.v1"
        const val CIPHER_TRANSFORMATION: String = "AES/GCM/NoPadding"
        const val ENVELOPE_VERSION: Byte = 1
        const val ENVELOPE_HEADER_SIZE: Int = 2
        const val GCM_TAG_LENGTH_BITS: Int = 128
        const val AES_KEY_SIZE_BITS: Int = 256
        const val MIN_IV_BYTES: Int = 12
        const val MAX_IV_BYTES: Int = 16
    }
}
