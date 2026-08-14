package io.morgan.mio.sync.work.factory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DelegatingMioWorkerFactoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyFactoriesAndUnknownWorkersReturnNull() {
        val factory = DelegatingMioWorkerFactory(emptySet())

        assertNull(factory.createWorker(context, "unknown.worker", workerParameters()))
    }

    @Test
    fun allFactoriesAreVisitedInDeterministicOrderAndOneClaimWins() {
        val calls = mutableListOf<String>()
        val worker = testWorker()
        val factory =
            DelegatingMioWorkerFactory(
                setOf(
                    FirstFactory(calls),
                    SecondFactory(calls, worker),
                ),
            )

        assertSame(worker, factory.createWorker(context, "claimed.worker", workerParameters()))
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun duplicateClaimsFailAfterEveryFactoryHasBeenAsked() {
        val calls = mutableListOf<String>()
        val first = testWorker()
        val second = testWorker()
        val factory =
            DelegatingMioWorkerFactory(
                setOf(
                    ClaimingFactory("first", calls, first),
                    ClaimingFactory("second", calls, second),
                ),
            )

        val failure =
            assertThrows(IllegalStateException::class.java) {
                factory.createWorker(context, "duplicate.worker", workerParameters())
            }

        assertEquals("Multiple MioWorkerFactory instances claimed a worker", failure.message)
        assertEquals(listOf("first", "second"), calls)
    }

    private fun testWorker(): ListenableWorker = TestWorker(context, workerParameters())

    @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST", "USELESS_CAST")
    private fun workerParameters(): WorkerParameters {
        val worker = TestListenableWorkerBuilder.from(context, TestWorker::class.java).build()
        val field = ListenableWorker::class.java.getDeclaredField("mWorkerParams")
        field.isAccessible = true
        return field.get(worker) as WorkerParameters
    }

    public class TestWorker(
        context: Context,
        parameters: WorkerParameters,
    ) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }

    private class FirstFactory(
        private val calls: MutableList<String>,
    ) : MioWorkerFactory {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? {
            calls += "first"
            return null
        }
    }

    private class SecondFactory(
        private val calls: MutableList<String>,
        private val worker: ListenableWorker,
    ) : MioWorkerFactory {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? {
            calls += "second"
            return worker
        }
    }

    private class ClaimingFactory(
        private val label: String,
        private val calls: MutableList<String>,
        private val worker: ListenableWorker,
    ) : MioWorkerFactory {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker {
            calls += label
            return worker
        }
    }
}
