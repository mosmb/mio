package io.morgan.mio.domain.account

public interface ValidRepository {
    public fun observe(): kotlinx.coroutines.flow.Flow<String>
}
