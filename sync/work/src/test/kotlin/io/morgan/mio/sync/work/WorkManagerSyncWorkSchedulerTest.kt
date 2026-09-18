package io.morgan.mio.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class WorkManagerSyncWorkSchedulerTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun scheduleUsesConnectedUniquePendingChain() {
        val accountId = AccountId("scheduler-account")
        val scheduler = WorkManagerSyncWorkScheduler(context, Dispatchers.Unconfined)

        kotlinx.coroutines.runBlocking { scheduler.schedule(accountId) }

        val workInfos =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerSyncWorkScheduler.uniqueWorkName(accountId))
                .get()
        val work = workInfos.single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertTrue(work.tags.contains(PendingSyncWorker::class.java.name))
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }
}
