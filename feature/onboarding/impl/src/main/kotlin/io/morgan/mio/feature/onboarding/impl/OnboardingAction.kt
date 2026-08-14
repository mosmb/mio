package io.morgan.mio.feature.onboarding.impl

internal sealed interface OnboardingAction {
    data object ContinueWithAniList : OnboardingAction

    data object ContinueWithMyAnimeList : OnboardingAction

    data object Retry : OnboardingAction
}
