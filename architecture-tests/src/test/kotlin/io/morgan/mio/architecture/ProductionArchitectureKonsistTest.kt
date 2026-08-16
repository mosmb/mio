package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun navigationBoundaries() {
        enforceNavigationBoundaries(productionFiles())
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
    fun platformServiceBoundaries() {
        enforcePlatformServiceBoundaries(productionFiles())
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
    fun sharedUiOwnership() {
        enforceSharedUiOwnership(productionFiles())
    }

    @Test
    fun routeScreenConvention() {
        enforceRouteScreenConvention(productionFiles())
    }

    @Test
    fun architecturalFileCohesion() {
        enforceArchitecturalFileCohesion(productionFiles())
    }

    @Test
    fun viewModelPresentationConvention() {
        enforceViewModelPresentationConvention(productionFiles())
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
    fun pendingOperationBoundaries() {
        enforcePendingOperationBoundaries(productionFiles())
    }

    @Test
    fun metroBoundaries() {
        enforceMetroBoundaries(productionFiles())
    }

    @Test
    fun taskFourProductionPersistenceTypesAreActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classes(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.hasAnnotationWithName("Entity") }
            },
            "production scan omitted every @Entity class",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classes(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.hasAnnotationWithName("Dao") }
            },
            "production scan omitted every @Dao class",
        )
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
    fun architecturalFileCohesionProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.name.endsWith("Repository") }
            },
            "production scan omitted every Repository role",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.name.endsWith("ViewModel") }
            },
            "production scan omitted every ViewModel role",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.name.endsWith("UiState") }
            },
            "production scan omitted every UiState role",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classes(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.hasPublicOrDefaultModifier && declaration.hasEnumModifier }
            },
            "production scan omitted every public enum class",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.file
                    .classes(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.hasPublicOrDefaultModifier && declaration.hasValueModifier }
            },
            "production scan omitted every public value class",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.sourceSet == "debug" &&
                    ownedFile.modulePath == ":core:logging" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "TimberMioLogger" }
            },
            "production scan omitted the debug source set",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.sourceSet == "release" &&
                    ownedFile.modulePath == ":core:logging" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "ReleaseMioLogger" }
            },
            "production scan omitted the release source set",
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

    @Test
    fun taskSevenProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val expectedRootKeys =
            mapOf(
                ":feature:onboarding:api" to "OnboardingKey",
                ":feature:today:api" to "TodayKey",
                ":feature:library:api" to "LibraryKey",
                ":feature:discover:api" to "DiscoverKey",
                ":feature:you:api" to "YouKey",
            )

        expectedRootKeys.forEach { (modulePath, keyName) ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == modulePath &&
                        ownedFile.file.text.contains("data object $keyName")
                },
                "production scan omitted $keyName from $modulePath",
            )
        }

        val builderFiles =
            files.count { ownedFile ->
                ownedFile.modulePath.startsWith(":feature:") &&
                    ownedFile.modulePath.endsWith(":impl") &&
                    ownedFile.file.text.contains(": NavEntryBuilder")
            }
        assertTrue(builderFiles >= 5, "expected at least five feature NavEntryBuilder implementations")

        val builderSetProviders =
            files.count { ownedFile ->
                ownedFile.modulePath.startsWith(":feature:") &&
                    ownedFile.modulePath.endsWith(":impl") &&
                    ownedFile.file.text.contains("@IntoSet") &&
                    ownedFile.file.text.contains("NavEntryBuilder")
            }
        assertTrue(builderSetProviders >= 5, "expected at least five Metro NavEntryBuilder set providers")

        val navDisplayOwners =
            files
                .filter { ownedFile ->
                    ownedFile.file.imports.any { import ->
                        import.name == "androidx.navigation3.ui.NavDisplay"
                    }
                }.mapTo(mutableSetOf(), OwnedKotlinFile::modulePath)
        assertEquals(setOf(":app"), navDisplayOwners)

        val appGraphNavigationRoots =
            files.count { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.text.contains("interface AppGraph") &&
                    ownedFile.file.text.contains("val navigationRegistry: NavigationRegistry")
            }
        assertEquals(1, appGraphNavigationRoots)

        val focusedMarkers =
            files.count { ownedFile ->
                ownedFile.modulePath == ":core:navigation" &&
                    ownedFile.file.text.contains("interface FocusedDestination")
            }
        assertEquals(1, focusedMarkers)
    }

    @Test
    fun taskNineProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val expectedScreens =
            mapOf(
                ":feature:onboarding:impl" to "Onboarding",
                ":feature:today:impl" to "Today",
                ":feature:library:impl" to "Library",
                ":feature:discover:impl" to "Discover",
                ":feature:you:impl" to "You",
            )

        expectedScreens.forEach { (modulePath, prefix) ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == modulePath &&
                        ownedFile.file.path
                            .replace('\\', '/')
                            .endsWith("/${prefix}Screen.kt") &&
                        ownedFile.file.text.contains("fun ${prefix}Route") &&
                        ownedFile.file.text.contains("fun ${prefix}Screen")
                },
                "production scan omitted ${prefix}Route/${prefix}Screen from $modulePath",
            )
        }

        listOf(
            "MioBottomNavigation",
            "MioButton",
            "MioBottomSheet",
            "MioSnackbar",
            "MioSnackbarHost",
        ).forEach { declarationName ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == ":core:designsystem" &&
                        ownedFile.file.packagee?.name == "io.morgan.mio.core.designsystem.component" &&
                        ownedFile.file.text.contains(declarationName)
                },
                "production scan omitted $declarationName from :core:designsystem",
            )
        }

        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:ui" &&
                    ownedFile.file.text.contains("MioArtwork") &&
                    ownedFile.file.text.contains("MioPoster")
            },
            "production scan omitted Task 10 artwork boundary",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:ui" &&
                    ownedFile.file.imports.any { import -> import.name == "coil3.ImageLoader" }
            },
            "production scan omitted explicit core-ui ImageLoader use",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/RootNavigationBar.kt") &&
                    ownedFile.file.imports.any { import ->
                        import.name == "io.morgan.mio.core.designsystem.component.MioBottomNavigation"
                    }
            },
            "RootNavigationBar must adapt MioBottomNavigation",
        )
        assertTrue(
            files
                .filter { ownedFile ->
                    ownedFile.modulePath == ":core:designsystem" &&
                        ownedFile.file.packagee?.name == "io.morgan.mio.core.designsystem.component"
                }.none { ownedFile ->
                    ownedFile.file.imports.any { import ->
                        import.name == "io.morgan.mio.core.navigation.MioNavigator" ||
                            import.name.startsWith("io.morgan.mio.core.navigation.") ||
                            import.name.startsWith("androidx.navigation")
                    }
                },
            "design-system components must not import navigation types",
        )
    }

    @Test
    fun taskTwelveOnboardingPresentationIsActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:onboarding:impl" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "OnboardingViewModel" }
            },
            "production scan omitted OnboardingViewModel",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:onboarding:impl" &&
                    ownedFile.file.text.contains("fun OnboardingRoute") &&
                    ownedFile.file.text.contains("fun OnboardingScreen") &&
                    ownedFile.file.imports.any { import -> import.name.endsWith("metroViewModel") }
            },
            "production scan omitted the Metro-backed onboarding Route/Screen",
        )
    }

    @Test
    fun taskElevenProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":domain:sync" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "PendingOperation" }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:sync" &&
                    ownedFile.file.interfaces(true).any { declaration ->
                        declaration.name == "PendingOperationWriter"
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:sync" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "DefaultPendingOperationWriter" &&
                            declaration.hasAnnotationWithName("ContributesBinding")
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:sync" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "RoomPendingOperationLocalDataSource"
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "PendingOperationEntity"
                    }
            },
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "PendingOperationDao" }
            },
        )
    }

    private fun productionFiles(): List<OwnedKotlinFile> =
        MioProductionModules.paths.flatMap { modulePath ->
            listOf("main", "debug", "release").flatMap { sourceSet ->
                val sourceDirectory =
                    repositoryRoot
                        .resolve(modulePath.removePrefix(":").replace(':', '/'))
                        .resolve("src/$sourceSet/kotlin")
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
                        }.map { file -> OwnedKotlinFile(modulePath, file, sourceSet) }
                }
            }
        }
}
