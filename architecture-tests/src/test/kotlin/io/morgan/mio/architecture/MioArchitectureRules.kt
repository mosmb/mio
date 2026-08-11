package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider

data class OwnedKotlinFile(
    val modulePath: String,
    val file: KoFileDeclaration,
)

internal object MioProductionModules {
    val paths =
        listOf(
            ":app",
            ":feature:onboarding:api",
            ":feature:onboarding:impl",
            ":feature:today:api",
            ":feature:today:impl",
            ":feature:library:api",
            ":feature:library:impl",
            ":feature:discover:api",
            ":feature:discover:impl",
            ":feature:you:api",
            ":feature:you:impl",
            ":feature:media:api",
            ":feature:media:impl",
            ":feature:progress:api",
            ":feature:progress:impl",
            ":domain:model",
            ":domain:account",
            ":domain:media",
            ":domain:collection",
            ":domain:progress",
            ":domain:queue",
            ":domain:sync",
            ":domain:preferences",
            ":data:account",
            ":data:media",
            ":data:collection",
            ":data:progress",
            ":data:sync",
            ":data:preferences",
            ":data:provider:api",
            ":data:provider:anilist",
            ":data:provider:myanimelist",
            ":core:common",
            ":core:testing",
            ":core:database",
            ":core:datastore",
            ":core:network",
            ":core:logging",
            ":core:ui",
            ":core:designsystem",
            ":core:navigation",
            ":sync:work",
            ":architecture-tests",
        )
}

internal fun enforcePackageOwnership(files: List<OwnedKotlinFile>) {
    assertNoViolations("Package ownership", files) { ownedFile ->
        val packageName =
            ownedFile.file.packagee
                ?.name
                .orEmpty()
        val expectedNamespace = moduleNamespace(ownedFile.modulePath)
        if (packageName.isNotEmpty() &&
            (packageName == expectedNamespace || packageName.startsWith("$expectedNamespace."))
        ) {
            null
        } else {
            "package '$packageName' must be '$expectedNamespace' or one of its subpackages"
        }
    }
}

internal fun enforceDomainPurity(files: List<OwnedKotlinFile>) {
    val forbiddenPrefixes =
        listOf(
            "android",
            "androidx",
            "io.morgan.mio.app",
            "io.morgan.mio.feature",
            "io.morgan.mio.data",
            "io.morgan.mio.core.database",
            "io.morgan.mio.core.datastore",
            "io.morgan.mio.core.network",
            "io.morgan.mio.core.logging",
            "io.morgan.mio.sync",
            "retrofit2",
            "okhttp3",
            "timber.log",
        )

    assertNoViolations("Domain purity", files.filter { it.modulePath.startsWith(":domain:") }) { ownedFile ->
        firstMatchingImport(ownedFile.file, forbiddenPrefixes)?.let { importName ->
            "domain code cannot import '$importName'"
        }
    }
}

internal fun enforcePresentationIsolation(files: List<OwnedKotlinFile>) {
    val forbiddenPrefixes =
        listOf(
            "io.morgan.mio.data",
            "io.morgan.mio.core.database",
            "io.morgan.mio.core.datastore",
            "io.morgan.mio.core.network",
            "androidx.room",
            "androidx.datastore",
            "retrofit2",
            "okhttp3",
        )

    assertNoViolations(
        "Presentation isolation",
        files.filter { isFeatureImplementation(it.modulePath) },
    ) { ownedFile ->
        val forbiddenImport = firstMatchingImport(ownedFile.file, forbiddenPrefixes)
        val otherFeatureImplementation =
            ownedFile.file.imports
                .map { it.name }
                .firstOrNull { importName -> isOtherFeatureImplementationImport(ownedFile.modulePath, importName) }

        when {
            forbiddenImport != null -> "feature implementation cannot import '$forbiddenImport'"
            otherFeatureImplementation != null ->
                "feature implementation cannot import another feature implementation '$otherFeatureImplementation'"

            else -> null
        }
    }
}

internal fun enforceFeatureApiIsolation(files: List<OwnedKotlinFile>) {
    val forbiddenPrefixes =
        listOf(
            "io.morgan.mio.data",
            "androidx.compose",
            "androidx.lifecycle.ViewModel",
            "androidx.lifecycle.viewmodel",
            "androidx.room",
            "androidx.datastore",
            "retrofit2",
            "okhttp3",
        )

    assertNoViolations("Feature API isolation", files.filter { isFeatureApi(it.modulePath) }) { ownedFile ->
        val imports = ownedFile.file.imports.map { it.name }
        val forbiddenImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, forbiddenPrefixes) ||
                    isFeatureImplementationImport(importName) ||
                    (
                        matchesPrefix(importName, "io.morgan.mio.domain") &&
                            !matchesPrefix(importName, "io.morgan.mio.domain.model")
                    )
            }

        forbiddenImport?.let { importName -> "feature API cannot import '$importName'" }
    }
}

