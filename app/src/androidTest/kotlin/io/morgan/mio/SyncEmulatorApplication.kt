package io.morgan.mio

import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.SyncEmulatorBindings

public class SyncEmulatorApplication : MioApplication() {
    internal override fun createAppGraph(): AppGraph {
        val arguments =
            androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
        val task20Fixture = arguments.getString("task20Fixture") == "true"
        val task21Fixture = arguments.getString("task21Fixture") == "true"
        check(!task21Fixture || task20Fixture) {
            "Task 21 fixture requires the existing task20Fixture composition flag"
        }
        val enabled = task20Fixture
        return if (enabled) {
            createDynamicGraphFactory<AppGraph.Factory>(
                SyncEmulatorBindings(super.createAppGraph().providerCredentialStore),
            ).create(this)
        } else {
            super.createAppGraph()
        }
    }
}
