package io.morgan.mio.core.logging

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BuildTypeMioLoggerTest {
    @Test
    fun buildTypeProvidesExactlyOneLoggerImplementationAndRepeatedConstructionIsSafe() {
        val implementationClass =
            listOf(
                "io.morgan.mio.core.logging.TimberMioLogger",
                "io.morgan.mio.core.logging.ReleaseMioLogger",
            ).firstNotNullOfOrNull { className ->
                runCatching { Class.forName(className) }.getOrNull()
            }
        assertNotNull(implementationClass)

        val first = implementationClass!!.getDeclaredConstructor().newInstance() as MioLogger
        val second = implementationClass.getDeclaredConstructor().newInstance() as MioLogger
        assertNotNull(first)
        assertNotNull(second)

        val timberClass = runCatching { Class.forName("timber.log.Timber") }.getOrNull()
        if (timberClass != null) {
            timberClass.getMethod("uprootAll").invoke(null)
            val logger = implementationClass.getDeclaredConstructor().newInstance() as MioLogger
            implementationClass.getDeclaredConstructor().newInstance()
            assertEquals(1, timberClass.getMethod("treeCount").invoke(null))

            shadowOf(android.os.Looper.getMainLooper()).idle()
            logger.log(MioLogLevel.DEBUG, MioLogEvent.NETWORK_REQUEST_STARTED)
            logger.log(MioLogLevel.INFO, MioLogEvent.NETWORK_REQUEST_SUCCEEDED)
            logger.log(MioLogLevel.WARNING, MioLogEvent.NETWORK_REQUEST_FAILED)
            logger.log(MioLogLevel.ERROR, MioLogEvent.NETWORK_REQUEST_FAILED)
            val eventNames = MioLogEvent.entries.map(Enum<*>::name).toSet()
            val logs =
                org.robolectric.shadows.ShadowLog
                    .getLogs()
                    .filter { log -> log.msg in eventNames }
            assertEquals(listOf(Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR), logs.map { it.type })
            assertEquals(
                listOf(
                    "NETWORK_REQUEST_STARTED",
                    "NETWORK_REQUEST_SUCCEEDED",
                    "NETWORK_REQUEST_FAILED",
                    "NETWORK_REQUEST_FAILED",
                ),
                logs.map { it.msg },
            )
            logs.forEach { log -> assertNull(log.throwable) }
            timberClass.getMethod("uprootAll").invoke(null)
        } else {
            first.log(MioLogLevel.ERROR, MioLogEvent.NETWORK_REQUEST_FAILED)
            val eventNames = MioLogEvent.entries.map(Enum<*>::name).toSet()
            assertEquals(
                emptyList<Any>(),
                org.robolectric.shadows.ShadowLog
                    .getLogs()
                    .filter { log -> log.msg in eventNames },
            )
        }
    }
}