internal fun enforceProviderIsolation(files: List<OwnedKotlinFile>) {
    val capabilityPrefixes =
        listOf(
            "io.morgan.mio.data.account",
            "io.morgan.mio.data.media",
            "io.morgan.mio.data.collection",
            "io.morgan.mio.data.progress",
            "io.morgan.mio.data.sync",
            "io.morgan.mio.data.preferences",
        )

    assertNoViolations("Provider isolation", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { it.name }
        val providerImports = imports.filter { importName -> matchesPrefix(importName, "io.morgan.mio.data.provider") }
        val forbiddenProviderImport =
            when {
                isFeatureOrDomainModule(ownedFile.modulePath) -> providerImports.firstOrNull()
                !isProviderModule(ownedFile.modulePath) ->
                    providerImports.firstOrNull { importName ->
                        !matchesPrefix(importName, "io.morgan.mio.data.provider.api")
                    }

                else -> null
            }
        val crossProviderImport =
            when (ownedFile.modulePath) {
                ":data:provider:anilist" ->
                    imports.firstOrNull { importName ->
                        matchesPrefix(importName, "io.morgan.mio.data.provider.myanimelist")
                    }

                ":data:provider:myanimelist" ->
                    imports.firstOrNull { importName ->
                        matchesPrefix(importName, "io.morgan.mio.data.provider.anilist")
                    }

                else -> null
            }
        val capabilityImport =
            if (isProviderImplementation(ownedFile.modulePath)) {
                imports.firstOrNull { importName -> matchesAnyPrefix(importName, capabilityPrefixes) }
            } else {
                null
            }

        when {
            forbiddenProviderImport != null ->
                "module '${ownedFile.modulePath}' cannot import provider code '$forbiddenProviderImport'"

            crossProviderImport != null -> "provider cannot import the other provider '$crossProviderImport'"
            capabilityImport != null -> "provider implementation cannot import capability data '$capabilityImport'"
            else -> null
        }
    }
}

internal fun enforceNetworkBoundary(files: List<OwnedKotlinFile>) {
    val retrofitOrOkHttpPrefixes = listOf("retrofit2", "okhttp3")
    val serializationPrefixes = listOf("kotlinx.serialization")
    val androidNetworkPrefixes = listOf("android.net")
    val networkSurfaceNames =
        setOf(
            "NetworkClientFactory",
            "NetworkTimeouts",
            "NetworkEvent",
            "NetworkEventSink",
            "AndroidNetworkMonitor",
        )

    assertNoViolations("Network boundary", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { import -> import.name }
        val isNetworkOwner = ownedFile.modulePath == ":core:network"
        val isConcreteProvider = isProviderImplementation(ownedFile.modulePath)
        val isAppCompositionFile =
            ownedFile.modulePath == ":app" &&
                ownedFile.file.packagee?.name == "io.morgan.mio.di"
        val forbiddenNetworkLibraryImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, retrofitOrOkHttpPrefixes) && !isNetworkOwner && !isConcreteProvider
            }
        val forbiddenSerializationImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, serializationPrefixes) && !isNetworkOwner && !isConcreteProvider
            }
        val forbiddenAndroidNetworkImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, androidNetworkPrefixes) && !isNetworkOwner
            }
        val escapedNetworkSurface =
            if (!isNetworkOwner && !isConcreteProvider) {
                imports.firstOrNull { importName ->
                    networkSurfaceNames.any { name -> importName.endsWith(".$name") } &&
                        (
                            !isAppCompositionFile ||
                                importName !in
                                setOf(
                                    "io.morgan.mio.core.network.NetworkClientFactory",
                                    "io.morgan.mio.core.network.NetworkMonitor",
                                )
                        )
                }
            } else {
                null
            }

        when {
            forbiddenNetworkLibraryImport != null ->
                "Retrofit/OkHttp imports are owned by :core:network or concrete provider modules; found '$forbiddenNetworkLibraryImport'"

            forbiddenSerializationImport != null ->
                "serialization imports are owned by :core:network or concrete provider modules; found '$forbiddenSerializationImport'"

            forbiddenAndroidNetworkImport != null ->
                "Android connectivity imports are owned by :core:network; found '$forbiddenAndroidNetworkImport'"

            escapedNetworkSurface != null ->
                "shared network type escaped its boundary through '$escapedNetworkSurface'"

            else -> null
        }
    }
}

private val taskSixScopeAndQualifierNames =
    setOf(
        "AppScope",
        "AccountScope",
        "ApplicationContext",
        "DefaultDispatcher",
        "IoDispatcher",
        "MainDispatcher",
    )

private val taskSixGraphIntrinsicNames =
    setOf(
        "createGraph",
        "createGraphFactory",
        "createDynamicGraph",
        "createDynamicGraphFactory",
        "asContribution",
    )

