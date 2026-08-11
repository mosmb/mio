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
                    ),
            )

        enforcePackageOwnership(files)
        enforceDomainPurity(files)
        enforcePresentationIsolation(files)
        enforceFeatureApiIsolation(files)
        enforceProviderIsolation(files)
        enforceLoggingBoundary(files)
        enforceSharedUiIsolation(files)
        enforceGloballyForbiddenImports(files)
        enforceDomainModelOwnership(files)
        enforceDomainImmutability(files)
        enforceRepositoryBoundaries(files)
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
            rule == ::enforceLoggingBoundary -> "Logging boundary"
            rule == ::enforceSharedUiIsolation -> "Shared UI isolation"
            rule == ::enforceGloballyForbiddenImports -> "Globally forbidden imports"
            rule == ::enforceDomainModelOwnership -> "Domain model ownership"
            rule == ::enforceDomainImmutability -> "Domain immutability"
            rule == ::enforceRepositoryBoundaries -> "Repository boundaries"
            else -> fail("unknown architecture rule")
        }
}
