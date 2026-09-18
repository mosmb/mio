package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import kotlin.io.path.isDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    fun synchronizationBoundaries() {
        enforceSynchronizationBoundaries(productionFiles())
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
                        ownedFile.file.classesAndInterfacesAndObjects().any { it.name == keyName }
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

    @Test
    fun taskThirteenProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val aniListFiles = files.filter { ownedFile -> ownedFile.modulePath == ":data:provider:anilist" }

        listOf(
            "AniListAuthenticator",
            "AniListIdentityDataSource",
            "AniListInitialImportDataSource",
            "AniListProviderFactory",
        ).forEach { typeName ->
            assertTrue(
                aniListFiles.any { ownedFile ->
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == typeName }
                },
                "production scan omitted $typeName",
            )
        }
        assertTrue(
            aniListFiles.any { ownedFile ->
                ownedFile.file.interfaces(true).any { declaration -> declaration.name == "AniListGraphQlService" }
            },
            "production scan omitted AniListGraphQlService",
        )
        assertTrue(
            aniListFiles.any { ownedFile ->
                ownedFile.file.classes(true, true).any { declaration -> declaration.name.endsWith("Dto") }
            },
            "production scan omitted every AniList DTO",
        )
        assertTrue(
            aniListFiles.none { ownedFile ->
                ownedFile.file.imports.any { import -> import.name.startsWith("android.net") }
            },
            "an AniList production file imports android.net",
        )

        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:account" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "RoomInitialImportLocalDataSource"
                    }
            },
            "production scan omitted RoomInitialImportLocalDataSource",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:account" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "InitialImportWriter" }
            },
            "production scan omitted InitialImportWriter",
        )

        listOf(
            ":data:provider:api" to "ProviderCollectionStatus.kt",
            ":domain:collection" to "CollectionStatus.kt",
            ":core:database" to "StoredCollectionStatus.kt",
        ).forEach { (modulePath, filename) ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == modulePath &&
                        ownedFile.file.path
                            .replace('\\', '/')
                            .substringAfterLast('/') == filename &&
                        ownedFile.file.text.contains("REPEATING")
                },
                "$filename in $modulePath does not contain REPEATING",
            )
        }
    }

    @Test
    fun taskFourteenProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        val myAnimeListFiles = files.filter { ownedFile -> ownedFile.modulePath == ":data:provider:myanimelist" }

        listOf(
            "MyAnimeListAuthenticator",
            "MyAnimeListIdentityDataSource",
            "MyAnimeListInitialImportDataSource",
            "MyAnimeListTokenExchange",
            "MyAnimeListProviderFactory",
        ).forEach { typeName ->
            assertTrue(
                myAnimeListFiles.any { ownedFile ->
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == typeName }
                },
                "production scan omitted $typeName",
            )
        }
        assertTrue(
            myAnimeListFiles.any { ownedFile ->
                ownedFile.file.interfaces(true).any { declaration -> declaration.name == "MyAnimeListApiService" }
            },
            "production scan omitted MyAnimeListApiService",
        )
        assertTrue(
            myAnimeListFiles.any { ownedFile ->
                ownedFile.file.interfaces(true).any { declaration -> declaration.name == "MyAnimeListOAuthService" }
            },
            "production scan omitted MyAnimeListOAuthService",
        )
        assertTrue(
            myAnimeListFiles.any { ownedFile ->
                ownedFile.file.classes(true, true).any { declaration -> declaration.name.endsWith("Dto") }
            },
            "production scan omitted every MyAnimeList DTO",
        )
        assertTrue(
            myAnimeListFiles.none { ownedFile ->
                ownedFile.file.imports.any { import -> import.name.startsWith("android.net") }
            },
            "a MyAnimeList production file imports android.net",
        )
        assertTrue(
            myAnimeListFiles.none { ownedFile ->
                ownedFile.file.imports.any { import -> import.name.startsWith("io.morgan.mio.data.provider.anilist") }
            },
            "a MyAnimeList production file imports AniList package",
        )
    }

    @Test
    fun taskFifteenProductionTypesAreActuallyScanned() {
        val files = productionFiles()

        listOf(
            ":data:provider:api" to "ProviderBindingResolver",
            ":data:account" to "AuthenticatedAccountSessionActivator",
            ":data:account" to "ProductionAccountSessionRepository",
            ":app" to "ProductionProviderBindingResolver",
            ":app" to "MetroAuthenticatedAccountSessionActivator",
        ).forEach { (modulePath, typeName) ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == modulePath &&
                        ownedFile.file
                            .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                            .any { declaration -> declaration.name == typeName }
                },
                "production scan omitted $typeName from $modulePath",
            )
        }
        assertTrue(
            files.none { ownedFile ->
                ownedFile.file
                    .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                    .any { declaration -> declaration.name == "UnavailableAccountSessionRepository" }
            },
            "the unavailable account-session placeholder remains in production",
        )
    }

    @Test
    fun taskSixteenProductionTypesAreActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:media:api" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/MediaKey.kt") &&
                    ownedFile.file.text.contains("FocusedDestination") &&
                    ownedFile.file.text.contains("private object MediaIdSerializer")
            },
            "production scan omitted the typed focused MediaKey serializer",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:media:impl" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "MediaDetailViewModel" }
            },
            "production scan omitted the AccountScope-assisted media detail ViewModel",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:media:impl" &&
                    ownedFile.file.text.contains(
                        "assistedMetroViewModel<MediaDetailViewModel, MediaDetailViewModel.Factory>",
                    )
            },
            "production scan omitted the assisted media detail ViewModel route",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.interfaces(true).any { declaration -> declaration.name == "AccountGraph" } &&
                    ownedFile.file.text.contains("ViewModelGraph")
            },
            "production scan omitted the AccountGraph ViewModelGraph extension",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name ==
                            "AccountMetroViewModelFactory"
                    } &&
                    ownedFile.file.text.contains("AccountScope")
            },
            "production scan omitted the AccountScope Metro ViewModel factory",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/MainActivity.kt") &&
                    ownedFile.file.text.contains("withActiveGraph") &&
                    ownedFile.file.text.contains("?: graph.metroViewModelFactory")
            },
            "production scan omitted MainActivity's active-account factory fallback",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:provider:anilist" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name ==
                            "AniListMediaDetailDataSource"
                    }
            },
            "production scan omitted AniList detail enrichment",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:provider:myanimelist" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name ==
                            "MyAnimeListMediaDetailDataSource"
                    }
            },
            "production scan omitted MyAnimeList detail enrichment",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:media" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name ==
                            "ProviderMediaRefreshRequester"
                    } &&
                    ownedFile.file.text.contains("refreshMedia")
            },
            "production scan omitted the Room-backed detail refresh requester",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" &&
                    ownedFile.file.text.contains("version = 7")
            },
            "production scan omitted the current database version",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" &&
                    ownedFile.file.text.contains("MIGRATION_2_3")
            },
            "production scan omitted the 2-to-3 media migration",
        )
    }

    @Test
    fun taskSeventeenTodayQueueAndAccountPresentationAreActuallyScanned() {
        val files = productionFiles()
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":domain:queue" &&
                    ownedFile.file.classes(true, true).any { declaration ->
                        declaration.name == "ObserveTodayQueueUseCase"
                    } &&
                    ownedFile.file.text.contains("flatMapLatest") &&
                    ownedFile.file.imports.any { import ->
                        import.name == "io.morgan.mio.domain.collection.CollectionRepository"
                    } &&
                    ownedFile.file.text.contains("io.morgan.mio.domain.media.MediaRepository")
            },
            "production scan omitted the Room-flow-backed Today queue use case",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:today:impl" &&
                    ownedFile.file.classes(true, true).any { declaration -> declaration.name == "TodayViewModel" } &&
                    ownedFile.file.text.contains("@ContributesIntoMap(AccountScope::class)") &&
                    ownedFile.file.text.contains("@ViewModelKey")
            },
            "production scan omitted the AccountScope Today ViewModel contribution",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:today:impl" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/TodayScreen.kt") &&
                    ownedFile.file.text.contains("metroViewModel<TodayViewModel>") &&
                    ownedFile.file.text.contains("MediaId(mediaId)")
            },
            "production scan omitted the Today Route-to-typed-media navigation seam",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:today:impl" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/TodayNowCard.kt") &&
                    ownedFile.file.text.contains("requiredHeightIn(min = 332.dp)") &&
                    ownedFile.file.text.contains("mergeDescendants = true")
            },
            "production scan omitted the bounded, merged-semantic Now card",
        )
    }

    @Test
    fun taskNineteenProgressTypesAreActuallyScanned() {
        val files = productionFiles()
        val requiredProductionFiles =
            listOf(
                ":domain:progress" to "ProgressRules.kt",
                ":domain:progress" to "StepProgressUseCase.kt",
                ":domain:progress" to "ProgressRepository.kt",
                ":data:progress" to "DefaultProgressRepository.kt",
                ":data:progress" to "RoomProgressLocalDataSource.kt",
                ":core:database" to "ProgressDao.kt",
                ":core:database" to "LocalProgressActivityEntity.kt",
                ":core:ui" to "MioProgressStepper.kt",
                ":core:ui" to "MioProgressValue.kt",
                ":feature:today:impl" to "TodayViewModel.kt",
                ":feature:today:impl" to "TodayAction.kt",
                ":feature:media:impl" to "MediaDetailViewModel.kt",
                ":feature:media:impl" to "MediaDetailAction.kt",
                ":app" to "ProgressBindings.kt",
            )

        requiredProductionFiles.forEach { (modulePath, filename) ->
            assertTrue(
                files.any { ownedFile ->
                    ownedFile.modulePath == modulePath &&
                        ownedFile.sourceSet == "main" &&
                        ownedFile.file.path
                            .replace('\\', '/')
                            .endsWith("/$filename")
                },
                "production scan omitted $filename from $modulePath",
            )
        }
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":domain:progress" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/ProgressRules.kt") &&
                    ownedFile.file.text.contains("ProgressDirection.INCREASE") &&
                    ownedFile.file.text.contains("ProgressDirection.DECREASE")
            },
            "production scan omitted both Task 19 pure-rule directions",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":data:progress" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/DefaultProgressRepository.kt") &&
                    ownedFile.file.text.contains("database.inTransaction") &&
                    ownedFile.file.text.contains("pendingOperationWriter.undo")
            },
            "production scan omitted the Task 19 atomic repository boundary",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" &&
                    ownedFile.file.text.contains("MIGRATION_3_4") &&
                    ownedFile.file.text.contains("local_progress_activity")
            },
            "production scan omitted the Task 19 Room migration/activity schema",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:database" && ownedFile.file.text.contains("version = 7")
            },
            "production scan omitted the Task 20 database version",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:ui" && ownedFile.file.text.contains("MioProgressStepper")
            },
            "production scan omitted the shared Task 19 progress stepper",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":core:ui" && ownedFile.file.text.contains("MioProgressValue")
            },
            "production scan omitted the shared Task 19 progress value",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:today:impl" &&
                    ownedFile.file.text.contains("MioProgressStepper")
            },
            "production scan omitted Today progress stepper wiring",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:today:impl" && ownedFile.file.text.contains("TodayAction.Step")
            },
            "production scan omitted Today progress actions",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:media:impl" &&
                    ownedFile.file.text.contains("MioProgressStepper")
            },
            "production scan omitted media-detail progress stepper wiring",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":feature:media:impl" &&
                    ownedFile.file.text.contains("MediaDetailAction.Step")
            },
            "production scan omitted media-detail progress actions",
        )
        assertTrue(
            files.any { ownedFile ->
                ownedFile.modulePath == ":app" &&
                    ownedFile.file.path
                        .replace('\\', '/')
                        .endsWith("/ProgressBindings.kt") &&
                    ownedFile.file.text.contains("AccountScope") &&
                    ownedFile.file.text.contains("DefaultProgressRepository")
            },
            "production scan omitted Account-scoped progress composition",
        )
        assertTrue(
            files.any { ownedFile -> ownedFile.sourceSet == "debug" } &&
                files.any { ownedFile -> ownedFile.sourceSet == "release" },
            "production scan omitted debug or release source sets while checking Task 19",
        )
    }

    private fun productionFiles(): List<OwnedKotlinFile> = scannedProductionFiles

    private val scannedProductionFiles: List<OwnedKotlinFile> by lazy {
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
}
