package io.morgan.mio.feature.today.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

public fun mutateFeatureBackStack(stack: NavBackStack<NavKey>) {
    stack.addAll(emptyList())
}
