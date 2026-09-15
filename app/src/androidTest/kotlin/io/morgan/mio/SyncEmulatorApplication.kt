package io.morgan.mio

import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.SyncEmulatorBindings

public class SyncEmulatorApplication : MioApplication() {
    internal override fun createAppGraph(): AppGraph {
        val enabled =
            androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("task20Fixture") == "true"
        return if (enabled) {
            createDynamicGraphFactory<AppGraph.Factory>(
                SyncEmulatorBindings(),
            ).create(this)
        } else {
            super.createAppGraph()
        }
    }
}