private val taskSixGraphTypePattern = Regex("\\b(?:AppGraph|AccountGraph)\\b")

internal fun enforceMetroBoundaries(files: List<OwnedKotlinFile>) {
    assertNoViolations("Metro boundaries", files) { ownedFile ->
        val declarations = ownedFile.file.declarations(true, true)
        val declarationNames = declarations.mapNotNull { declaration -> (declaration as? KoNameProvider)?.name }
        val imports = ownedFile.file.imports.map { import -> import.name }
        val source = ownedFile.file.text
        val isApp = ownedFile.modulePath == ":app"
        val isFeatureApiModule = isFeatureApi(ownedFile.modulePath)
        val isFeatureImplementationModule = isFeatureImplementation(ownedFile.modulePath)
        val hasGraphDeclaration =
            ownedFile.file.classes(true, true).any { declaration ->
                declaration.hasAnnotationWithName("DependencyGraph") ||
                    declaration.hasAnnotationWithName("GraphExtension")
            } ||
                ownedFile.file.interfaces(true).any { declaration ->
                    declaration.hasAnnotationWithName("DependencyGraph") ||
                        declaration.hasAnnotationWithName("GraphExtension")
                }
        val graphDeclarationName =
            ownedFile.file
                .classes(true, true)
                .firstOrNull { declaration ->
                    declaration.hasAnnotationWithName("DependencyGraph") ||
                        declaration.hasAnnotationWithName("GraphExtension")
                }?.name
                ?: ownedFile.file
                    .interfaces(true)
                    .firstOrNull { declaration ->
                        declaration.hasAnnotationWithName("DependencyGraph") ||
                            declaration.hasAnnotationWithName("GraphExtension")
                    }?.name
        val misplacedGraphName =
            if (hasGraphDeclaration && !isApp) {
                graphDeclarationName ?: "graph declaration"
            } else {
                null
            }
        val misplacedNamedGraph =
            declarationNames
                .firstOrNull { name ->
                    name == "AppGraph" || name == "AccountGraph"
                }?.takeUnless { isApp }
        val misplacedScopeOrQualifier =
            declarationNames.firstOrNull { name ->
                name in taskSixScopeAndQualifierNames && ownedFile.modulePath != ":core:common"
            }
        val forbiddenDomainOrApiImport =
            if (isFeatureApiModule || ownedFile.modulePath.startsWith(":domain:")) {
                firstMatchingImport(ownedFile.file, listOf("dev.zacsweers.metro", "io.morgan.mio.di"))
            } else {
                null
            }
        val intrinsicImport =
            imports.firstOrNull { importName ->
                taskSixGraphIntrinsicNames.any { intrinsicName ->
                    importName == "dev.zacsweers.metro.$intrinsicName"
                }
            }
        val intrinsicCall =
            Regex(
                "\\b(?:createGraph|createGraphFactory|createDynamicGraph|createDynamicGraphFactory|asContribution)\\s*(?:<|\\()",
            ).find(source)?.value
        val dynamicGraphUse =
            imports.firstOrNull { importName ->
                importName == "dev.zacsweers.metro.createDynamicGraph" ||
                    importName == "dev.zacsweers.metro.createDynamicGraphFactory"
            } ?: Regex("\\bcreateDynamicGraph(?:Factory)?\\s*(?:<|\\()")
                .find(source)
                ?.value
        val forbiddenIntrinsicOutsideApp =
            if (!isApp && (intrinsicImport != null || intrinsicCall != null)) {
                intrinsicImport ?: intrinsicCall
            } else {
                null
            }
        val forbiddenFeatureGraphUse =
            if (isFeatureImplementationModule &&
                (hasGraphDeclaration || intrinsicImport != null || intrinsicCall != null)
            ) {
                graphDeclarationName ?: intrinsicImport ?: intrinsicCall ?: "graph use"
            } else {
                null
            }
        val forbiddenName =
            (declarationNames + ownedFile.file.properties(true).map { property -> property.name })
                .firstOrNull { name ->
                    name.endsWith("ServiceLocator") ||
                        name.endsWith("GraphHolder") ||
                        name.endsWith("Injector")
                }
        val staticGraphObject =
            ownedFile.file.objects(true).firstOrNull { declaration ->
                taskSixGraphTypePattern.containsMatchIn(declaration.text)
            }
        val topLevelGraphProperty =
            ownedFile.file.properties(true).firstOrNull { property ->
                property.isTopLevel && taskSixGraphTypePattern.containsMatchIn(property.text)
            }
        val companionGraphProperty =
            ownedFile.file.properties(true).firstOrNull { property ->
                !property.isTopLevel &&
                    taskSixGraphTypePattern.containsMatchIn(property.text) &&
                    (property.containingDeclaration as? KoObjectDeclaration)?.hasCompanionModifier == true
            }
        val fieldInjection =
            ownedFile.file.properties(true).firstOrNull { property ->
                property.hasAnnotationWithName("Inject")
            }
        val forbiddenContribution =
            if (ownedFile.modulePath.startsWith(":domain:") ||
                isFeatureApiModule ||
                ownedFile.modulePath == ":data:provider:api"
            ) {
                if (Regex("@ContributesBinding\\b").containsMatchIn(source)) "@ContributesBinding" else null
            } else {
                null
            }

        when {
            misplacedGraphName != null ->
                "graph declaration '$misplacedGraphName' must live in :app"

            misplacedNamedGraph != null ->
                "graph declaration '$misplacedNamedGraph' must live in :app"

            misplacedScopeOrQualifier != null ->
                "scope or qualifier '$misplacedScopeOrQualifier' must live in :core:common"

            forbiddenDomainOrApiImport != null ->
                "domain and feature API code cannot import '$forbiddenDomainOrApiImport'"

            forbiddenFeatureGraphUse != null ->
                "feature implementation cannot declare or create a graph '$forbiddenFeatureGraphUse'"

            forbiddenIntrinsicOutsideApp != null ->
                "graph intrinsic '$forbiddenIntrinsicOutsideApp' is only allowed in :app production code"

            dynamicGraphUse != null ->
                "dynamic graph intrinsic '$dynamicGraphUse' is test-only"

            forbiddenName != null ->
                "production declaration '$forbiddenName' uses a forbidden locator/holder/injector name"

            staticGraphObject != null ->
                "object '${staticGraphObject.name}' must not hold AppGraph or AccountGraph"

            topLevelGraphProperty != null ->
                "top-level property '${topLevelGraphProperty.name}' must not hold AppGraph or AccountGraph"

            companionGraphProperty != null ->
                "companion property '${companionGraphProperty.name}' must not hold AppGraph or AccountGraph"

            fieldInjection != null ->
                "property '${fieldInjection.name}' uses field injection; use constructor injection"

            forbiddenContribution != null ->
                "@ContributesBinding is not allowed in '${ownedFile.modulePath}'"

            else -> null
        }
    }
}

