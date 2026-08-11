package io.morgan.mio

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.di.AppGraph

public class MioApplication : Application() {
    internal lateinit var appGraph: AppGraph
        private set

    public override fun onCreate() {
        super.onCreate()
        appGraph =
            createGraphFactory<AppGraph.Factory>()
                .create(this)
    }
}
