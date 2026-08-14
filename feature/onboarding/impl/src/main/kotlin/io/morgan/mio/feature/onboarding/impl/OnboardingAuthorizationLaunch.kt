package io.morgan.mio.feature.onboarding.impl

internal class OnboardingAuthorizationLaunch(
    val authorizationUri: String,
) {
    init {
        require(authorizationUri.isNotBlank())
    }

    override fun toString(): String = "OnboardingAuthorizationLaunch([REDACTED])"
}
