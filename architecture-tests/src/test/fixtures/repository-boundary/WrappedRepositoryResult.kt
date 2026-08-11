package io.morgan.mio.domain.account

public interface WrappedRepository {
    public fun observe(): Resource<String>
}
