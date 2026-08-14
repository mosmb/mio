package io.morgan.mio.di

import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock

object ProcessBindings {
    val clock: Clock = Clock.System
    val dispatcher = Dispatchers.Default
}