private val taskFiveCanonicalProviderDeclarations =
    setOf(
        "ProviderKey",
        "ProviderAccountId",
        "ProviderMediaId",
        "ProviderCollectionEntryId",
        "ProviderImageReference",
        "ProviderIdentity",
        "ProviderAuthorizationRequest",
        "ProviderAuthorizationTransaction",
        "ProviderAuthorizationLaunch",
        "ProviderAuthorizationCallback",
        "ProviderCredentials",
        "ProviderAuthenticator",
        "ProviderResult",
        "ProviderRecovery",
        "ProviderFailure",
        "ProviderMediaType",
        "ProviderMediaFormat",
        "ProviderMediaPublicationStatus",
        "ProviderProgressUnit",
        "ProviderMediaTotals",
        "ProviderReleaseTime",
        "ProviderRelease",
        "ProviderMedia",
        "ProviderCollectionStatus",
        "ProviderCollectionEntry",
        "CompleteProviderImport",
        "ProviderIdentityDataSource",
        "ProviderInitialImportDataSource",
        "ProviderBinding",
    )

internal fun enforceProviderContractOwnership(files: List<OwnedKotlinFile>) {
    val forbiddenApiImports =
        listOf(
            "android",
            "androidx",
            "io.morgan.mio.domain",
            "io.morgan.mio.core.database",
            "io.morgan.mio.core.datastore",
            "io.morgan.mio.core.network",
            "retrofit2",
            "okhttp3",
            "kotlinx.serialization",
            "io.morgan.mio.data.provider.anilist",
            "io.morgan.mio.data.provider.myanimelist",
        )
    val duplicatedProviderSpecificDeclarations =
        files
            .filter { ownedFile -> isProviderImplementation(ownedFile.modulePath) }
            .flatMap { ownedFile ->
                ownedFile.file
                    .declarations(true, true)
                    .mapNotNull { declaration -> (declaration as? KoNameProvider)?.name }
                    .filter { name ->
                        name.endsWith("Dto") ||
                            name.endsWith("ApiService") ||
                            name.endsWith("GraphQlService") ||
                            name.endsWith("OAuthService")
                    }
            }.groupingBy { name -> name }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    assertNoViolations("Provider contract ownership", files) { ownedFile ->
        val declarationNames =
            ownedFile.file
                .declarations(true, true)
                .mapNotNull { declaration -> (declaration as? KoNameProvider)?.name }
                .distinct()
        val misplacedCanonical =
            declarationNames.firstOrNull { name ->
                name in taskFiveCanonicalProviderDeclarations && ownedFile.modulePath != ":data:provider:api"
            }
        val misplacedRemoteDeclaration =
            declarationNames.firstOrNull { name ->
                name.endsWith("Dto") ||
                    name.endsWith("ApiService") ||
                    name.endsWith("GraphQlService") ||
                    name.endsWith("OAuthService")
            }
        val forbiddenApiImport =
            if (ownedFile.modulePath == ":data:provider:api") {
                firstMatchingImport(ownedFile.file, forbiddenApiImports)
            } else {
                null
            }

        when {
            misplacedCanonical != null ->
                "canonical provider declaration '$misplacedCanonical' must be owned by :data:provider:api"

            misplacedRemoteDeclaration != null && !isProviderImplementation(ownedFile.modulePath) ->
                "provider DTO/service declaration '$misplacedRemoteDeclaration' must live in one concrete provider module"

            misplacedRemoteDeclaration != null &&
                misplacedRemoteDeclaration in duplicatedProviderSpecificDeclarations ->
                "provider DTO/service declaration '$misplacedRemoteDeclaration' must live in exactly one concrete provider module"

            forbiddenApiImport != null ->
                ":data:provider:api cannot import '$forbiddenApiImport'"

            else -> null
        }
    }
}

