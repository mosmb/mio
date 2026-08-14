package io.morgan.mio

import android.app.Application
import androidx.work.Configuration
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.di.AppGraph

public class MioApplication :
    Application(),
    Configuration.Provider {
    internal lateinit var appGraph: AppGraph
        private set

    public override fun onCreate() {
        super.onCreate()
        appGraph =
            createGraphFactory<AppGraph.Factory>()
                .create(this)
        appGraph.logger
    }

    override val workManagerConfiguration: Configuration
        get() = appGraph.workManagerConfiguration
}
