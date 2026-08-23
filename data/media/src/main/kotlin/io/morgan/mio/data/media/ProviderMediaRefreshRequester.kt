package io.morgan.mio.data.media

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.data.media.mapper.ProviderMediaDetailsPersistenceMapper
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.CancellationException

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class ProviderMediaRefreshRequester(
    private val providerBinding: ProviderBinding,
    private val credentialStore: ProviderCredentialStore,
    private val localDataSource: RoomMediaLocalDataSource,
) : MediaRefreshRequester {
    override suspend fun refreshMedia(mediaId: MediaId) {
        val storedProvider = providerBinding.provider.toStoredValue()
        val remoteId =
            try {
                localDataSource.findMediaProviderId(mediaId, storedProvider)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SQLiteException) {
                throw MediaRefreshOperationalException()
            } ?: throw MediaRefreshOperationalException()

        val credentials =
            try {
                credentialStore.read(providerBinding.provider)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: ProviderCredentialStorageException) {
                throw MediaRefreshOperationalException()
            } ?: throw MediaRefreshOperationalException()

        val result =
            try {
                providerBinding.mediaDetailDataSource.fetchMediaDetails(
                    credentials = credentials,
                    mediaId = ProviderMediaId(remoteId),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        val details =
            when (result) {
                is ProviderResult.Success -> result.value
                is ProviderResult.Failure -> throw MediaRefreshOperationalException()
            }
        if (
            details.provider != providerBinding.provider ||
            details.root.id != ProviderMediaId(remoteId)
        ) {
            throw MediaRefreshOperationalException()
        }
        val snapshot = ProviderMediaDetailsPersistenceMapper.map(details)
        require(snapshot.owner.media.id == mediaId)

        try {
            localDataSource.replaceMediaDetailsSnapshot(snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw MediaRefreshOperationalException()
        }
    }

    private fun ProviderKey.toStoredValue(): StoredConnectedProvider =
        when (this) {
            ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
        }
}

private class MediaRefreshOperationalException : RuntimeException()