internal fun enforceProviderContractsDoNotEscape(files: List<OwnedKotlinFile>) {
    val providerPrefixes = listOf("io.morgan.mio.data.provider")
    val escapedTypeNames = taskFiveCanonicalProviderDeclarations

    assertNoViolations("Provider contract escape", files) { ownedFile ->
        val providerImport =
            if (isFeatureOrDomainModule(ownedFile.modulePath)) {
                firstMatchingImport(ownedFile.file, providerPrefixes)
            } else {
                null
            }
        val publicEscape =
            if (
                ownedFile.modulePath != ":data:provider:api" &&
                !isProviderImplementation(ownedFile.modulePath) &&
                ownedFile.modulePath != ":core:testing" &&
                !(ownedFile.modulePath == ":app" && ownedFile.file.packagee?.name == "io.morgan.mio.di")
            ) {
                firstPublicProviderType(ownedFile, escapedTypeNames)
            } else {
                null
            }

        when {
            providerImport != null ->
                "domain and feature code cannot import provider code '$providerImport'"

            publicEscape != null ->
                "public API cannot expose provider type '$publicEscape'"

            else -> null
        }
    }
}

internal fun enforceLoggingBoundary(files: List<OwnedKotlinFile>) {
    assertNoViolations("Logging boundary", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { it.name }
        val timberImport = imports.firstOrNull { importName -> matchesPrefix(importName, "timber.log") }
        val loggingPackageImport =
            imports.firstOrNull { importName -> matchesPrefix(importName, "io.morgan.mio.core.logging") }

        when {
            timberImport != null && ownedFile.modulePath != ":core:logging" ->
                "only :core:logging may import Timber '$timberImport'"

            loggingPackageImport != null && ownedFile.modulePath.startsWith(":domain:") ->
                "domain code cannot import logging '$loggingPackageImport'"

            timberImport != null && ownedFile.modulePath.startsWith(":feature:") ->
                "features cannot import Timber directly '$timberImport'"

            else -> null
        }
    }
}

internal fun enforceSharedUiIsolation(files: List<OwnedKotlinFile>) {
    assertNoViolations(
        "Shared UI isolation",
        files.filter { it.modulePath == ":core:ui" || it.modulePath == ":core:designsystem" },
    ) { ownedFile ->
        firstMatchingImport(ownedFile.file, listOf("io.morgan.mio.feature"))?.let { importName ->
            "shared UI cannot import feature code '$importName'"
        }
    }
}

internal fun enforceGloballyForbiddenImports(files: List<OwnedKotlinFile>) {
    val forbiddenPrefixes =
        listOf(
            "androidx.navigation",
            "androidx.paging",
            "dagger",
            "com.google.dagger",
            "dagger.hilt",
            "org.koin",
            "com.google.gson",
            "com.squareup.moshi",
            "com.fasterxml.jackson",
            "io.reactivex",
            "java.time",
            "kotlinx.coroutines.GlobalScope",
        )
    val forbiddenExactImports =
        setOf(
            "java.io.Serializable",
            "java.io.ObjectInputStream",
            "java.io.ObjectOutputStream",
            "java.util.Date",
            "java.util.Calendar",
        )

    assertNoViolations("Globally forbidden imports", files) { ownedFile ->
        val forbiddenImport =
            ownedFile.file.imports
                .map { it.name }
                .firstOrNull { importName ->
                    importName in forbiddenExactImports || matchesAnyPrefix(importName, forbiddenPrefixes)
                }

        forbiddenImport?.let { importName -> "forbidden import '$importName'" }
    }
}

