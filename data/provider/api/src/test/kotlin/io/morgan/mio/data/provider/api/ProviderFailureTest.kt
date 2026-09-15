package io.morgan.mio.data.provider.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

class ProviderFailureTest {
    @Test
    fun failuresExposeOnlyTheSpecifiedRecoveryDirective() {
        assertEquals(ProviderRecovery.RETRY, ProviderFailure.NetworkUnavailable.recovery)
        assertEquals(ProviderRecovery.RETRY, ProviderFailure.Timeout.recovery)
        assertEquals(ProviderRecovery.RETRY, ProviderFailure.RateLimited(1.seconds).recovery)
        assertEquals(ProviderRecovery.RETRY, ProviderFailure.ServiceUnavailable.recovery)
        assertEquals(ProviderRecovery.REAUTHENTICATE, ProviderFailure.AuthenticationRequired.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.AuthorizationDenied.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.InvalidAuthorizationCallback.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.MalformedResponse.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.UnsupportedMandatoryData.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.MutationRejected.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.MutationConflict.recovery)
        assertEquals(ProviderRecovery.DO_NOT_RETRY, ProviderFailure.Unexpected.recovery)
    }

    @Test
    fun rateLimitRetryAfterCannotBeNegative() {
        assertThrows<IllegalArgumentException> {
            ProviderFailure.RateLimited((-1).seconds)
        }
        assertEquals(null, ProviderFailure.RateLimited(null).retryAfter)
    }

    @Test
    fun resultWrapsOnlyCanonicalSuccessOrFailure() {
        val success: ProviderResult<String> = ProviderResult.Success("normalized")
        val failure: ProviderResult<String> = ProviderResult.Failure(ProviderFailure.Timeout)

        assertEquals("normalized", (success as ProviderResult.Success).value)
        assertEquals(ProviderFailure.Timeout, (failure as ProviderResult.Failure).failure)
    }
}
