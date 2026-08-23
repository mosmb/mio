package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderAuthorizationLaunch
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaTotals
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderRelease
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

public fun providerCredentialsFixture(provider: ProviderKey = ProviderKey.ANILIST): ProviderCredentials =
    ProviderCredentials(
        provider = provider,
        accessToken = "fixture-access-value",
        refreshToken = "fixture-refresh-value",
        expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
        tokenType = "Fixture",
    )

public fun providerIdentityFixture(
    provider: ProviderKey = ProviderKey.ANILIST,
    accountId: ProviderAccountId = ProviderAccountId("account-fixture"),
): ProviderIdentity =
    ProviderIdentity(
        provider = provider,
        accountId = accountId,
        displayName = "Fixture Account",
        avatar = ProviderImageReference("https://images.invalid/avatar.png"),
    )

public fun providerMediaFixture(
    id: String = "media-fixture",
    providerMediaType: ProviderMediaType = ProviderMediaType.ANIME,
    relatedMediaIds: List<String> = emptyList(),
    totals: ProviderMediaTotals = ProviderMediaTotals(episodes = 12, chapters = null, volumes = null),
    releases: List<ProviderRelease> =
        listOf(
            ProviderRelease(
                mediaId = ProviderMediaId(id),
                unit = ProviderProgressUnit.EPISODE,
                number = 1,
                time = ProviderReleaseTime.Unknown,
            ),
        ),
    releaseYear: Int? = null,
    scorePercent: Int? = null,
): ProviderMedia =
    ProviderMedia(
        id = ProviderMediaId(id),
        type = providerMediaType,
        title = "Fixture Media",
        format = ProviderMediaFormat.TV,
        publicationStatus = ProviderMediaPublicationStatus.RELEASING,
        synopsis = null,
        poster = ProviderImageReference("https://images.invalid/poster.png"),
        banner = null,
        totals = totals,
        genres = listOf("Adventure", "Drama"),
        relatedMediaIds = relatedMediaIds.map(::ProviderMediaId),
        releases = releases,
        releaseYear = releaseYear,
        scorePercent = scorePercent,
    )

public fun providerMediaDetailsFixture(
    provider: ProviderKey = ProviderKey.ANILIST,
    root: ProviderMedia = providerMediaFixture(id = "101", relatedMediaIds = listOf("102")),
    related: List<ProviderMedia> = listOf(providerMediaFixture(id = "102")),
): ProviderMediaDetails = ProviderMediaDetails(provider, root, related)

public fun providerCollectionEntryFixture(
    id: String = "entry-fixture",
    accountId: ProviderAccountId = ProviderAccountId("account-fixture"),
    mediaId: String = "media-fixture",
    status: ProviderCollectionStatus = ProviderCollectionStatus.CURRENT,
    progressUnit: ProviderProgressUnit = ProviderProgressUnit.EPISODE,
    total: Int? = 12,
): ProviderCollectionEntry =
    ProviderCollectionEntry(
        id = ProviderCollectionEntryId(id),
        accountId = accountId,
        mediaId = ProviderMediaId(mediaId),
        status = status,
        progress = 1,
        progressUnit = progressUnit,
        total = total,
        updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
    )

public fun completeProviderImportFixture(
    provider: ProviderKey = ProviderKey.ANILIST,
    accountId: ProviderAccountId = ProviderAccountId("account-fixture"),
    media: List<ProviderMedia> = listOf(providerMediaFixture()),
    entries: List<ProviderCollectionEntry> =
        listOf(providerCollectionEntryFixture(accountId = accountId)),
): CompleteProviderImport =
    CompleteProviderImport(
        provider = provider,
        accountId = accountId,
        media = media,
        entries = entries,
    )

public fun providerBindingFixture(
    provider: ProviderKey = ProviderKey.ANILIST,
): io.morgan.mio.data.provider.api.ProviderBinding {
    val authenticator = FakeProviderAuthenticator(provider)
    val identityDataSource = FakeProviderIdentityDataSource(provider)
    val initialImportDataSource = FakeProviderInitialImportDataSource(provider)
    val mediaDetailDataSource = FakeProviderMediaDetailDataSource(provider)
    return io.morgan.mio.data.provider.api.ProviderBinding(
        provider = provider,
        authenticator = authenticator,
        identityDataSource = identityDataSource,
        initialImportDataSource = initialImportDataSource,
        mediaDetailDataSource = mediaDetailDataSource,
    )
}

public fun providerAuthorizationLaunchFixture(): ProviderAuthorizationLaunch =
    ProviderAuthorizationLaunch(
        authorizationUrl = "https://auth.invalid/authorize",
        transaction = ProviderAuthorizationTransaction("fixture-state", "fixture-verifier"),
    )

public fun providerSuccessFixture(): ProviderResult<CompleteProviderImport> =
    ProviderResult.Success(completeProviderImportFixture())
