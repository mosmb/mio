package io.morgan.mio.data.media

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.media.local.RoomMediaSearchLocalDataSource
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaBrowseDataSource
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaBrowseCategory
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultMediaBrowseRepositoryTest {
    private lateinit var database: MioDatabase
    private val requests = mutableListOf<ProviderMediaBrowseRequest>()
    private var response: suspend (
        ProviderMediaBrowseRequest,
    ) -> ProviderResult<List<ProviderMedia>> = {
        ProviderResult.Success(listOf(providerMediaFixture("101", releases = emptyList())))
    }
    private val source =
        object : ProviderMediaBrowseDataSource {
            override val provider = ProviderKey.ANILIST

            override fun categories(type: ProviderMediaType) = ProviderBrowseCategory.entries

            override suspend fun browse(
                credentials: ProviderCredentials,
                request: ProviderMediaBrowseRequest,
            ): ProviderResult<List<ProviderMedia>> {
                requests +=
                    request
                return response(request)
            }
        }
    private lateinit var repository: DefaultMediaBrowseRepository

    @Before fun setUp() =
        kotlinx.coroutines.runBlocking {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(SELECTED_ACCOUNT_KEY, "browse", StoredConnectedProvider.ANILIST, "Fixture", null),
                AccountProviderIdEntity("browse", StoredConnectedProvider.ANILIST, "42"),
            )
            val base = providerBindingFixture(ProviderKey.ANILIST)
            val binding =
                ProviderBinding(
                    base.provider,
                    base.authenticator,
                    base.identityDataSource,
                    base.initialImportDataSource,
                    base.mediaDetailDataSource,
                    base.mediaSearchDataSource,
                    source,
                    base.syncDataSource,
                )
            val credentials =
                FakeProviderCredentialStore().also {
                    it.write(
                        providerCredentialsFixture(ProviderKey.ANILIST),
                    )
                }
            repository =
                DefaultMediaBrowseRepository(
                    AccountId("browse"),
                    binding,
                    credentials,
                    database,
                    RoomMediaSearchLocalDataSource(database),
                    object : Clock {
                        override fun now() = Instant.parse("2026-12-31T23:59:59Z")
                    },
                )
        }

    @After fun tearDown() {
        database.close()
    }

    @Test fun cacheSeparatesCategoriesAndSearchSurvivesFailuresAndDisconnectCascades() =
        runTest {
            assertNull(repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first())
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING),
            )
            assertEquals(1, repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()!!.size)
            assertNull(repository.observe(MediaType.ANIME, MediaBrowseCategory.TOP_RATED).first())
            assertNull(
                RoomMediaSearchLocalDataSource(
                    database,
                ).observeSearch("browse", "browse-v1", MediaSearchFilter.ANIME).first(),
            )
            response = { ProviderResult.Failure(ProviderFailure.NetworkUnavailable) }
            assertEquals(
                MediaSearchCommandOutcome.FAILED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING),
            )
            assertEquals(1, repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()!!.size)
            database.openHelper.writableDatabase.execSQL("DELETE FROM selected_account")
            assertNull(repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first())
            response = { ProviderResult.Success(emptyList()) }
            assertEquals(
                MediaSearchCommandOutcome.SUPERSEDED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING),
            )
        }

    @Test fun nextSeasonCrossesYearAndSuccessfulEmptyCacheIsDifferentFromNoCache() =
        runTest {
            response = { ProviderResult.Success(emptyList()) }
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.NEXT_SEASON),
            )
            assertEquals(2027, requests.single().year)
            assertEquals("WINTER", requests.single().season)
            assertEquals(
                emptyList<Media>(),
                repository.observe(MediaType.ANIME, MediaBrowseCategory.NEXT_SEASON).first(),
            )
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun rateLimitedRefreshKeepsCacheAndHonorsRetryAfterBeforeOneRetry() =
        runTest {
            repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING)
            val cached = repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()
            var calls = 0
            response = {
                if (calls++ == 0) {
                    ProviderResult.Failure(ProviderFailure.RateLimited(54.seconds))
                } else {
                    ProviderResult.Success(emptyList())
                }
            }
            val refresh = async { repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING) }
            runCurrent()
            assertEquals(1, calls)
            // Query Room without letting runTest auto-advance the pending retry timer.
            assertEquals(
                cached,
                kotlinx.coroutines.runBlocking {
                    repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()
                },
            )
            advanceTimeBy(53_999)
            runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(1)
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, refresh.await())
            assertEquals(2, calls)
            assertEquals(emptyList<Media>(), repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first())

            calls = 0
            response = {
                calls++
                ProviderResult.Failure(ProviderFailure.RateLimited(1.seconds))
            }
            assertEquals(
                MediaSearchCommandOutcome.FAILED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING),
            )
            assertEquals(2, calls)
        }

    @Test fun olderRequestCannotReplaceNewerCategoryCache() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            response =
                {
                    if (calls++ ==
                        0
                    ) {
                        entered.complete(Unit)
                        release.await()
                        ProviderResult.Success(listOf(providerMediaFixture("101", releases = emptyList())))
                    } else {
                        ProviderResult.Success(listOf(providerMediaFixture("102", releases = emptyList())))
                    }
                }
            val older = async { repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING) }
            entered.await()
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refresh(MediaType.ANIME, MediaBrowseCategory.TRENDING),
            )
            val newest = repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()
            release.complete(Unit)
            assertEquals(MediaSearchCommandOutcome.SUPERSEDED, older.await())
            assertEquals(1, repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first()!!.size)
            assertEquals(newest, repository.observe(MediaType.ANIME, MediaBrowseCategory.TRENDING).first())
        }
}
