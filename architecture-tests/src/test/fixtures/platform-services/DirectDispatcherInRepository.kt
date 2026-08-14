package io.morgan.mio.data.account

import kotlinx.coroutines.Dispatchers

class DirectDispatcherInRepository {
    val dispatcher = Dispatchers.IO
}
