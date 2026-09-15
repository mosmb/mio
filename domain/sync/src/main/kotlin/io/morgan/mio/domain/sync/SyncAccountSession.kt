package io.morgan.mio.domain.sync

public interface SyncAccountSession : AutoCloseable {
    public val synchronize: SynchronizeAccountUseCase

    override fun close()
}
