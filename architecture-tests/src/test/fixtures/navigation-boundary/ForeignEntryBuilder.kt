package io.morgan.mio.feature.today.impl
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.feature.library.api.LibraryKey
class ForeignEntryBuilder : NavEntryBuilder {
    fun build() { entry<LibraryKey> {} }
}
