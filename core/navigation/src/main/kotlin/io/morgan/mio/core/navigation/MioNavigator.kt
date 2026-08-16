package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.NavKey

public interface MioNavigator {
    public fun navigate(destination: NavKey): Unit

    public fun goBack(): Boolean
}
