package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.io.path.isDirectory

class ArchitectureRuleFixturesTest {
    private val repositoryRoot: Path
        get() =
            Path
                .of(
                    requireNotNull(System.getProperty("mio.project.root")) {
                        "mio.project.root must point to the repository root"
                    },
                ).toAbsolutePath()
                .normalize()

    @Test
    fun validFixturesPassEveryRule() {
        val files =
            loadFixtureDirectory(
                directoryName = "valid",
                ownerModule = ":domain:model",
                ownerByFilename =
                    mapOf(
                        "ValidDomain.kt" to ":domain:model",
                        "ValidDomainModel.kt" to ":domain:model",
                        "ValidRepositoryContract.kt" to ":domain:account",
                        "ValidFakeRepository.kt" to ":core:testing",
                        "ValidFeatureImpl.kt" to ":feature:today:impl",
                        "ValidCapabilityData.kt" to ":data:progress",
                        "ValidProvider.kt" to ":data:provider:anilist",
                        "ValidLogging.kt" to ":core:logging",
                        "ValidFeatureApi.kt" to ":feature:today:api",
                        "ValidRoomEntity.kt" to ":core:database",
                        "ValidRoomDao.kt" to ":core:database",
                        "ValidRoomLocalDataSource.kt" to ":data:media",
                        "ValidDataStoreLocalDataSource.kt" to ":data:preferences",
                        "ValidRepositoryImplementation.kt" to ":data:media",
                        "ValidMetroContribution.kt" to ":data:media",
                        "ValidNetworkFactory.kt" to ":core:network",
                        "ValidProviderContract.kt" to ":data:provider:api",
                        "ValidProviderContractFake.kt" to ":core:testing",
                        "ValidCapabilityProviderConsumer.kt" to ":data:account",
                    ),
            )

        enforcePackageOwnership(files)
        enforceDomainPurity(files)
        enforcePresentationIsolation(files)
        enforceFeatureApiIsolation(files)
        enforceProviderIsolation(files)
        enforceNetworkBoundary(files)
        enforceNavigationBoundaries(files)
        enforceProviderContractOwnership(files)
        enforceProviderContractsDoNotEscape(files)
        enforceLoggingBoundary(files)
        enforceSharedUiIsolation(files)
        enforceGloballyForbiddenImports(files)
        enforceDomainModelOwnership(files)
        enforceDomainImmutability(files)
        enforceRepositoryBoundaries(files)
        enforcePersistenceTypeOwnership(files)
        enforcePersistenceDoesNotEscape(files)
        enforceOneMechanismPerDataSource(files)
        enforceMetroBoundaries(files)
    }

    @Test
    fun wrongPackageIsRejected() {
        assertFixtureRejected("package-ownership", "WrongPackage.kt", ":domain:model", ::enforcePackageOwnership)
    }

    @Test
    fun androidDomainTypeIsRejected() {
        assertFixtureRejected("domain-purity", "AndroidDomainType.kt", ":domain:model", ::enforceDomainPurity)
    }

    @Test
    fun domainLoggerIsRejected() {
        assertFixtureRejected("domain-purity", "DomainLogger.kt", ":domain:model", ::enforceDomainPurity)
    }

    @Test
    fun featureDataImportIsRejected() {
        assertFixtureRejected(
            "presentation-isolation",
            "FeatureImportsData.kt",
            ":feature:today:impl",
            ::enforcePresentationIsolation,
        )
    }

    @Test
    fun crossFeatureImplementationImportIsRejected() {
        assertFixtureRejected(
            "presentation-isolation",
            "FeatureImportsOtherImpl.kt",
            ":feature:today:impl",
            ::enforcePresentationIsolation,
        )
    }

    @Test
    fun composableFeatureApiIsRejected() {
        assertFixtureRejected(
            "feature-api-isolation",
            "ApiComposable.kt",
            ":feature:today:api",
            ::enforceFeatureApiIsolation,
        )
    }

    @Test
    fun broadDomainFeatureApiImportIsRejected() {
        assertFixtureRejected(
            "feature-api-isolation",
            "ApiImportsBroadDomain.kt",
            ":feature:today:api",
            ::enforceFeatureApiIsolation,
        )
    }

    @Test
    fun providerLeakIsRejected() {
        assertFixtureRejected(
            "provider-isolation",
            "ProviderLeak.kt",
            ":feature:today:impl",
            ::enforceProviderIsolation,
        )
    }

    @Test
    fun providerApiLeakIsRejected() {
        assertFixtureRejected(
            "provider-isolation",
            "FeatureImportsProviderApi.kt",
            ":feature:today:impl",
            ::enforceProviderIsolation,
        )
    }

