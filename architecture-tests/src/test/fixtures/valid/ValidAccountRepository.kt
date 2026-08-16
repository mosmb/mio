package io.morgan.mio.domain.account

public interface ValidAccountRepository {
    public fun observe(): kotlinx.coroutines.flow.Flow<String>
}
