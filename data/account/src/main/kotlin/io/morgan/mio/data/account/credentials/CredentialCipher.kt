package io.morgan.mio.data.account.credentials

internal interface CredentialCipher {
    fun encrypt(
        providerStorageKey: String,
        plaintext: ByteArray,
    ): ByteArray

    fun decrypt(
        providerStorageKey: String,
        envelope: ByteArray,
    ): ByteArray

    fun deleteKey()
}
