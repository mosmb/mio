package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.isDirectory

class ProductionArchitectureKonsistTest {
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
    fun packageOwnership() {
        enforcePackageOwnership(productionFiles())
    }

    @Test
    fun domainPurity() {
        enforceDomainPurity(productionFiles())
    }

    @Test
    fun presentationIsolation() {
        enforcePresentationIsolation(productionFiles())
    }

    @Test
    fun featureApiIsolation() {
        enforceFeatureApiIsolation(productionFiles())
    }

    @Test
    fun providerIsolation() {
        enforceProviderIsolation(productionFiles())
    }

    @Test
    fun loggingBoundary() {
        enforceLoggingBoundary(productionFiles())
    }

    @Test
    fun sharedUiIsolation() {
        enforceSharedUiIsolation(productionFiles())
    }

    @Test
    fun globallyForbiddenImports() {
        enforceGloballyForbiddenImports(productionFiles())
    }

    @Test
    fun domainModelOwnership() {
        enforceDomainModelOwnership(productionFiles())
    }

    @Test
    fun domainImmutability() {
        enforceDomainImmutability(productionFiles())
    }

    @Test
    fun repositoryBoundaries() {
        enforceRepositoryBoundaries(productionFiles())
    }

    @Test
    fun taskThreeProductionModulesAreActuallyScanned() {
        val scannedModules = productionFiles().mapTo(mutableSetOf(), OwnedKotlinFile::modulePath)
        val expectedModules =
            setOf(
                ":domain:model",
                ":domain:account",
                ":domain:media",
                ":domain:collection",
                ":domain:preferences",
                ":core:testing",
            )

        assertTrue(
            scannedModules.containsAll(expectedModules),
            "production scan omitted task-three modules: ${expectedModules - scannedModules}",
        )
    }

    private fun productionFiles(): List<OwnedKotlinFile> =
        MioProductionModules.paths.flatMap { modulePath ->
            val sourceDirectory =
                repositoryRoot
                    .resolve(modulePath.removePrefix(":").replace(':', '/'))
                    .resolve("src/main/kotlin")
                    .normalize()

            if (!sourceDirectory.isDirectory()) {
                emptyList()
            } else {
                Konsist
                    .scopeFromExternalDirectory(sourceDirectory.toString())
                    .files
                    .filter { file ->
                        val normalizedPath = file.path.replace('\\', '/')
                        normalizedPath.startsWith("${sourceDirectory.toString().replace('\\', '/')}/") &&
                            !normalizedPath.contains("/build/") &&
                            !normalizedPath.contains("/generated/")
                    }.map { file -> OwnedKotlinFile(modulePath, file) }
            }
        }
}
