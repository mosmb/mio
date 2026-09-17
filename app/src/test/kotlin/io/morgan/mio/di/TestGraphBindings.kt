package io.morgan.mio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import coil3.ImageLoader
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.common.di.ProcessCoroutineScope
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.logging.MioLogger
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.network.NetworkAvailability
import io.morgan.mio.core.network.NetworkMonitor
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.repository.FakeAccountRepository
import io.morgan.mio.core.testing.repository.FakeCollectionRepository
import io.morgan.mio.core.testing.repository.FakeMediaRepository
import io.morgan.mio.core.testing.repository.FakePreferencesRepository
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.sync.PendingSyncWorkDispatcher
import io.morgan.mio.domain.account.AccountRepository
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.domain.sync.SyncWorkScheduler
import io.morgan.mio.session.FakeProviderAccountSessionRepository
import io.morgan.mio.sync.work.factory.MioWorkerFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Instant

@BindingContainer
internal class TestGraphBindings(
    private val database: MioDatabase,
    private val providerBinding: ProviderBinding = providerBindingFixture(),
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher,
    private val processCoroutineScope: CoroutineScope,
    private val logger: MioLogger,
    private val providerCredentialStore: ProviderCredentialStore,
    private val imageLoader: TrackingImageLoader,
    private val workerFactory: WorkerFactory,
    private val workManagerConfiguration: Configuration,
    private val accountRepository: AccountRepository,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val pendingSyncWorkDispatcher: PendingSyncWorkDispatcher =
        PendingSyncWorkDispatcher(database, SyncWorkScheduler { }),
) {
    @Provides
    fun provideBindingResolver(): io.morgan.mio.data.provider.api.ProviderBindingResolver =
        io.morgan.mio.data.provider.api.ProviderBindingResolver { provider ->
            providerBinding.takeIf { it.provider == provider }
        }

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
    @ProcessCoroutineScope
    fun provideProcessCoroutineScope(): CoroutineScope = processCoroutineScope

    @Provides
    fun provideLogger(): MioLogger = logger

    @Provides
    fun provideProviderCredentialStore(): ProviderCredentialStore = providerCredentialStore

    @Provides
    fun provideImageLoader(
        @ApplicationContext applicationContext: Context,
    ): ImageLoader {
        require(applicationContext.applicationContext.packageName == imageLoader.applicationPackageName)
        return imageLoader
    }

    @Provides
    fun provideWorkerFactory(factories: Set<MioWorkerFactory>): WorkerFactory = factories.let { workerFactory }

    @Provides
    fun provideWorkManagerConfiguration(): Configuration = workManagerConfiguration

    @Provides
    fun provideAccountRepository(requester: AccountRefreshRequester): AccountRepository =
        requester.let { accountRepository }

    @Provides
    fun provideMediaRepository(): MediaRepository = mediaRepository

    @Provides
    fun provideCollectionRepository(requester: CollectionRefreshRequester): CollectionRepository =
        requester.let { collectionRepository }

    @Provides
    fun providePreferencesRepository(): PreferencesRepository = preferencesRepository

    @Provides
    fun provideAccountSessionRepository(): AccountSessionRepository = accountSessionRepository

    @Provides
    fun provideNetworkMonitor(): NetworkMonitor = networkMonitor

    @Provides
    fun provideSyncAccountSessionFactory(): SyncAccountSessionFactory = SyncAccountSessionFactory { null }

    @Provides
    fun providePendingSyncWorkDispatcher(): PendingSyncWorkDispatcher = pendingSyncWorkDispatcher

    @Provides
    fun provideNavigationRegistry(
        onboardingDestination: OnboardingDestination,
        topLevelDestinations: Set<TopLevelDestination>,
        entryBuilders: Set<NavEntryBuilder>,
    ): NavigationRegistry =
        NavigationRegistry(
            onboardingDestination = onboardingDestination,
            topLevelDestinations = topLevelDestinations,
            entryBuilders = entryBuilders,
        )
}

internal class TestGraphFixture(
    context: Context,
    scheduler: TestCoroutineScheduler,
    providerBinding: ProviderBinding = providerBindingFixture(),
    taskClock: Clock? = null,
    testDispatcher: CoroutineDispatcher? = null,
) : AutoCloseable {
    val dispatcher: CoroutineDispatcher = testDispatcher ?: StandardTestDispatcher(scheduler)
    private val dataStoreScope = CoroutineScope(SupervisorJob() + dispatcher)
    val processCoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val temporaryDirectory: Path = Files.createTempDirectory("mio-task-six")
    val dataStoreFile: Path = temporaryDirectory.resolve("test.preferences_pb")
    val database: MioDatabase = MioDatabaseFactory.createInMemory(context)
    val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile.toFile() },
        )
    val clock: Clock =
        taskClock
            ?: object : Clock {
                override fun now(): Instant = Instant.parse("2026-08-11T12:00:00Z")
            }
    val networkMonitor = TestNetworkMonitor()
    val accountRepository = FakeAccountRepository()
    val mediaRepository = FakeMediaRepository()
    val collectionRepository = FakeCollectionRepository()
    val preferencesRepository = FakePreferencesRepository()
    val logger: MioLogger = MioLogger.None
    val providerCredentialStore: FakeProviderCredentialStore = FakeProviderCredentialStore()
    val accountSessionRepository: FakeProviderAccountSessionRepository =
        FakeProviderAccountSessionRepository(
            providerBinding = providerBinding,
            providerCredentialStore = providerCredentialStore,
        )
    val imageLoader: TrackingImageLoader =
        TrackingImageLoader(
            applicationPackageName = context.applicationContext.packageName,
            delegate = ImageLoader.Builder(context).build(),
        )
    val workerFactory: WorkerFactory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = null
        }
    val workManagerConfiguration: Configuration =
        Configuration.Builder().setWorkerFactory(workerFactory).build()
    val bindings =
        TestGraphBindings(
            database = database,
            providerBinding = providerBinding,
            dataStore = dataStore,
            clock = clock,
            defaultDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            processCoroutineScope = processCoroutineScope,
            logger = logger,
            providerCredentialStore = providerCredentialStore,
            imageLoader = imageLoader,
            workerFactory = workerFactory,
            workManagerConfiguration = workManagerConfiguration,
            accountRepository = accountRepository,
            mediaRepository = mediaRepository,
            collectionRepository = collectionRepository,
            preferencesRepository = preferencesRepository,
            accountSessionRepository = accountSessionRepository,
            networkMonitor = networkMonitor,
        )

    override fun close() {
        database.close()
        dataStoreScope.cancel()
        processCoroutineScope.cancel()
        imageLoader.shutdown()
        Files.deleteIfExists(dataStoreFile)
        Files.deleteIfExists(temporaryDirectory)
    }
}

internal class TestNetworkMonitor : NetworkMonitor {
    val values = MutableStateFlow(NetworkAvailability.UNAVAILABLE)
    override val availability: Flow<NetworkAvailability> = values
}

internal class TrackingImageLoader(
    internal val applicationPackageName: String,
    private val delegate: ImageLoader,
) : ImageLoader by delegate {
    var shutdownCalls: Int = 0
        private set

    override fun shutdown() {
        shutdownCalls += 1
        delegate.shutdown()
    }
}
