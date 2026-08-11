package io.morgan.mio.data.collection

import io.morgan.mio.domain.account.AccountRepository

class RepositoryBackedLocalDataSource(
    private val repository: AccountRepository,
)
