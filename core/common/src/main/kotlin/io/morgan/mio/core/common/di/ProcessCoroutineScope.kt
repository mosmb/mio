package io.morgan.mio.core.common.di

import dev.zacsweers.metro.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class ProcessCoroutineScope
