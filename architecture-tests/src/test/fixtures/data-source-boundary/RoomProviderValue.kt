package io.morgan.mio.data.media
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.provider.api.ProviderKey
class RoomValueLocalDataSource(private val database: MioDatabase) {
    fun lookup(provider: ProviderKey): String = provider.toString()
}
