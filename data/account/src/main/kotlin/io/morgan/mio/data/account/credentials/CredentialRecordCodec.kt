package io.morgan.mio.data.account.credentials

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Instant

@Inject
internal class CredentialRecordCodec {
    internal fun encode(credentials: ProviderCredentials): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(PAYLOAD_VERSION)
            data.writeString(storageKey(credentials.provider))
            data.writeString(credentials.accessToken)
            data.writeOptionalString(credentials.refreshToken)
            data.writeOptionalInstant(credentials.expiresAt)
            data.writeString(credentials.tokenType)
        }
        return output.toByteArray()
    }

    internal fun decode(
        expectedStorageKey: String,
        payload: ByteArray,
    ): ProviderCredentials {
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                require(data.readInt() == PAYLOAD_VERSION)
                val encodedStorageKey = data.readString()
                require(encodedStorageKey == expectedStorageKey)
                val provider = providerForStorageKey(encodedStorageKey)
                val accessToken = data.readString()
                val refreshToken = data.readOptionalString()
                val expiresAt = data.readOptionalInstant()
                val tokenType = data.readString()
                require(data.available() == 0)
                return ProviderCredentials(
                    provider = provider,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    tokenType = tokenType,
                )
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("Credential record is invalid")
        }
    }

    internal fun storageKey(provider: ProviderKey): String =
        when (provider) {
            ProviderKey.ANILIST -> ANILIST_STORAGE_KEY
            ProviderKey.MY_ANIME_LIST -> MY_ANIME_LIST_STORAGE_KEY
        }

    private fun providerForStorageKey(storageKey: String): ProviderKey =
        when (storageKey) {
            ANILIST_STORAGE_KEY -> ProviderKey.ANILIST
            MY_ANIME_LIST_STORAGE_KEY -> ProviderKey.MY_ANIME_LIST
            else -> throw IllegalArgumentException("Credential provider key is invalid")
        }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(value.isNotBlank() && bytes.isNotEmpty() && bytes.size <= MAX_FIELD_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeOptionalString(value: String?) {
        writeOptionalMarker(value != null)
        if (value != null) writeString(value)
    }

    private fun DataOutputStream.writeOptionalInstant(value: Instant?) {
        writeOptionalMarker(value != null)
        if (value != null) writeLong(value.toEpochMilliseconds())
    }

    private fun DataOutputStream.writeOptionalMarker(present: Boolean) {
        writeByte(if (present) 1 else 0)
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 1..MAX_FIELD_BYTES)
        require(available() >= length)
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8).also { value -> require(value.isNotBlank()) }
    }

    private fun DataInputStream.readOptionalString(): String? =
        when (readUnsignedByte()) {
            0 -> null
            1 -> readString()
            else -> throw IllegalArgumentException("Credential optional marker is invalid")
        }

    private fun DataInputStream.readOptionalInstant(): Instant? =
        when (readUnsignedByte()) {
            0 -> null
            1 -> Instant.fromEpochMilliseconds(readLong())
            else -> throw IllegalArgumentException("Credential optional marker is invalid")
        }

    private companion object {
        const val PAYLOAD_VERSION: Int = 1
        const val MAX_FIELD_BYTES: Int = 64 * 1024
        const val ANILIST_STORAGE_KEY: String = "credential.anilist"
        const val MY_ANIME_LIST_STORAGE_KEY: String = "credential.myanimelist"
    }
}
