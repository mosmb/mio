package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

public interface NavEntryBuilder {
    public val destinationKeyTypes: Set<KClass<out NavKey>>

    public fun EntryProviderScope<NavKey>.build(navigator: MioNavigator): Unit
}