internal fun enforceDomainModelOwnership(files: List<OwnedKotlinFile>) {
    val domainModelDeclarations =
        setOf(
            "AccountId",
            "MediaId",
            "CollectionEntryId",
            "OperationId",
            "MediaType",
            "ProgressUnit",
            "ProgressValue",
            "UnitTotal",
            "UnitNumber",
            "MediaTitle",
            "ImageReference",
        )
    val rawIdentifierNames =
        setOf(
            "providerid",
            "remoteid",
            "anilistid",
            "myanimelistid",
            "provideraccountid",
            "providermediaid",
            "providercollectionentryid",
        )

    assertNoViolations("Domain model ownership", files) { ownedFile ->
        val declarationNames =
            buildList {
                addAll(
                    ownedFile.file.declarations(true, true).mapNotNull { declaration ->
                        (declaration as? KoNameProvider)?.name
                    },
                )
                addAll(ownedFile.file.properties(true).map { property -> property.name })
            }.distinct()
        val misplacedDeclaration =
            declarationNames.firstOrNull { name ->
                name in domainModelDeclarations && ownedFile.modulePath != ":domain:model"
            }
        val rawIdentifier =
            if (ownedFile.modulePath.startsWith(":domain:")) {
                declarationNames.firstOrNull { name -> name.lowercase() in rawIdentifierNames }
            } else {
                null
            }

        when {
            misplacedDeclaration != null ->
                "domain model declaration '$misplacedDeclaration' must be owned by :domain:model"

            rawIdentifier != null -> "domain code cannot declare raw identifier '$rawIdentifier'"
            else -> null
        }
    }
}

internal fun enforceDomainImmutability(files: List<OwnedKotlinFile>) {
    val forbiddenTypeFragments =
        listOf(
            "MutableList",
            "MutableSet",
            "MutableMap",
            "ArrayList",
            "HashSet",
            "HashMap",
            "Array<",
            "BooleanArray",
            "ByteArray",
            "CharArray",
            "DoubleArray",
            "FloatArray",
            "IntArray",
            "LongArray",
            "ShortArray",
            "UByteArray",
            "UIntArray",
            "ULongArray",
            "UShortArray",
        )

    assertNoViolations("Domain immutability", files.filter { it.modulePath.startsWith(":domain:") }) { ownedFile ->
        val properties = ownedFile.file.properties(true)
        val mutableProperty = properties.firstOrNull { property -> property.isVar }
        val mutableTypeProperty =
            properties.firstOrNull { property ->
                forbiddenTypeFragments.any { fragment -> property.text.contains(fragment) }
            }

        when {
            mutableProperty != null ->
                "property '${mutableProperty.name}' is mutable"

            mutableTypeProperty != null ->
                "property '${mutableTypeProperty.name}' uses a mutable or array type"

            else -> null
        }
    }
}

internal fun enforceRepositoryBoundaries(files: List<OwnedKotlinFile>) {
    val forbiddenRepositoryTextFragments =
        listOf(
            "android",
            "androidx",
            "room",
            "datastore",
            "retrofit",
            "dto",
            "entity",
            "dao",
            "io.morgan.mio.data.provider.",
        )
    val genericWrapperPattern = Regex("\\b(?:Resource|Outcome|Either|Result)\\s*<")
    val extendedRepositoryPattern = Regex("(?s)\\binterface\\s+\\w+Repository\\b[^{}]*:")

    assertNoViolations("Repository boundaries", files) { ownedFile ->
        val repositoryInterfaces =
            ownedFile.file
                .interfaces(true)
                .filter { declaration -> declaration.name.endsWith("Repository") }
        val repositoryClasses =
            ownedFile.file
                .classes(true, true)
                .filter { declaration -> declaration.name.endsWith("Repository") }
        val repositorySource = ownedFile.file.text
        val forbiddenFragment =
            forbiddenRepositoryTextFragments.firstOrNull { fragment ->
                repositorySource.contains(fragment, ignoreCase = true)
            }
        val wrappedResult = genericWrapperPattern.find(repositorySource)?.value
        val misplacedRepositoryClass =
            repositoryClasses.firstOrNull { declaration ->
                !ownedFile.modulePath.startsWith(":data:") &&
                    !(ownedFile.modulePath == ":core:testing" && declaration.name.startsWith("Fake"))
            }

        when {
            repositoryInterfaces.isNotEmpty() && !ownedFile.modulePath.startsWith(":domain:") ->
                "repository interface '${repositoryInterfaces.first().name}' must live in a :domain:* module"

            misplacedRepositoryClass != null ->
                "repository class '${misplacedRepositoryClass.name}' must live in a :data:* module"

            repositoryInterfaces.any { declaration -> extendedRepositoryPattern.containsMatchIn(declaration.text) } ->
                "repository interfaces must not extend a base repository or marker interface"

            repositoryInterfaces.isNotEmpty() && forbiddenFragment != null ->
                "repository interface source cannot mention '$forbiddenFragment'"

            repositoryInterfaces.isNotEmpty() && wrappedResult != null ->
                "repository interfaces cannot return generic wrapper '$wrappedResult'"

            else -> null
        }
    }
}

