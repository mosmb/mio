package io.morgan.mio.feature.today.api

import io.morgan.mio.core.navigation.NavEntryBuilder

private object EntryBuilderInFeatureApi : NavEntryBuilder {
    override val destinationKeyTypes = emptySet<kotlin.reflect.KClass<out androidx.navigation3.runtime.NavKey>>()
}
