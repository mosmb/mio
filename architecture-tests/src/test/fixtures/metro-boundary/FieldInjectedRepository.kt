package io.morgan.mio.data.account

import dev.zacsweers.metro.Inject

public class FieldInjectedRepository {
    @Inject
    public lateinit var repository: Any
}
