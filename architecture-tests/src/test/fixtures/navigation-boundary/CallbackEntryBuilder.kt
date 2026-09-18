package io.morgan.mio.feature.today.impl
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.feature.library.api.LibraryKey
import io.morgan.mio.feature.today.api.TodayKey
class CallbackEntryBuilder : NavEntryBuilder {
    fun build() { entry<TodayKey> { navigate(LibraryKey()) } }
}