    @Test
    fun crossProviderImportIsRejected() {
        assertFixtureRejected(
            "provider-isolation",
            "CrossProviderImport.kt",
            ":data:provider:anilist",
            ::enforceProviderIsolation,
        )
    }

    @Test
    fun providerCapabilityImportIsRejected() {
        assertFixtureRejected(
            "provider-isolation",
            "ProviderImportsRepositoryImpl.kt",
            ":data:provider:anilist",
            ::enforceProviderIsolation,
        )
    }

    @Test
    fun retrofitOutsideNetworkBoundaryIsRejected() {
        assertFixtureRejected(
            "network-boundary",
            "FeatureImportsRetrofit.kt",
            ":feature:today:impl",
            ::enforceNetworkBoundary,
        )
    }

    @Test
    fun okhttpOutsideNetworkBoundaryIsRejected() {
        assertFixtureRejected(
            "network-boundary",
            "CapabilityImportsOkHttp.kt",
            ":data:media",
            ::enforceNetworkBoundary,
        )
    }

    @Test
    fun serializationInProviderApiIsRejected() {
        assertFixtureRejected(
            "network-boundary",
            "ProviderApiImportsSerialization.kt",
            ":data:provider:api",
            ::enforceNetworkBoundary,
        )
    }

    @Test
    fun networkFactoryOutsideBoundaryIsRejected() {
        assertFixtureRejected(
            "network-boundary",
            "FeatureImportsNetworkFactory.kt",
            ":feature:today:impl",
            ::enforceNetworkBoundary,
        )
    }

    @Test
    fun nonSerializableNavKeyIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "NonSerializableNavKey.kt",
            ":feature:today:api",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun aggregateNavKeyPayloadIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "NavKeyWithAggregate.kt",
            ":feature:today:api",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun featureImplementationNavKeyIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "NavKeyInFeatureImpl.kt",
            ":feature:today:impl",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun coreNavigationNavKeyIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "CoreOwnsNavKey.kt",
            ":core:navigation",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun featureApiEntryBuilderIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "EntryBuilderInFeatureApi.kt",
            ":feature:today:api",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun featureOwnedNavDisplayIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "FeatureOwnsNavDisplay.kt",
            ":feature:today:impl",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun fullyQualifiedFeatureNavDisplayIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "FeatureUsesQualifiedNavDisplay.kt",
            ":feature:today:impl",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun featureBackStackMutationIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "FeatureMutatesBackStack.kt",
            ":feature:today:impl",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun fullyQualifiedFeatureBackStackUseIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "FeatureUsesQualifiedNavigationState.kt",
            ":feature:today:impl",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun featureApiJsonSerializationIsRejected() {
        assertFixtureRejected(
            "navigation-boundary",
            "FeatureApiImportsJson.kt",
            ":feature:today:api",
            ::enforceNavigationBoundaries,
        )
    }

    @Test
    fun misplacedProviderIdIsRejected() {
        assertFixtureRejected(
            "provider-contract-ownership",
            "DomainOwnsProviderMediaId.kt",
            ":domain:media",
            ::enforceProviderContractOwnership,
        )
    }

    @Test
    fun providerDtoInCanonicalApiIsRejected() {
        assertFixtureRejected(
            "provider-contract-ownership",
            "ProviderApiOwnsDto.kt",
            ":data:provider:api",
            ::enforceProviderContractOwnership,
        )
    }

    @Test
    fun providerResultEscapeIsRejected() {
        assertFixtureRejected(
            "provider-contract-escape",
            "FeatureImportsProviderResult.kt",
            ":feature:today:impl",
            ::enforceProviderContractsDoNotEscape,
        )
    }

    @Test
    fun providerCredentialsEscapeIsRejected() {
        assertFixtureRejected(
            "provider-contract-escape",
            "RepositoryReturnsProviderCredentials.kt",
            ":data:account",
            ::enforceProviderContractsDoNotEscape,
        )
    }

    @Test
    fun timberOutsideLoggingIsRejected() {
        assertFixtureRejected(
            "logging-boundary",
            "TimberOutsideLogging.kt",
            ":feature:today:impl",
            ::enforceLoggingBoundary,
        )
    }

    @Test
    fun sharedUiFeatureImportIsRejected() {
        assertFixtureRejected(
            "shared-ui-isolation",
            "SharedUiImportsFeature.kt",
            ":core:ui",
            ::enforceSharedUiIsolation,
        )
    }

    @Test
    fun javaTimeImportIsRejected() {
        assertFixtureRejected(
            "forbidden-imports",
            "JavaTimeUsage.kt",
            ":domain:model",
            ::enforceGloballyForbiddenImports,
        )
    }

