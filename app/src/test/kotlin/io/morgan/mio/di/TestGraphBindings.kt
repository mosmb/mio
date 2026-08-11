package io.morgan.mio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.testing.repository.FakeAccountRepository
import io.morgan.mio.core.testing.repository.FakeCollectionRepository
import io.morgan.mio.core.testing.repository.FakeMediaRepository
import io.morgan.mio.core.testing.repository.FakePreferencesRepository
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.media.MediaRefreshRequester
import io.morgan.mio.domain.account.AccountRepository
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Instant

@BindingContainer
internal class TestGraphBindings(
    private val database: MioDatabase,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher,
    private val accountRepository: AccountRepository,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    @Provides
    fun provideDatabase(): MioDatabase = database

    @Provides
    fun provideDataStore(): DataStore<Preferences> = dataStore

    @Provides
    fun provideClock(): Clock = clock

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = defaultDispatcher

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = ioDispatcher

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = mainDispatcher

    @Provides
    fun provideAccountRepository(requester: AccountRefreshRequester): AccountRepository =
        requester.let { accountRepository }

    @Provides
    fun provideMediaRepository(requester: MediaRefreshRequester): MediaRepository = requester.let { mediaRepository }

    @Provides
    fun provideCollectionRepository(requester: CollectionRefreshRequester): CollectionRepository =
        requester.let { collectionRepository }

    @Provides
    fun providePreferencesRepository(): PreferencesRepository = preferencesRepository
}

internal class TestGraphFixture(
    context: Context,
    scheduler: TestCoroutineScheduler,
) : AutoCloseable {
    val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler)
    private val dataStoreScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val temporaryDirectory: Path = Files.createTempDirectory("mio-task-six")
    val dataStoreFile: Path = temporaryDirectory.resolve("test.preferences_pb")
    val database: MioDatabase = MioDatabaseFactory.createInMemory(context)
    val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile.toFile() },
        )
    val clock: Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-08-11T12:00:00Z")
        }
    val accountRepository = FakeAccountRepository()
    val mediaRepository = FakeMediaRepository()
    val collectionRepository = FakeCollectionRepository()
    val preferencesRepository = FakePreferencesRepository()
    val bindings =
        TestGraphBindings(
            database = database,
            dataStore = dataStore,
            clock = clock,
            defaultDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            accountRepository = accountRepository,
            mediaRepository = mediaRepository,
            collectionRepository = collectionRepository,
            preferencesRepository = preferencesRepository,
        )

    override fun close() {
        database.close()
        dataStoreScope.cancel()
        Files.deleteIfExists(dataStoreFile)
        Files.deleteIfExists(temporaryDirectory)
    }
}
