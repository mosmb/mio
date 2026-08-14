package io.morgan.mio.navigation

import android.content.Context
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.TopLevelSlot
import io.morgan.mio.di.AppGraph
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NavigationRegistrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun productionRegistryContainsTheFourTopLevelDestinationsInOrder() {
        val graph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            val registry = graph.navigationRegistry

            assertEquals(
                listOf(TopLevelSlot.TODAY, TopLevelSlot.LIBRARY, TopLevelSlot.DISCOVER, TopLevelSlot.YOU),
                registry.topLevelDestinations.map { destination -> destination.slot },
            )
        } finally {
            graph.database.close()
            graph.processCoroutineScope.cancel()
            graph.imageLoader.shutdown()
        }
    }

    @Test
    fun productionProviderResolvesEveryRegisteredRootAndRejectsUnknownKeys() {
        val graph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            val registry = graph.navigationRegistry
            val navigator = NoOpNavigator
            val provider = registry.entryProvider(navigator)
            val rootKeys =
                listOf(registry.onboardingDestination.key) +
                    registry.topLevelDestinations.map { destination -> destination.key }

            assertEquals(5, rootKeys.size)
            assertEquals(5, rootKeys.map { key -> key::class }.toSet().size)
            rootKeys.forEach { key -> assertNotNull(provider(key)) }
            assertThrows(Throwable::class.java) { provider(UnknownNavigationTestKey) }
        } finally {
            graph.database.close()
            graph.processCoroutineScope.cancel()
            graph.imageLoader.shutdown()
        }
    }

    @Test
    fun registryIsProcessScopedWithinTheSameGraphInstance() {
        val graph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            assertSame(graph.navigationRegistry, graph.navigationRegistry)
        } finally {
            graph.database.close()
            graph.processCoroutineScope.cancel()
            graph.imageLoader.shutdown()
        }
    }

    private object NoOpNavigator : MioNavigator {
        override fun navigate(destination: NavKey): Unit = Unit

        override fun goBack(): Boolean = false
    }
}

private data object UnknownNavigationTestKey : NavKey