    @Test
    fun globalScopeImportIsRejected() {
        assertFixtureRejected(
            "forbidden-imports",
            "GlobalScopeUsage.kt",
            ":domain:model",
            ::enforceGloballyForbiddenImports,
        )
    }

    @Test
    fun forbiddenFrameworkImportsAreRejected() {
        assertFixtureRejected(
            "forbidden-imports",
            "ForbiddenFrameworkImports.kt",
            ":core:common",
            ::enforceGloballyForbiddenImports,
        )
    }

    @Test
    fun navigationTwoImportIsRejected() {
        assertFixtureRejected(
            "forbidden-imports",
            "NavigationTwoImport.kt",
            ":feature:today:api",
            ::enforceGloballyForbiddenImports,
        )
    }

    @Test
    fun misplacedDomainModelDeclarationIsRejected() {
        assertFixtureRejected(
            "domain-model-ownership",
            "MisplacedMediaId.kt",
            ":domain:media",
            ::enforceDomainModelOwnership,
        )
    }

    @Test
    fun featureOwnedDomainModelDeclarationIsRejected() {
        assertFixtureRejected(
            "domain-model-ownership",
            "FeatureMediaId.kt",
            ":feature:media:api",
            ::enforceDomainModelOwnership,
        )
    }

    @Test
    fun rawProviderIdentifierIsRejected() {
        assertFixtureRejected(
            "domain-model-ownership",
            "RawProviderId.kt",
            ":domain:media",
            ::enforceDomainModelOwnership,
        )
    }

    @Test
    fun mutableDomainPropertyIsRejected() {
        assertFixtureRejected(
            "domain-immutability",
            "MutableDomainModel.kt",
            ":domain:media",
            ::enforceDomainImmutability,
        )
    }

    @Test
    fun mutableDomainCollectionIsRejected() {
        assertFixtureRejected(
            "domain-immutability",
            "MutableDomainCollection.kt",
            ":domain:collection",
            ::enforceDomainImmutability,
        )
    }

    @Test
    fun dataRepositoryInterfaceIsRejected() {
        assertFixtureRejected(
            "repository-boundary",
            "DataRepositoryInterface.kt",
            ":data:account",
            ::enforceRepositoryBoundaries,
        )
    }

    @Test
    fun domainRepositoryClassIsRejected() {
        assertFixtureRejected(
            "repository-boundary",
            "DomainRepositoryClass.kt",
            ":domain:account",
            ::enforceRepositoryBoundaries,
        )
    }

    @Test
    fun fakeRepositoryOutsideCoreTestingIsRejected() {
        assertFixtureRejected(
            "repository-boundary",
            "FeatureFakeRepository.kt",
            ":feature:today:impl",
            ::enforceRepositoryBoundaries,
        )
    }

    @Test
    fun wrappedRepositoryResultIsRejected() {
        assertFixtureRejected(
            "repository-boundary",
            "WrappedRepositoryResult.kt",
            ":domain:account",
            ::enforceRepositoryBoundaries,
        )
    }

    @Test
    fun dataOwnedEntityIsRejected() {
        assertFixtureRejected(
            "persistence-ownership",
            "DataOwnedEntity.kt",
            ":data:media",
            ::enforcePersistenceTypeOwnership,
        )
    }

    @Test
    fun featureOwnedDaoIsRejected() {
        assertFixtureRejected(
            "persistence-ownership",
            "FeatureOwnedDao.kt",
            ":feature:today:impl",
            ::enforcePersistenceTypeOwnership,
        )
    }

    @Test
    fun featureRoomImportIsRejected() {
        assertFixtureRejected(
            "persistence-escape",
            "FeatureImportsRoom.kt",
            ":feature:today:impl",
            ::enforcePersistenceDoesNotEscape,
        )
    }

    @Test
    fun domainEntityReturnIsRejected() {
        assertFixtureRejected(
            "persistence-escape",
            "DomainReturnsEntity.kt",
            ":domain:media",
            ::enforcePersistenceDoesNotEscape,
        )
    }

    @Test
    fun repositoryDaoReturnIsRejected() {
        assertFixtureRejected(
            "persistence-escape",
            "RepositoryReturnsDao.kt",
            ":data:account",
            ::enforcePersistenceDoesNotEscape,
        )
    }

    @Test
    fun featureDataStoreImportIsRejected() {
        assertFixtureRejected(
            "persistence-escape",
            "FeatureImportsDataStore.kt",
            ":feature:today:impl",
            ::enforcePersistenceDoesNotEscape,
        )
    }

