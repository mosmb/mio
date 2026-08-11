package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.ImageReference

internal data class AccountProviderIdInput(
    val provider: StoredConnectedProvider,
    val remoteId: String,
) {
    init {
        require(remoteId.isNotBlank())
    }
}

internal fun Account.toPersistence(
    providerId: AccountProviderIdInput,
): Pair<SelectedAccountEntity, AccountProviderIdEntity> {
    require(providerId.provider == provider.toStoredValue())
    return Pair(
        SelectedAccountEntity(
            selectionKey = SELECTED_ACCOUNT_KEY,
            accountId = id.value,
            connectedProvider = provider.toStoredValue(),
            displayName = displayName,
            avatarReference = avatar?.value,
        ),
        AccountProviderIdEntity(
            accountId = id.value,
            provider = providerId.provider,
            remoteAccountId = providerId.remoteId,
        ),
    )
}

internal fun SelectedAccountEntity.toDomain(): Account =
    Account(
        id = AccountId(accountId),
        provider = connectedProvider.toDomain(),
        displayName = displayName,
        avatar = avatarReference?.let(::ImageReference),
    )

private fun ConnectedProvider.toStoredValue(): StoredConnectedProvider =
    when (this) {
        ConnectedProvider.ANILIST -> StoredConnectedProvider.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }

private fun StoredConnectedProvider.toDomain(): ConnectedProvider =
    when (this) {
        StoredConnectedProvider.ANILIST -> ConnectedProvider.ANILIST
        StoredConnectedProvider.MY_ANIME_LIST -> ConnectedProvider.MY_ANIME_LIST
    }