internal fun enforcePersistenceTypeOwnership(files: List<OwnedKotlinFile>) {
    assertNoViolations("Persistence type ownership", files) { ownedFile ->
        val declarationNames =
            buildList {
                addAll(ownedFile.file.classes(true, true).map { declaration -> declaration.name })
                addAll(ownedFile.file.interfaces(true).map { declaration -> declaration.name })
            }.distinct()
        val source = ownedFile.file.text
        val hasEntity = source.contains("@Entity") || declarationNames.any { it.endsWith("Entity") }
        val hasDao = source.contains("@Dao") || declarationNames.any { it.endsWith("Dao") }
        val hasCorePersistenceType =
            declarationNames.any { declaration ->
                declaration in
                    setOf(
                        "MioDatabase",
                        "MioDatabaseFactory",
                        "MioMigrations",
                        "MioTypeConverters",
                        "MediaAggregate",
                    ) ||
                    declaration.endsWith("Migrations")
            }
        val repositoryClass =
            ownedFile.file
                .classes(true, true)
                .firstOrNull { declaration -> declaration.name.endsWith("Repository") }
        val localDataSourceClass =
            ownedFile.file
                .classes(true, true)
                .firstOrNull { declaration -> declaration.name.endsWith("LocalDataSource") }

        when {
            (hasEntity || hasDao || hasCorePersistenceType) && ownedFile.modulePath != ":core:database" ->
                "Room persistence types must live in :core:database"

            repositoryClass != null &&
                !ownedFile.modulePath.startsWith(":data:") &&
                !(ownedFile.modulePath == ":core:testing" && repositoryClass.name.startsWith("Fake")) ->
                "repository implementation '${repositoryClass.name}' must live in a :data:* module"

            localDataSourceClass != null &&
                (!ownedFile.modulePath.startsWith(":data:") || ownedFile.modulePath.startsWith(":data:provider:")) ->
                "local data source '${localDataSourceClass.name}' must live in a capability :data:* module"

            else -> null
        }
    }
}

internal fun enforcePersistenceDoesNotEscape(files: List<OwnedKotlinFile>) {
    val roomPrefixes = listOf("androidx.room", "io.morgan.mio.core.database")
    val dataStorePrefixes = listOf("androidx.datastore", "io.morgan.mio.core.datastore")
    val publicStorageTypePattern =
        Regex(
            "\\b(?:[A-Za-z_][A-Za-z0-9_]*(?:Entity|Dao)|MioDatabase|Preferences|" +
                "DevicePreferencesRecord|AccountPreferencesRecord|[A-Za-z_][A-Za-z0-9_]*ProviderIdEntity)\\b",
        )

    assertNoViolations("Persistence escape", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { import -> import.name }
        val source = ownedFile.file.text
        val outsideRoomBoundary =
            ownedFile.modulePath != ":core:database" && !ownedFile.modulePath.startsWith(":data:")
        val outsideDataStoreBoundary =
            ownedFile.modulePath != ":core:datastore" && ownedFile.modulePath != ":data:preferences"
        val isAppCompositionFile =
            ownedFile.modulePath == ":app" && ownedFile.file.packagee?.name == "io.morgan.mio.di"
        val roomImport =
            if (outsideRoomBoundary &&
                !isAppCompositionFile
            ) {
                firstMatchingImport(ownedFile.file, roomPrefixes)
            } else {
                null
            }
        val dataStoreImport =
            if (outsideDataStoreBoundary) firstMatchingImport(ownedFile.file, dataStorePrefixes) else null
        val isRepository =
            ownedFile.file.interfaces(true).any { declaration -> declaration.name.endsWith("Repository") } ||
                ownedFile.file.classes(true, true).any { declaration -> declaration.name.endsWith("Repository") }
        val exposedStorageType =
            if (isRepository) publicStorageTypePattern.find(source)?.value else null

        when {
            roomImport != null -> "Room persistence escaped its boundary through '$roomImport'"
            dataStoreImport != null -> "DataStore persistence escaped its boundary through '$dataStoreImport'"
            exposedStorageType != null ->
                "repository public API exposes persistence type '$exposedStorageType'"

            else -> null
        }
    }
}

