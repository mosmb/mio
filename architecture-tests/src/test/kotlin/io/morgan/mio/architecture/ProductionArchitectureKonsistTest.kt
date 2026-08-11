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
    fun networkBoundary() {
        enforceNetworkBoundary(productionFiles())
    }

    @Test
    fun providerContractOwnership() {
        enforceProviderContractOwnership(productionFiles())
    }

    @Test
    fun providerContractsDoNotEscape() {
        enforceProviderContractsDoNotEscape(productionFiles())
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
    fun persistenceTypeOwnership() {
        enforcePersistenceTypeOwnership(productionFiles())
    }

    @Test
    fun persistenceDoesNotEscape() {
        enforcePersistenceDoesNotEscape(productionFiles())
    }

    @Test
    fun oneMechanismPerDataSource() {
        enforceOneMechanismPerDataSource(productionFiles())
    }

    @Test
    fun metroBoundaries() {
        enforceMetroBoundaries(productionFiles())
    }

    @Test
    fun taskFourProductionPersistenceTypesAreActuallyScanned() {
        val files = productionFiles()
        val source = files.map { ownedFile -> ownedFile.file.text }
        assertTrue(source.any { text -> text.contains("@Entity") })
        assertTrue(source.any { text -> text.contains("@Dao") })
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath.startsWith(":data:") &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name.endsWith("Repository") }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath.startsWith(":data:") &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name.endsWith("LocalDataSource")
                    } &&
                    ownedFile.file.imports.any { import ->
                        import.name == "io.morgan.mio.core.database.MioDatabase" ||
                            import.name.startsWith("androidx.datastore")
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:preferences" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "DataStorePreferencesLocalDataSource"
                    }
            },
        )
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

    @Test
    fun taskFiveProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val source = files.map { ownedFile -> ownedFile.file.text }
        assertTrue(source.any { text -> text.contains("NetworkClientFactory") })
        assertTrue(source.any { text -> text.contains("AndroidNetworkMonitor") })
        assertTrue(source.any { text -> text.contains("ProviderAuthenticator") })
        assertTrue(source.any { text -> text.contains("ProviderInitialImportDataSource") })
        assertTrue(source.any { text -> text.contains("CompleteProviderImport") })
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:testing" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name.startsWith("FakeProvider")
                    }
            },
        )
    }

    @Test
    fun taskSixProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val source = files.map { ownedFile -> ownedFile.file.text }
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.interfaces(true).any { declaration -> declaration.name == "AppGraph" }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.interfaces(true).any { declaration -> declaration.name == "AccountGraph" }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath.startsWith(":data:") &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name.endsWith("Repository") &&
                            declaration.hasAnnotationWithName("ContributesBinding")
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file.classes(true, true).any { declaration ->
                    declaration.hasAnnotationWithName("Inject") && declaration.hasPrimaryConstructor
                }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.text.contains("class MioApplication") &&
                    ownedFile.file.text.contains("createGraphFactory<AppGraph.Factory>()")
            },
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
