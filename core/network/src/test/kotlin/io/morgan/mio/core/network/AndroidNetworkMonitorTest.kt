package io.morgan.mio.core.network

import android.content.Context
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidNetworkMonitorTest {
    @Test
    fun androidAdapterEmitsAnAdvisoryInitialStateWithoutWaitingForARealRequest() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val monitor = AndroidNetworkMonitor(connectivityManager)

            val emissions = monitor.availability.take(1).toList()

            assertEquals(listOf(NetworkAvailability.UNAVAILABLE), emissions)
        }

    @Test
    fun validatedAvailabilityAndLossEmitDistinctTransitionsAndUnregister() =
        runTest {
            val access = FakeConnectivityAccess(initialCapabilities = capabilities(validated = false))
            val monitor = AndroidNetworkMonitor(access)
            val emissions = mutableListOf<NetworkAvailability>()
            val collection = backgroundScope.launch { monitor.availability.take(3).toList(emissions) }
            runCurrent()

            access.callback.onCapabilitiesChanged(capabilities(validated = false))
            runCurrent()
            access.callback.onCapabilitiesChanged(capabilities(validated = true))
            runCurrent()
            access.callback.onLost()
            runCurrent()

            collection.join()
            assertEquals(
                listOf(
                    NetworkAvailability.UNAVAILABLE,
                    NetworkAvailability.AVAILABLE,
                    NetworkAvailability.UNAVAILABLE,
                ),
                emissions,
            )
            assertEquals(1, access.registrationCount)
            assertEquals(1, access.unregisterCount)
        }

    @Test
    fun blockedAndUnblockedTransitionsReuseTheLastCapabilities() =
        runTest {
            val access = FakeConnectivityAccess(initialCapabilities = capabilities(validated = true))
            val monitor = AndroidNetworkMonitor(access)
            val emissions = mutableListOf<NetworkAvailability>()
            val collection = backgroundScope.launch { monitor.availability.take(3).toList(emissions) }
            runCurrent()

            access.callback.onBlockedStatusChanged(false)
            runCurrent()
            access.callback.onBlockedStatusChanged(true)
            runCurrent()
            access.callback.onBlockedStatusChanged(false)
            runCurrent()

            collection.join()
            assertEquals(
                listOf(
                    NetworkAvailability.AVAILABLE,
                    NetworkAvailability.UNAVAILABLE,
                    NetworkAvailability.AVAILABLE,
                ),
                emissions,
            )
            assertEquals(1, access.unregisterCount)
        }

    @Test
    fun cancellingCollectionUnregistersExactlyOnce() =
        runTest {
            val access = FakeConnectivityAccess(initialCapabilities = null)
            val monitor = AndroidNetworkMonitor(access)
            val collection = backgroundScope.launch { monitor.availability.collect() }
            runCurrent()

            assertNotNull(access.registeredCallback)
            assertEquals(0, access.unregisterCount)

            collection.cancelAndJoin()
            assertEquals(1, access.registrationCount)
            assertEquals(1, access.unregisterCount)
        }

    @Test
    fun missingPermissionDuringInitialReadEmitsUnavailableAndNeverRegisters() =
        runTest {
            val access = FakeConnectivityAccess(initialFailure = SecurityException("missing permission"))

            val emissions = AndroidNetworkMonitor(access).availability.toList()

            assertEquals(listOf(NetworkAvailability.UNAVAILABLE), emissions)
            assertEquals(0, access.registrationCount)
            assertEquals(0, access.unregisterCount)
        }

    @Test
    fun missingPermissionDuringRegistrationClosesAfterUnavailable() =
        runTest {
            val access =
                FakeConnectivityAccess(
                    initialCapabilities = capabilities(validated = true),
                    registrationFailure = SecurityException("missing permission"),
                )

            val emissions = AndroidNetworkMonitor(access).availability.toList()

            assertEquals(
                listOf(NetworkAvailability.AVAILABLE, NetworkAvailability.UNAVAILABLE),
                emissions,
            )
            assertEquals(1, access.registrationCount)
            assertEquals(0, access.unregisterCount)
        }

    @Test
    fun teardownPermissionRaceDoesNotFailCancellation() =
        runTest {
            val access =
                FakeConnectivityAccess(
                    initialCapabilities = null,
                    unregisterFailure = SecurityException("permission revoked"),
                )
            val collection =
                backgroundScope.launch {
                    AndroidNetworkMonitor(access).availability.collect()
                }
            runCurrent()

            collection.cancelAndJoin()

            assertEquals(1, access.unregisterCount)
        }

    private fun capabilities(validated: Boolean): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { capabilities ->
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) {
                shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

    private class FakeConnectivityAccess(
        private val initialCapabilities: NetworkCapabilities? = null,
        private val initialFailure: SecurityException? = null,
        private val registrationFailure: SecurityException? = null,
        private val unregisterFailure: RuntimeException? = null,
    ) : ConnectivityAccess {
        var registeredCallback: ConnectivityCallback? = null
            private set
        var registrationCount: Int = 0
            private set
        var unregisterCount: Int = 0
            private set

        val callback: ConnectivityCallback
            get() = requireNotNull(registeredCallback)

        override fun currentCapabilities(): NetworkCapabilities? {
            initialFailure?.let { throw it }
            return initialCapabilities
        }

        override fun registerDefaultNetworkCallback(callback: ConnectivityCallback): ConnectivityRegistration {
            registrationCount += 1
            registrationFailure?.let { throw it }
            check(registeredCallback == null)
            registeredCallback = callback
            return ConnectivityRegistration {
                unregisterCount += 1
                unregisterFailure?.let { throw it }
            }
        }
    }
}
