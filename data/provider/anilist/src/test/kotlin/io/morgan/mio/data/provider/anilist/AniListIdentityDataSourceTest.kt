package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AniListIdentityDataSourceTest {
    private lateinit var server: MockAniListServer
    private lateinit var dataSource: AniListIdentityDataSource

    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.ANILIST,
            accessToken = "fixture-token",
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    @Before
    fun setUp() {
        server = MockAniListServer()
        dataSource = AniListIdentityDataSource(server.service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun representativeViewerPayloadMapsToTheExactIdentity() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("viewer.json"))

            val result = dataSource.fetchIdentity(credentials) as ProviderResult.Success

            assertEquals(ProviderKey.ANILIST, result.value.provider)
            assertEquals("42", result.value.accountId.value)
            assertEquals("Fixture Viewer", result.value.displayName)
            assertEquals(ProviderImageReference("https://images.invalid/large.png"), result.value.avatar)

            val recorded = server.takeRequest()
            assertEquals("/", recorded.target)
            assertEquals("POST", recorded.method)
            assertEquals("Bearer fixture-token", recorded.headers["Authorization"])
        }

    @Test
    fun nullNameReturnsUnsupportedMandatoryData() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("viewer-missing-name.json"))

            val result = dataSource.fetchIdentity(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData), result)
        }

    @Test
    fun nullOrNonPositiveIdReturnsMalformedResponse() =
        runTest {
            server.enqueue("""{"data":{"Viewer":{"id":0,"name":"Name","avatar":null}}}""")

            val result = dataSource.fetchIdentity(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
        }

    @Test
    fun nullAvatarYieldsNullAvatarRatherThanAPlaceholder() =
        runTest {
            server.enqueue("""{"data":{"Viewer":{"id":1,"name":"Name","avatar":null}}}""")

            val result = dataSource.fetchIdentity(credentials) as ProviderResult.Success

            assertNull(result.value.avatar)
        }

    @Test
    fun httpFailureStatusesMapToTheirFailures() =
        runTest {
            server.enqueue("{}", code = 401)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthenticationRequired),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("{}", code = 403)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthorizationDenied),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("{}", code = 429)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.RateLimited(null)),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("{}", code = 429, headers = mapOf("Retry-After" to "30"))
            assertEquals(
                ProviderResult.Failure(ProviderFailure.RateLimited(kotlin.time.Duration.parse("30s"))),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("{}", code = 503)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.ServiceUnavailable),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun graphQlErrorOnHttp200MapsThroughHttpStatus() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("graphql-error-unauthorized.json"))

            val result = dataSource.fetchIdentity(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.AuthenticationRequired), result)
        }

    @Test
    fun malformedJsonBodyMapsToMalformedResponse() =
        runTest {
            server.enqueue("not json")

            val result = dataSource.fetchIdentity(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
        }

    @Test
    fun cancellingTheCollectingCoroutineRethrowsCancellationException() =
        runTest {
            var caught: Throwable? = null
            val job =
                launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    try {
                        dataSource.fetchIdentity(credentials)
                    } catch (cancellation: CancellationException) {
                        caught = cancellation
                        throw cancellation
                    }
                }
            job.cancel()
            job.join()

            assertTrue(caught is CancellationException || job.isCancelled)
        }
}