    @Test
    fun mixedMechanismDataSourceIsRejected() {
        assertFixtureRejected(
            "data-source-boundary",
            "MixedMechanismLocalDataSource.kt",
            ":data:preferences",
            ::enforceOneMechanismPerDataSource,
        )
    }

    @Test
    fun repositoryBackedDataSourceIsRejected() {
        assertFixtureRejected(
            "data-source-boundary",
            "RepositoryBackedLocalDataSource.kt",
            ":data:collection",
            ::enforceOneMechanismPerDataSource,
        )
    }

    @Test
    fun graphOutsideAppIsRejected() {
        assertFixtureRejected(
            "metro-boundary",
            "DataOwnsGraph.kt",
            ":data:media",
            ::enforceMetroBoundaries,
        )
    }

    @Test
    fun featureGraphCreationIsRejected() {
        assertFixtureRejected(
            "metro-boundary",
            "FeatureCreatesGraph.kt",
            ":feature:today:impl",
            ::enforceMetroBoundaries,
        )
    }

    @Test
    fun fieldInjectionIsRejected() {
        assertFixtureRejected(
            "metro-boundary",
            "FieldInjectedRepository.kt",
            ":data:account",
            ::enforceMetroBoundaries,
        )
    }

    @Test
    fun staticGraphHolderIsRejected() {
        assertFixtureRejected(
            "metro-boundary",
            "StaticGraphHolder.kt",
            ":app",
            ::enforceMetroBoundaries,
        )
    }

    private fun assertFixtureRejected(
        directoryName: String,
        filename: String,
        ownerModule: String,
        rule: (List<OwnedKotlinFile>) -> Unit,
    ) {
        val files = loadFixtureDirectory(directoryName, ownerModule, filename = filename)
        val failure = assertThrows<AssertionError> { rule(files) }
        val message = failure.message.orEmpty()

        assertTrue(message.contains(filename), "failure should identify $filename: $message")
        assertTrue(message.contains(ruleLabel(rule)), "failure should identify its rule: $message")
    }

    private fun loadFixtureDirectory(
        directoryName: String,
        ownerModule: String,
        filename: String? = null,
        ownerByFilename: Map<String, String> = emptyMap(),
    ): List<OwnedKotlinFile> {
        val fixtureDirectory =
            repositoryRoot
                .resolve("architecture-tests/src/test/fixtures")
                .resolve(directoryName)
                .normalize()

        assertTrue(fixtureDirectory.isDirectory(), "fixture directory does not exist: $fixtureDirectory")

        val files =
            Konsist
                .scopeFromExternalDirectory(fixtureDirectory.toString())
                .files
                .filter { file ->
                    val normalizedPath = file.path.replace('\\', '/')
                    normalizedPath.startsWith("${fixtureDirectory.toString().replace('\\', '/')}/") &&
                        (filename == null || normalizedPath.substringAfterLast('/') == filename)
                }.map { file ->
                    val filename = file.path.replace('\\', '/').substringAfterLast('/')
                    OwnedKotlinFile(ownerByFilename[filename] ?: ownerModule, file)
                }

        assertFalse(files.isEmpty(), "fixture directory is empty: $fixtureDirectory")
        return files
    }

    private fun ruleLabel(rule: (List<OwnedKotlinFile>) -> Unit): String =
        when {
            rule == ::enforcePackageOwnership -> "Package ownership"
            rule == ::enforceDomainPurity -> "Domain purity"
            rule == ::enforcePresentationIsolation -> "Presentation isolation"
            rule == ::enforceFeatureApiIsolation -> "Feature API isolation"
            rule == ::enforceProviderIsolation -> "Provider isolation"
            rule == ::enforceNetworkBoundary -> "Network boundary"
            rule == ::enforceNavigationBoundaries -> "Navigation boundaries"
            rule == ::enforceProviderContractOwnership -> "Provider contract ownership"
            rule == ::enforceProviderContractsDoNotEscape -> "Provider contract escape"
            rule == ::enforceLoggingBoundary -> "Logging boundary"
            rule == ::enforceSharedUiIsolation -> "Shared UI isolation"
            rule == ::enforceGloballyForbiddenImports -> "Globally forbidden imports"
            rule == ::enforceDomainModelOwnership -> "Domain model ownership"
            rule == ::enforceDomainImmutability -> "Domain immutability"
            rule == ::enforceRepositoryBoundaries -> "Repository boundaries"
            rule == ::enforcePersistenceTypeOwnership -> "Persistence type ownership"
            rule == ::enforcePersistenceDoesNotEscape -> "Persistence escape"
            rule == ::enforceOneMechanismPerDataSource -> "One mechanism per data source"
            rule == ::enforceMetroBoundaries -> "Metro boundaries"
            else -> fail("unknown architecture rule")
        }
}