internal fun enforceOneMechanismPerDataSource(files: List<OwnedKotlinFile>) {
    val roomPrefixes = listOf("androidx.room", "io.morgan.mio.core.database")
    val dataStorePrefixes = listOf("androidx.datastore", "io.morgan.mio.core.datastore")
    val providerPrefixes = listOf("io.morgan.mio.data.provider", "retrofit2", "okhttp3")

    assertNoViolations("One mechanism per data source", files) { ownedFile ->
        val dataSource =
            ownedFile.file
                .classes(true, true)
                .firstOrNull { declaration -> declaration.name.endsWith("LocalDataSource") }
        if (dataSource == null) {
            null
        } else {
            val imports = ownedFile.file.imports.map { import -> import.name }
            val mechanisms =
                buildSet {
                    if (matchesAnyPrefixFromList(imports, roomPrefixes)) add("Room")
                    if (matchesAnyPrefixFromList(imports, dataStorePrefixes)) add("DataStore")
                    if (matchesAnyPrefixFromList(imports, providerPrefixes)) add("provider/network")
                }
            val repositoryImport =
                imports.firstOrNull { importName ->
                    importName.endsWith("Repository") || importName.contains(".Repository.")
                }
            val otherDataSourceImport =
                imports.firstOrNull { importName ->
                    importName.endsWith("LocalDataSource") &&
                        !importName.endsWith(".${dataSource.name}")
                }

            when {
                mechanisms.size > 1 -> "data source mixes mechanisms ${mechanisms.sorted()}"
                repositoryImport != null -> "data source cannot depend on repository '$repositoryImport'"
                otherDataSourceImport != null ->
                    "data source cannot depend on another data source '$otherDataSourceImport'"

                else -> null
            }
        }
    }
}

private fun matchesAnyPrefixFromList(
    imports: List<String>,
    prefixes: List<String>,
): Boolean = imports.any { importName -> matchesAnyPrefix(importName, prefixes) }

private fun assertNoViolations(
    ruleLabel: String,
    files: List<OwnedKotlinFile>,
    violation: (OwnedKotlinFile) -> String?,
) {
    val violations =
        files.mapNotNull { ownedFile ->
            violation(ownedFile)?.let { reason ->
                "$ruleLabel: ${ownedFile.file.path} [${ownedFile.modulePath}] - $reason"
            }
        }

    if (violations.isNotEmpty()) {
        throw AssertionError(violations.joinToString(separator = "\n"))
    }
}

private fun firstMatchingImport(
    file: KoFileDeclaration,
    prefixes: List<String>,
): String? =
    file.imports
        .map { it.name }
        .firstOrNull { importName -> matchesAnyPrefix(importName, prefixes) }

private fun firstPublicProviderType(
    ownedFile: OwnedKotlinFile,
    typeNames: Set<String>,
): String? {
    val declarations =
        buildList {
            addAll(ownedFile.file.functions(true).map { declaration -> declaration.text })
            addAll(ownedFile.file.properties(true).map { declaration -> declaration.text })
        }

    return typeNames.firstOrNull { typeName ->
        declarations.any { declaration ->
            val trimmed = declaration.trimStart()
            !trimmed.startsWith("private ") &&
                !trimmed.startsWith("internal ") &&
                Regex("\\b$typeName\\b").containsMatchIn(declaration.substringBefore('=').substringBefore('{'))
        }
    }
}

private fun matchesAnyPrefix(
    value: String,
    prefixes: List<String>,
): Boolean = prefixes.any { prefix -> matchesPrefix(value, prefix) }

private fun matchesPrefix(
    value: String,
    prefix: String,
): Boolean = value == prefix || value.startsWith("$prefix.")

private fun moduleNamespace(modulePath: String): String =
    if (modulePath == ":app") {
        "io.morgan.mio"
    } else {
        "io.morgan.mio." + modulePath.removePrefix(":").replace(':', '.')
    }

private fun isFeatureApi(modulePath: String): Boolean =
    modulePath.startsWith(":feature:") && modulePath.endsWith(":api")

private fun isFeatureImplementation(modulePath: String): Boolean =
    modulePath.startsWith(":feature:") && modulePath.endsWith(":impl")

private fun isFeatureOrDomainModule(modulePath: String): Boolean =
    modulePath.startsWith(":feature:") || modulePath.startsWith(":domain:")

private fun isProviderModule(modulePath: String): Boolean = modulePath.startsWith(":data:provider:")

private fun isProviderImplementation(modulePath: String): Boolean =
    modulePath == ":data:provider:anilist" || modulePath == ":data:provider:myanimelist"

private fun isFeatureImplementationImport(importName: String): Boolean {
    val segments = importName.split('.')
    return segments.size >= 6 &&
        segments[0] == "io" &&
        segments[1] == "morgan" &&
        segments[2] == "mio" &&
        segments[3] == "feature" &&
        segments[5] == "impl"
}

private fun isOtherFeatureImplementationImport(
    modulePath: String,
    importName: String,
): Boolean {
    if (!isFeatureImplementationImport(importName)) {
        return false
    }

    val importedFeature = importName.split('.')[4]
    val owningFeature = modulePath.split(':').filter { it.isNotEmpty() }.getOrNull(1)
    return importedFeature != owningFeature
}
