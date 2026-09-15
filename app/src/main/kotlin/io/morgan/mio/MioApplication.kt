package io.morgan.mio

import android.app.Application
import androidx.work.Configuration
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.di.AppGraph

public open class MioApplication :
    Application(),
    Configuration.Provider {
    internal lateinit var appGraph: AppGraph
        private set

    public override fun onCreate() {
        super.onCreate()
        appGraph = createAppGraph()
        appGraph.logger
        appGraph.pendingSyncWorkDispatcher.start(appGraph.processCoroutineScope)
    }

    internal open fun createAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>().create(this)

    override val workManagerConfiguration: Configuration
        get() = appGraph.workManagerConfiguration
}
