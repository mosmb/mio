package io.morgan.mio.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceAndObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

data class OwnedKotlinFile(
    val modulePath: String,
    val file: KoFileDeclaration,
    val sourceSet: String = "main",
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
            "io.morgan.mio.sync.work",
            "androidx.work",
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
        val isAppProviderComposition =
            ownedFile.modulePath == ":app" && ownedFile.file.packagee?.name == "io.morgan.mio.di"
        val forbiddenProviderImport =
            when {
                isFeatureOrDomainModule(ownedFile.modulePath) -> providerImports.firstOrNull()
                !isProviderModule(ownedFile.modulePath) && !isAppProviderComposition ->
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
        val isNavigationFeatureApi = isFeatureApi(ownedFile.modulePath)
        val isAppCompositionFile =
            ownedFile.modulePath == ":app" &&
                ownedFile.file.packagee?.name == "io.morgan.mio.di"
        val forbiddenNetworkLibraryImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, retrofitOrOkHttpPrefixes) && !isNetworkOwner && !isConcreteProvider
            }
        val forbiddenSerializationImport =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, serializationPrefixes) &&
                    !isNetworkOwner &&
                    !isConcreteProvider &&
                    !(isNavigationFeatureApi && importName == "kotlinx.serialization.Serializable") &&
                    !(
                        ownedFile.modulePath == ":feature:media:api" &&
                            fileName(ownedFile.file.path) == "MediaKey.kt" &&
                            ownedFile.file.text.contains("private object MediaIdSerializer")
                    )
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

internal fun enforceNavigationBoundaries(files: List<OwnedKotlinFile>) {
    val typedDomainModelNames =
        files
            .filter { ownedFile -> ownedFile.modulePath == ":domain:model" }
            .flatMap { ownedFile ->
                ownedFile.file
                    .classes(true, true)
                    .filter { declaration ->
                        declaration.hasValueModifier
                    }.map { declaration -> declaration.name }
            }.toSet()
    val allowedEnumNames =
        files
            .filter { ownedFile ->
                ownedFile.modulePath == ":domain:model" || isFeatureApi(ownedFile.modulePath)
            }.flatMap { ownedFile ->
                ownedFile.file
                    .classes(true, true)
                    .filter { declaration ->
                        declaration.hasEnumModifier
                    }.map { declaration -> declaration.name }
            }.toSet()
    val primitiveTypes =
        setOf(
            "Byte",
            "Short",
            "Int",
            "Long",
            "Float",
            "Double",
            "Boolean",
            "Char",
            "String",
        )
    val stateOwnerModules = setOf(":app", ":core:navigation")
    val focusedFeatureApiImports =
        files
            .filter { ownedFile -> isFeatureApi(ownedFile.modulePath) }
            .flatMap { ownedFile ->
                val packageName = ownedFile.file.packagee?.name ?: return@flatMap emptyList()
                ownedFile.file
                    .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                    .filter { declaration ->
                        declaration.text.substringBefore('{').contains(": FocusedDestination")
                    }.map { declaration -> "$packageName.${declaration.name}" }
            }.toSet()
    val navigationStateImports =
        listOf(
            "androidx.navigation3.runtime.NavBackStack",
            "androidx.navigation3.runtime.rememberNavBackStack",
            "androidx.navigation3.runtime.rememberDecoratedNavEntries",
            "androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator",
        )

    assertNoViolations("Navigation boundaries", files) { ownedFile ->
        val declarations = navigationDeclarations(ownedFile.file)
        val parentNamesByDeclaration: Map<KoClassAndInterfaceAndObjectDeclaration, Set<String>> =
            declarations.associate { declaration ->
                declaration to navigationParentNames(declaration)
            }
        val markerDeclaration =
            declarations.firstOrNull { declaration ->
                declaration.name == "FocusedDestination" &&
                    declaration is KoInterfaceDeclaration &&
                    ownedFile.modulePath == ":core:navigation"
            }
        val keyDeclarations =
            parentNamesByDeclaration
                .filter { (declaration, parentNames) ->
                    "NavKey" in parentNames ||
                        "FocusedDestination" in parentNames ||
                        (ownedFile.modulePath == ":feature:media:api" && declaration.name == "MediaKey")
                }.keys
                .filterNot { declaration -> declaration == markerDeclaration }
        val entryBuilderDeclarations =
            parentNamesByDeclaration
                .filter { (_, parentNames) -> "NavEntryBuilder" in parentNames }
                .keys
        val imports = ownedFile.file.imports.map { import -> import.name }
        val navDisplayUse =
            imports.firstOrNull { importName ->
                importName == "androidx.navigation3.ui.NavDisplay"
            } ?: "androidx.navigation3.ui.NavDisplay"
                .takeIf(ownedFile.file.text::contains)
        val forbiddenFeatureApiImport =
            if (isFeatureApi(ownedFile.modulePath)) {
                imports.firstOrNull { importName ->
                    matchesAnyPrefix(
                        importName,
                        listOf(
                            "androidx.compose",
                            "androidx.navigation3.runtime.EntryProviderScope",
                            "androidx.navigation3.runtime.NavEntry",
                            "io.morgan.mio.core.navigation.NavEntryBuilder",
                        ),
                    )
                }
            } else {
                null
            }
        val forbiddenStateUse =
            imports.firstOrNull { importName ->
                matchesAnyPrefix(importName, navigationStateImports) && ownedFile.modulePath !in stateOwnerModules
            } ?: navigationStateImports
                .firstOrNull { importName ->
                    ownedFile.modulePath !in stateOwnerModules && ownedFile.file.text.contains(importName)
                }
        val directBackStackMutation =
            if (ownedFile.modulePath !in stateOwnerModules &&
                (ownedFile.file.text.contains("NavBackStack") || ownedFile.file.text.contains("rememberNavBackStack"))
            ) {
                Regex("\\b\\w+\\.(?:add|addAll|remove|removeAt|removeLast|clear|set)\\s*(?:<[^>]+>)?\\(")
                    .find(ownedFile.file.text)
                    ?.value
            } else {
                null
            }
        val serializationImport =
            if (isFeatureApi(ownedFile.modulePath)) {
                imports.firstOrNull { importName ->
                    matchesPrefix(importName, "kotlinx.serialization") &&
                        importName != "kotlinx.serialization.Serializable" &&
                        !isPrivateFocusedKeySerializer(ownedFile, keyDeclarations)
                }
            } else {
                null
            }
        val privateFocusedKeySerializerIssue =
            keyDeclarations
                .firstOrNull { declaration -> declaration.name == "MediaKey" }
                ?.takeIf { !isPrivateFocusedKeySerializer(ownedFile, keyDeclarations) }
                ?.name
        val keyPayloadIssue =
            keyDeclarations
                .asSequence()
                .flatMap { declaration ->
                    val parameters =
                        if (declaration is KoClassDeclaration && declaration.hasPrimaryConstructor) {
                            declaration.primaryConstructor?.parameters.orEmpty()
                        } else {
                            emptyList()
                        }
                    val properties = declaration.properties(false)
                    (
                        parameters.mapNotNull { parameter ->
                            parameter.type.text.let { typeText -> typeText to parameter.name }
                        } +
                            properties.mapNotNull { property ->
                                property.type?.text?.let { typeText -> typeText to property.name }
                            }
                    ).asSequence()
                }.mapNotNull { (typeText, propertyName) ->
                    val normalizedType = typeText.replace(" ", "").removeSuffix("?")
                    val simpleType = normalizedType.substringAfterLast('.')
                    val importedType =
                        imports.firstOrNull { importName ->
                            importName.substringAfterLast('.') == simpleType
                        }
                    val importedPackage = importedType?.substringBeforeLast('.', missingDelimiterValue = "")
                    val isPrimitive = simpleType in primitiveTypes && normalizedType == simpleType
                    val isTypedDomainValue =
                        simpleType in typedDomainModelNames &&
                            importedPackage == "io.morgan.mio.domain.model"
                    val isAllowedEnum =
                        simpleType in allowedEnumNames &&
                            (
                                importedPackage == "io.morgan.mio.domain.model" ||
                                    importedPackage != null &&
                                    importedPackage.startsWith("io.morgan.mio.feature.") &&
                                    importedPackage.endsWith(".api")
                            )
                    if (isPrimitive || isTypedDomainValue || isAllowedEnum) {
                        null
                    } else {
                        "key property '$propertyName' uses forbidden payload type '$typeText'"
                    }
                }.firstOrNull()
        val wrongKeyOwner =
            keyDeclarations
                .firstOrNull { declaration ->
                    !isFeatureApi(ownedFile.modulePath)
                }?.name
        val unserializableKey =
            keyDeclarations
                .firstOrNull { declaration ->
                    !declaration.hasAnnotationWithName("Serializable")
                }?.name
        val parcelableKey =
            keyDeclarations
                .firstOrNull { declaration ->
                    val parentNames = parentNamesByDeclaration.getValue(declaration)
                    "Parcelable" in parentNames || imports.any { importName -> importName == "android.os.Parcelable" }
                }?.name
        val wrongFocusedOwner =
            keyDeclarations
                .firstOrNull { declaration ->
                    "FocusedDestination" in parentNamesByDeclaration.getValue(declaration) &&
                        !isFeatureApi(ownedFile.modulePath)
                }?.name
        val wrongEntryBuilderOwner =
            entryBuilderDeclarations
                .firstOrNull { declaration ->
                    !isFeatureImplementation(ownedFile.modulePath)
                }?.name
        val wrongEntryBuilderFeature =
            if (entryBuilderDeclarations.isNotEmpty() && isFeatureImplementation(ownedFile.modulePath)) {
                val implementationFeature = ownedFile.modulePath.split(':').getOrNull(2)
                imports.firstOrNull { importName ->
                    val segments = importName.split('.')
                    segments.size > 5 &&
                        segments.take(4) == listOf("io", "morgan", "mio", "feature") &&
                        segments[5] == "api" &&
                        segments[4] != implementationFeature &&
                        importName !in focusedFeatureApiImports
                }
            } else {
                null
            }

        when {
            wrongKeyOwner != null ->
                "NavKey declaration '$wrongKeyOwner' must live in a feature API module"

            unserializableKey != null ->
                "NavKey declaration '$unserializableKey' must be annotated with @Serializable"

            parcelableKey != null ->
                "NavKey declaration '$parcelableKey' must not implement Parcelable"

            keyPayloadIssue != null -> keyPayloadIssue
            wrongEntryBuilderOwner != null ->
                "NavEntryBuilder implementation '$wrongEntryBuilderOwner' must live in its matching feature impl module"

            wrongEntryBuilderFeature != null ->
                "NavEntryBuilder implementation cannot register another feature API '$wrongEntryBuilderFeature'"

            navDisplayUse != null && ownedFile.modulePath != ":app" ->
                "NavDisplay is app-owned; found '$navDisplayUse'"

            forbiddenStateUse != null ->
                "navigation back-stack state is owned by :core:navigation or :app; found '$forbiddenStateUse'"

            directBackStackMutation != null ->
                "direct back-stack mutation is owned by :core:navigation or :app; found '$directBackStackMutation'"

            wrongFocusedOwner != null ->
                "FocusedDestination implementation '$wrongFocusedOwner' must live in a feature API module"

            privateFocusedKeySerializerIssue != null ->
                "focused key '$privateFocusedKeySerializerIssue' must use a private MediaIdSerializer"

            forbiddenFeatureApiImport != null ->
                "feature API cannot own entry builders or Compose/navigation UI types; found '$forbiddenFeatureApiImport'"

            serializationImport != null ->
                "feature API serialization is limited to kotlinx.serialization.Serializable; found '$serializationImport'"

            else -> null
        }
    }
}

private fun isPrivateFocusedKeySerializer(
    ownedFile: OwnedKotlinFile,
    keyDeclarations: List<KoClassAndInterfaceAndObjectDeclaration>,
): Boolean =
    keyDeclarations.any { declaration -> declaration.name == "MediaKey" } &&
        ownedFile.file.text.contains(Regex("\\bprivate\\s+object\\s+MediaIdSerializer\\b")) &&
        ownedFile.file.text.contains("KSerializer<MediaId>")

private fun navigationDeclarations(file: KoFileDeclaration): List<KoClassAndInterfaceAndObjectDeclaration> =
    file
        .declarations(true, true)
        .filterIsInstance<KoClassAndInterfaceAndObjectDeclaration>()

private fun navigationParentNames(declaration: KoClassAndInterfaceAndObjectDeclaration): Set<String> {
    val header = declaration.text.substringBefore('{').substringBefore('=')
    return setOf("NavKey", "FocusedDestination", "NavEntryBuilder").filterTo(mutableSetOf()) { parentName ->
        Regex("\\)\\s*:\\s*[^\\{=]*\\b$parentName\\b").containsMatchIn(header) ||
            Regex("\\b(?:class|object|interface)\\s+\\w+\\s*:\\s*[^\\{=]*\\b$parentName\\b")
                .containsMatchIn(header)
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
        "ProcessCoroutineScope",
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
            ownedFile.file
                .objects(true)
                .firstOrNull { declaration ->
                    taskSixGraphTypePattern.containsMatchIn(declaration.text)
                }?.takeUnless {
                    ownedFile.modulePath == ":app" && fileName(ownedFile.file.path) == "SyncProcessBindings.kt"
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
        "ProviderAuthorizationRedirect",
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
        "ProviderBindingResolver",
        "ProviderCredentialStore",
        "ProviderCredentialStorageException",
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
        val classifierNames =
            ownedFile.file
                .classesAndInterfacesAndObjects(includeNested = true, includeLocal = false)
                .mapNotNull { declaration -> (declaration as? KoNameProvider)?.name }
        val misplacedAniListDeclaration =
            classifierNames.firstOrNull { name ->
                name.startsWith("AniList") && ownedFile.modulePath != ":data:provider:anilist"
            }
        val misplacedMyAnimeListDeclaration =
            classifierNames.firstOrNull { name ->
                name.startsWith("MyAnimeList") && ownedFile.modulePath != ":data:provider:myanimelist"
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

            misplacedAniListDeclaration != null ->
                "declaration '$misplacedAniListDeclaration' must be owned by :data:provider:anilist"

            misplacedMyAnimeListDeclaration != null ->
                "declaration '$misplacedMyAnimeListDeclaration' must be owned by :data:provider:myanimelist"

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
                !(
                    ownedFile.modulePath == ":data:sync" &&
                        fileName(ownedFile.file.path) in
                        setOf("PersistedSyncAccount.kt", "RoomSyncAccountLocalDataSource.kt")
                ) &&
                !(ownedFile.modulePath == ":app" && ownedFile.file.packagee?.name == "io.morgan.mio.di") &&
                !isCredentialBoundaryFile(ownedFile)
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

internal fun enforcePlatformServiceBoundaries(files: List<OwnedKotlinFile>) {
    val qualifierNames = taskSixScopeAndQualifierNames
    val cryptoPrefixes = listOf("android.security.keystore", "java.security.KeyStore", "javax.crypto")
    val credentialConstants =
        listOf(
            "mio.secure_credentials",
            "io.morgan.mio.credentials.v1",
            "credential.anilist",
            "credential.myanimelist",
        )
    val providerCredentialNames =
        setOf("ProviderCredentialStore", "ProviderCredentials", "ProviderCredentialStorageException")

    assertNoViolations("Platform service boundaries", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { import -> import.name }
        val source = ownedFile.file.text
        val declarations =
            ownedFile.file
                .declarations(true, true)
                .mapNotNull { declaration -> (declaration as? KoNameProvider)?.name }
        val isProcessBinding =
            ownedFile.modulePath == ":app" &&
                fileName(ownedFile.file.path) == "ProcessBindings.kt" &&
                ownedFile.sourceSet == "main"
        val isCredentialBoundary = isCredentialBoundaryFile(ownedFile)
        val isProviderCredentialOwner =
            ownedFile.sourceSet == "test" ||
                ownedFile.modulePath == ":data:provider:api" ||
                isProviderImplementation(ownedFile.modulePath) ||
                ownedFile.modulePath == ":data:media" ||
                ownedFile.modulePath == ":data:account" ||
                ownedFile.modulePath == ":data:sync" ||
                ownedFile.modulePath == ":core:testing" ||
                (ownedFile.modulePath == ":app" && ownedFile.file.packagee?.name == "io.morgan.mio.di") ||
                isCredentialBoundary
        val misplacedQualifier =
            declarations.firstOrNull { name ->
                name in qualifierNames && ownedFile.modulePath != ":core:common"
            }
        val directClock =
            Regex("\\bClock\\.System\\b").find(source)?.value?.takeUnless { isProcessBinding }
        val directDispatcher =
            Regex("\\bDispatchers\\.(?:Default|IO|Main|Unconfined)\\b")
                .find(source)
                ?.value
                ?.takeUnless { isProcessBinding }
        val cryptoImport =
            imports
                .firstOrNull { importName -> matchesAnyPrefix(importName, cryptoPrefixes) }
                ?.takeUnless { isCredentialBoundary }
        val credentialConstant =
            credentialConstants
                .firstOrNull { constant -> source.contains(constant) }
                ?.takeUnless { isCredentialBoundary }
        val codecEscape =
            (
                imports.firstOrNull { importName -> importName.endsWith(".CredentialRecordCodec") }
                    ?: declarations.firstOrNull { name -> name == "CredentialRecordCodec" }
            )?.takeUnless { isCredentialBoundary }
        val providerCredentialImport =
            imports
                .firstOrNull { importName ->
                    providerCredentialNames.any { name -> importName.endsWith(".$name") }
                }?.takeUnless { isProviderCredentialOwner }
        val coilImport =
            imports
                .firstOrNull { importName -> matchesPrefix(importName, "coil3") }
                ?.takeUnless {
                    ownedFile.sourceSet == "test" ||
                        ownedFile.modulePath == ":core:ui" ||
                        (
                            ownedFile.modulePath == ":feature:media:impl" &&
                                it == "coil3.ImageLoader"
                        ) ||
                        (
                            ownedFile.modulePath == ":feature:today:impl" &&
                                fileName(ownedFile.file.path) == "TodayScreen.kt" &&
                                it == "coil3.ImageLoader"
                        ) ||
                        (
                            ownedFile.modulePath == ":app" &&
                                (
                                    ownedFile.file.packagee?.name == "io.morgan.mio.di" ||
                                        fileName(ownedFile.file.path) == "MainActivity.kt" ||
                                        fileName(ownedFile.file.path) == "MioApp.kt"
                                )
                        )
                }
        val workImport =
            imports
                .firstOrNull { importName -> matchesPrefix(importName, "androidx.work") }
                ?.takeUnless {
                    ownedFile.sourceSet == "test" ||
                        ownedFile.modulePath == ":sync:work" ||
                        (
                            ownedFile.modulePath == ":app" &&
                                (
                                    ownedFile.file.packagee?.name == "io.morgan.mio.di" ||
                                        fileName(ownedFile.file.path) == "MioApplication.kt"
                                )
                        )
                }
        val timberImport =
            imports
                .firstOrNull { importName -> matchesPrefix(importName, "timber.log") }
                ?.takeUnless {
                    ownedFile.modulePath == ":core:logging" && ownedFile.sourceSet == "debug"
                }
        val runtimeTimberMessage =
            Regex("""\bTimber\.(?:d|i|w|e)\s*\(\s*(?!event\.name\b|MioLogEvent\.[A-Z_]+\.name\b)[^)]""")
                .find(source)
                ?.value
        val forbiddenConsoleUse =
            when {
                firstMatchingImport(ownedFile.file, listOf("android.util.Log")) != null ->
                    firstMatchingImport(ownedFile.file, listOf("android.util.Log"))
                Regex("\\bprintln\\s*\\(").containsMatchIn(source) -> "println"
                Regex("\\bSystem\\.(?:out|err)\\b").containsMatchIn(source) -> "System.out/err"
                else -> null
            }

        when {
            misplacedQualifier != null ->
                "scope or qualifier '$misplacedQualifier' must live in :core:common"
            directClock != null ->
                "Clock.System is app-owned by ProcessBindings.kt; found '$directClock'"
            directDispatcher != null ->
                "direct dispatcher '$directDispatcher' is app-owned by ProcessBindings.kt"
            cryptoImport != null ->
                "credential cryptography import '$cryptoImport' must stay in the account credential boundary"
            credentialConstant != null ->
                "credential storage constant '$credentialConstant' must stay in the account credential boundary"
            codecEscape != null ->
                "CredentialRecordCodec must stay in the account credential boundary"
            providerCredentialImport != null ->
                "provider credential type '$providerCredentialImport' escaped its allowed boundary"
            coilImport != null ->
                "Coil import '$coilImport' is limited to :core:ui, app composition, and tests"
            workImport != null ->
                "WorkManager import '$workImport' is limited to :sync:work, MioApplication, app DI, and tests"
            timberImport != null ->
                "Timber import '$timberImport' is limited to core/logging debug sources"
            runtimeTimberMessage != null ->
                "Timber may receive only closed event names; found '$runtimeTimberMessage'"
            forbiddenConsoleUse != null ->
                "production source cannot use '$forbiddenConsoleUse'"
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
            timberImport != null &&
                !(ownedFile.modulePath == ":core:logging" && ownedFile.sourceSet == "debug") ->
                "only core/logging debug sources may import Timber '$timberImport'"

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

internal fun enforceSharedUiOwnership(files: List<OwnedKotlinFile>) {
    val forbiddenImports =
        listOf(
            "io.morgan.mio.feature",
            "io.morgan.mio.domain",
            "io.morgan.mio.data",
            "io.morgan.mio.core.navigation",
            "androidx.navigation",
            "androidx.room",
            "androidx.datastore",
            "retrofit2",
            "okhttp3",
            "io.morgan.mio.data.provider",
        )

    assertNoViolations(
        "Shared UI ownership",
        files.filter { ownedFile ->
            ownedFile.modulePath == ":core:designsystem" &&
                ownedFile.file.packagee?.name == "io.morgan.mio.core.designsystem.component"
        },
    ) { ownedFile ->
        val publicDeclaration =
            ownedFile.file
                .declarations(false, false)
                .mapNotNull { declaration ->
                    if (declaration !is KoClassAndInterfaceAndObjectDeclaration &&
                        declaration !is KoFunctionDeclaration
                    ) {
                        return@mapNotNull null
                    }
                    val named = declaration as? KoNameProvider ?: return@mapNotNull null
                    if (named.name != ownedFile.file.packagee?.name &&
                        isPublicDeclaration(declaration) &&
                        !named.name.startsWith("Mio")
                    ) {
                        named.name
                    } else {
                        null
                    }
                }.firstOrNull()
        val forbiddenImport = firstMatchingImport(ownedFile.file, forbiddenImports)

        when {
            publicDeclaration != null ->
                "public shared component/type '$publicDeclaration' must start with Mio"

            forbiddenImport != null ->
                "design-system component cannot import '$forbiddenImport'"

            else -> null
        }
    }
}

internal fun enforceRouteScreenConvention(files: List<OwnedKotlinFile>) {
    assertNoViolations(
        "Route/Screen convention",
        files.filter { ownedFile -> isFeatureImplementation(ownedFile.modulePath) },
    ) { ownedFile ->
        val functions = ownedFile.file.functions(true)
        val routes = functions.filter { declaration -> declaration.name.endsWith("Route") }
        val screens = functions.filter { declaration -> declaration.name.endsWith("Screen") }

        val routeIssue =
            routes
                .asSequence()
                .mapNotNull { route ->
                    val prefix = route.name.removeSuffix("Route")
                    val matchingScreens = screens.filter { screen -> screen.name == "${prefix}Screen" }
                    when {
                        matchingScreens.size != 1 ->
                            "Route '${route.name}' must have exactly one same-file '${prefix}Screen'"

                        fileName(ownedFile.file.path) != "${prefix}Screen.kt" ->
                            "Route '${route.name}' must be declared in '${prefix}Screen.kt'"

                        !hasModifierAsFirstOptionalParameter(route) ->
                            "Route '${route.name}' must have Modifier as its first optional parameter"

                        countCall(route.text, "${prefix}Screen") != 1 ->
                            "Route '${route.name}' must invoke '${prefix}Screen' exactly once"

                        containsVisualLayout(route.text) ->
                            "Route '${route.name}' must not declare visual layout"

                        else -> null
                    }
                }.firstOrNull()
        val screenIssue =
            screens
                .asSequence()
                .mapNotNull { screen ->
                    val prefix = screen.name.removeSuffix("Screen")
                    val matchingRoutes = routes.filter { route -> route.name == "${prefix}Route" }
                    when {
                        matchingRoutes.size != 1 ->
                            "Screen '${screen.name}' must have exactly one same-file '${prefix}Route'"

                        fileName(ownedFile.file.path) != "${prefix}Screen.kt" ->
                            "Screen '${screen.name}' must be declared in '${prefix}Screen.kt'"

                        !hasModifierAsFirstOptionalParameter(screen) ->
                            "Screen '${screen.name}' must have Modifier as its first optional parameter"

                        containsForbiddenScreenParameter(screen) ->
                            "Screen '${screen.name}' must remain stateless and not accept navigation/data types"

                        containsForbiddenScreenBody(screen.text) ->
                            "Screen '${screen.name}' must remain stateless and contain only UI rendering"

                        else -> null
                    }
                }.firstOrNull()

        routeIssue ?: screenIssue
    }
}

internal fun enforceArchitecturalFileCohesion(files: List<OwnedKotlinFile>) {
    assertNoViolations("Architectural file cohesion", files) { ownedFile ->
        val actualFileName = fileName(ownedFile.file.path)
        val roleNames =
            topLevelArchitecturalDeclarations(ownedFile.file)
                .map { declaration -> declaration.name }
                .filter { name -> architecturalRoleSuffixes.any(name::endsWith) }
        val mismatchedRole =
            roleNames.firstOrNull { roleName -> actualFileName != "$roleName.kt" }
        val mismatchedEntity =
            ownedFile.file
                .classes(includeNested = false, includeLocal = false)
                .filter { declaration -> declaration.hasAnnotationWithName("Entity") }
                .firstOrNull { declaration ->
                    actualFileName != "${declaration.name}.kt" &&
                        !isPendingOperationEntityAggregation(declaration.name, actualFileName)
                }
        val mismatchedPublicEnum =
            topLevelPublicClasses(ownedFile.file)
                .firstOrNull { declaration ->
                    declaration.hasEnumModifier && actualFileName != "${declaration.name}.kt"
                }
        val mismatchedPublicValue =
            topLevelPublicClasses(ownedFile.file)
                .firstOrNull { declaration ->
                    declaration.hasValueModifier && actualFileName != "${declaration.name}.kt"
                }
        val publicTypeDeclarations = topLevelPublicDeclarations(ownedFile.file)
        val publicFunctionDeclarations = topLevelPublicFunctions(ownedFile.file)
        val filenameStem = actualFileName.removeSuffix(".kt")
        val missingPrimaryDeclaration =
            publicTypeDeclarations.isNotEmpty() &&
                publicTypeDeclarations.none { declaration -> declaration.name == filenameStem } &&
                publicFunctionDeclarations.none { declaration -> declaration.name == filenameStem }

        when {
            roleNames.size > 1 ->
                "file '$actualFileName' contains multiple architectural roles: ${roleNames.joinToString()}"

            mismatchedRole != null ->
                "architectural role '$mismatchedRole' must live in '$mismatchedRole.kt'"

            mismatchedEntity != null ->
                "entity '${mismatchedEntity.name}' must live in '${mismatchedEntity.name}.kt'"

            mismatchedPublicEnum != null ->
                "public enum class '${mismatchedPublicEnum.name}' must live in '${mismatchedPublicEnum.name}.kt'"

            mismatchedPublicValue != null ->
                "public value class '${mismatchedPublicValue.name}' must live in '${mismatchedPublicValue.name}.kt'"

            missingPrimaryDeclaration ->
                "file '$actualFileName' with public top-level types must match one public type or function declaration"

            else -> null
        }
    }
}

internal fun enforceViewModelPresentationConvention(files: List<OwnedKotlinFile>) {
    val featureFiles = files.filter { ownedFile -> isFeatureImplementation(ownedFile.modulePath) }
    val viewModelNames =
        featureFiles
            .flatMap { ownedFile -> ownedFile.file.classes(true, true) }
            .map { declaration -> declaration.name }
            .filter { name -> name.endsWith("ViewModel") }
            .toSet()

    assertNoViolations("ViewModel presentation convention", featureFiles) { ownedFile ->
        val source = ownedFile.file.text
        val viewModel =
            ownedFile.file
                .classes(true, true)
                .firstOrNull { declaration -> declaration.name.endsWith("ViewModel") }
        val viewModelIssue =
            viewModel?.let { declaration ->
                val declarationText = declaration.text
                val assistedViewModel = declaration.hasAnnotationWithName("AssistedInject")
                val matchingRouteName = "${declaration.name.removeSuffix("ViewModel")}Route"
                val matchingRoute =
                    featureFiles
                        .filter { routeFile -> routeFile.modulePath == ownedFile.modulePath }
                        .asSequence()
                        .flatMap { routeFile ->
                            routeFile.file.functions(true).asSequence().map { route ->
                                routeFile to
                                    route
                            }
                        }.filter { (_, route) -> route.name == matchingRouteName }
                        .toList()
                val route = matchingRoute.singleOrNull()
                val stateFlowNames =
                    Regex("\\bval\\s+([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*StateFlow\\s*<")
                        .findAll(declarationText)
                        .map { match -> match.groupValues[1] }
                        .toList()
                val assistedFactoryIsValid =
                    assistedViewModel &&
                        Regex("@AssistedFactory\\b").containsMatchIn(declarationText) &&
                        Regex("@ManualViewModelAssistedFactoryKey\\b").containsMatchIn(declarationText) &&
                        Regex("@ContributesIntoMap\\s*\\(\\s*AccountScope::class\\s*\\)").containsMatchIn(
                            declarationText,
                        ) &&
                        Regex("fun\\s+interface\\s+Factory\\b").containsMatchIn(declarationText)
                val accountScopedStandardViewModel =
                    Regex("@ContributesIntoMap\\s*\\(\\s*AccountScope::class\\s*\\)").containsMatchIn(source) &&
                        ownedFile.file.imports.any { import ->
                            import.name == "io.morgan.mio.domain.model.AccountId"
                        }
                val contributesIntoAllowedScope =
                    Regex("@ContributesIntoMap\\s*\\(\\s*AppScope::class\\s*\\)").containsMatchIn(source) ||
                        accountScopedStandardViewModel
                when {
                    !Regex("\\bViewModel\\s*\\(").containsMatchIn(declarationText) ->
                        "ViewModel '${declaration.name}' must extend androidx.lifecycle.ViewModel"

                    !assistedViewModel && !declaration.hasAnnotationWithName("Inject") ->
                        "ViewModel '${declaration.name}' must use constructor injection"

                    !assistedViewModel && !declaration.hasAnnotationWithName("ViewModelKey") ->
                        "ViewModel '${declaration.name}' must have @ViewModelKey"

                    !assistedViewModel &&
                        !contributesIntoAllowedScope ->
                        "ViewModel '${declaration.name}' must contribute into AppScope"

                    assistedViewModel && declaration.hasAnnotationWithName("ViewModelKey") ->
                        "assisted ViewModel '${declaration.name}' must use its AccountScope assisted factory"

                    assistedViewModel && !assistedFactoryIsValid ->
                        "assisted ViewModel '${declaration.name}' must contribute a ManualViewModelAssistedFactory in AccountScope"

                    matchingRoute.size != 1 ->
                        "ViewModel '${declaration.name}' must have exactly one matching Route in ${ownedFile.modulePath}"

                    assistedViewModel &&
                        !Regex(
                            "\\bassistedMetroViewModel\\s*<\\s*${Regex.escape(
                                declaration.name,
                            )}\\s*,\\s*${Regex.escape(declaration.name)}\\.Factory\\s*>",
                        ).containsMatchIn(route!!.second.text) ->
                        "Route '$matchingRouteName' must obtain '${declaration.name}' with assistedMetroViewModel()"

                    !assistedViewModel &&
                        !Regex("\\bmetroViewModel\\s*<\\s*${Regex.escape(declaration.name)}\\s*>").containsMatchIn(
                            route!!.second.text,
                        ) ->
                        "Route '$matchingRouteName' must obtain '${declaration.name}' with metroViewModel()"

                    stateFlowNames.isEmpty() ->
                        "ViewModel '${declaration.name}' must expose feature-owned StateFlow presentation state"

                    stateFlowNames.any { propertyName ->
                        !Regex(
                            "\\bviewModel\\s*\\.\\s*${Regex.escape(
                                propertyName,
                            )}\\s*\\.\\s*collectAsStateWithLifecycle\\s*\\(",
                        ).containsMatchIn(route?.second?.text.orEmpty())
                    } ->
                        "Route '$matchingRouteName' must lifecycle-collect every ViewModel StateFlow property"

                    countCall(
                        route?.second?.text.orEmpty(),
                        "${declaration.name.removeSuffix("ViewModel")}Screen",
                    ) != 1 ->
                        "Route '$matchingRouteName' must invoke its Screen exactly once"

                    else -> null
                }
            }

        val flowPropertyIssue =
            ownedFile.file
                .properties(true)
                .firstOrNull { property ->
                    val propertyText = property.type?.text.orEmpty()
                    val isFlow =
                        Regex("\\b(?:StateFlow|SharedFlow)\\s*<").containsMatchIn(propertyText)
                    isFlow && containsForbiddenPresentationFlowType(ownedFile, propertyText)
                }?.let { property ->
                    "flow property '${property.name}' must expose only feature-owned UI state/effects"
                }

        val screenIssue =
            ownedFile.file
                .functions(true)
                .filter { function -> function.name.endsWith("Screen") }
                .firstOrNull { function ->
                    containsForbiddenScreenBody(function.text) ||
                        containsForbiddenScreenParameter(function)
                }?.let { function ->
                    "Screen '${function.name}' must not accept or use ViewModel/domain/navigation/runtime types"
                }

        when {
            Regex("\\bChannel\\s*<|\\bChannel\\s*\\(").containsMatchIn(source) ->
                "feature presentation must not use Channel for UI effects"

            Regex("\\bcollectAsState\\s*\\(").containsMatchIn(source) ->
                "feature ViewModel state must use collectAsStateWithLifecycle()"

            firstMatchingImport(ownedFile.file, listOf("io.morgan.mio.data", "io.morgan.mio.data.provider")) != null ->
                "feature presentation cannot import data or provider code"

            firstMatchingImport(ownedFile.file, listOf("io.morgan.mio.di.AppGraph", "io.morgan.mio.di.AccountGraph")) !=
                null ||
                Regex("\\b(?:AppGraph|AccountGraph)\\b").containsMatchIn(source) ->
                "feature presentation cannot access application or account graphs"

            Regex(
                "\\b(?:EventWrapper|ConsumedEvent|hasBeenHandled|consumed)\\b|\\bEvent\\s*<",
            ).containsMatchIn(source) ->
                "feature presentation must not use consumed-event wrappers"

            viewModelIssue != null -> viewModelIssue
            flowPropertyIssue != null -> flowPropertyIssue
            screenIssue != null -> screenIssue
            else -> null
        }
    }

    assertNoViolations("ViewModel presentation convention", files) { ownedFile ->
        val source = ownedFile.file.text
        val imports = ownedFile.file.imports.map { import -> import.name }
        val localFactoryImport = imports.firstOrNull { importName -> importName.endsWith("LocalMetroViewModelFactory") }
        val providerModuleIssue =
            if (isProviderImplementation(ownedFile.modulePath) &&
                Regex(
                    "\\b(?:AccountSession|OnboardingViewModel|OnboardingScreen|OnboardingRoute)\\b",
                ).containsMatchIn(source)
            ) {
                "provider implementation must not contain Task 12 session or onboarding behavior"
            } else {
                null
            }

        when {
            localFactoryImport != null && ownedFile.modulePath != ":app" ->
                "LocalMetroViewModelFactory may only be provided by app composition"

            ownedFile.modulePath == ":app" &&
                ownedFile.sourceSet == "main" &&
                imports.any { importName -> matchesPrefix(importName, "io.morgan.mio.core.testing") } ->
                "production app cannot import :core:testing"

            providerModuleIssue != null -> providerModuleIssue
            else -> null
        }
    }

    if (viewModelNames.isEmpty() && featureFiles.any { ownedFile -> ownedFile.file.text.contains("ViewModel") }) {
        throw AssertionError("ViewModel presentation convention: feature ViewModel text was not parsed as a class")
    }
}

private fun containsForbiddenPresentationFlowType(
    ownedFile: OwnedKotlinFile,
    propertyText: String,
): Boolean {
    val forbiddenImportedType =
        ownedFile.file.imports
            .filter { import ->
                matchesAnyPrefix(
                    import.name,
                    listOf(
                        "io.morgan.mio.data",
                        "io.morgan.mio.data.provider",
                        "androidx.room",
                        "android.net.Uri",
                    ),
                )
            }.map { import -> import.name.substringAfterLast('.') }
            .any { typeName -> Regex("\\b${Regex.escape(typeName)}\\b").containsMatchIn(propertyText) }
    val forbiddenName =
        Regex(
            "\\b(?:AccountSessionState|AccountId|ConnectedProvider|Provider[A-Za-z]*|[A-Za-z]*Repository|[A-Za-z]*DataSource|[A-Za-z]*Dao|[A-Za-z]*Entity|[A-Za-z]*Dto|NavKey|NavBackStack|NavigationState|BackStack|Uri)\\b",
        ).containsMatchIn(propertyText)
    return forbiddenImportedType || forbiddenName
}

private fun containsForbiddenScreenBody(text: String): Boolean =
    Regex(
        "\\b(?:ViewModel|Repository|DataSource|MioNavigator|NavKey|NavBackStack|NavigationState|BackStack|LocalUriHandler|UriHandler|repeatOnLifecycle|metroViewModel|collectAsStateWithLifecycle|AccountSessionState|ConnectedProvider|AccountId)\\b|\\b(?:openUri|collectAsStateWithLifecycle)\\s*\\(",
    ).containsMatchIn(text)

private val architecturalRoleSuffixes =
    listOf(
        "Repository",
        "UseCase",
        "ViewModel",
        "UiState",
        "UiEffect",
        "UiMapper",
    )

private fun topLevelArchitecturalDeclarations(file: KoFileDeclaration): List<KoClassAndInterfaceAndObjectDeclaration> =
    file
        .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
        .filter { declaration -> declaration.isTopLevel }

private fun topLevelPublicDeclarations(file: KoFileDeclaration): List<KoClassAndInterfaceAndObjectDeclaration> =
    topLevelArchitecturalDeclarations(file).filter(::isPublicDeclaration)

private fun topLevelPublicClasses(file: KoFileDeclaration): List<KoClassDeclaration> =
    file
        .classes(includeNested = false, includeLocal = false)
        .filter { declaration -> declaration.isTopLevel && isPublicDeclaration(declaration) }

private fun topLevelPublicFunctions(file: KoFileDeclaration): List<KoFunctionDeclaration> =
    file
        .functions(includeNested = false, includeLocal = false)
        .filter { declaration -> declaration.isTopLevel && isPublicDeclaration(declaration) }

/**
 * Task 11 intentionally aggregates exactly these two entities with PendingOperationEntity.
 * Keep the exception constrained by both declaration and filename; other entities still need
 * their own matching files.
 */
private fun isPendingOperationEntityAggregation(
    declarationName: String,
    actualFileName: String,
): Boolean =
    actualFileName == "PendingOperationEntity.kt" &&
        declarationName in
        setOf(
            "PendingOperationGroupEntity",
            "PendingOperationDependencyEntity",
        )

private fun isPublicDeclaration(declaration: Any): Boolean =
    (declaration as? KoVisibilityModifierProvider)?.hasPublicOrDefaultModifier == true

private fun fileName(path: String): String = path.replace('\\', '/').substringAfterLast('/')

private fun hasModifierAsFirstOptionalParameter(function: KoFunctionDeclaration): Boolean {
    val firstOptionalParameter =
        function.parameters.firstOrNull { parameter ->
            parameter.defaultValue
                .orEmpty()
                .isNotBlank()
        }
    return firstOptionalParameter?.name == "modifier" &&
        firstOptionalParameter.type.name == "Modifier" &&
        firstOptionalParameter.defaultValue == "Modifier"
}

private fun countCall(
    text: String,
    functionName: String,
): Int = Regex("""\b${Regex.escape(functionName)}\s*\(""").findAll(text).count()

private fun containsVisualLayout(text: String): Boolean =
    Regex("""\b(?:Text|Row|Column|Box|Surface|Image|Button|LazyColumn|LazyRow)\s*\(""").containsMatchIn(text)

private fun containsForbiddenScreenParameter(function: KoFunctionDeclaration): Boolean =
    function.parameters.any { parameter ->
        Regex(
            """(?:ViewModel|Repository|DataSource|MioNavigator|NavKey|NavBackStack|NavigationState|BackStack)""",
        ).containsMatchIn(parameter.type.text) ||
            parameter.name in
            setOf("navigator", "navigationState", "backStack", "repository", "dataSource", "viewModel")
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
        val isTaskNineteenProgressRepository =
            ownedFile.modulePath == ":data:progress" &&
                fileName(ownedFile.file.path) == "DefaultProgressRepository.kt"
        val exposedStorageType =
            if (isRepository &&
                !isTaskNineteenProgressRepository
            ) {
                publicStorageTypePattern.find(source)?.value
            } else {
                null
            }

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
            val persistedAccountBoundary =
                ownedFile.modulePath == ":data:sync" &&
                    fileName(ownedFile.file.path) == "RoomSyncAccountLocalDataSource.kt"
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
                mechanisms.size > 1 && !persistedAccountBoundary ->
                    "data source mixes mechanisms ${mechanisms.sorted()}"
                repositoryImport != null -> "data source cannot depend on repository '$repositoryImport'"
                otherDataSourceImport != null ->
                    "data source cannot depend on another data source '$otherDataSourceImport'"

                else -> null
            }
        }
    }
}

internal fun enforcePendingOperationBoundaries(files: List<OwnedKotlinFile>) {
    val mutationModules = setOf(":data:collection", ":data:progress")
    val forbiddenSyncImports =
        listOf(
            "io.morgan.mio.data.sync.local",
            "io.morgan.mio.data.sync.mapper",
            "io.morgan.mio.data.sync.delivery",
            "io.morgan.mio.data.sync.reconciliation",
            "io.morgan.mio.sync.work",
            "androidx.work",
        )
    val writerDeclarations =
        files.filter { ownedFile ->
            ownedFile.file.declarations(true, true).any { declaration ->
                (declaration as? KoNameProvider)?.name == "PendingOperationWriter"
            }
        }

    assertNoViolations("Pending operation boundaries", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { import -> import.name }
        val syncImport =
            if (ownedFile.modulePath in mutationModules) {
                imports.firstOrNull { importName ->
                    matchesPrefix(importName, "io.morgan.mio.data.sync") &&
                        importName != "io.morgan.mio.data.sync.PendingOperationWriter"
                }
            } else {
                null
            }
        val forbiddenMutationImport =
            if (ownedFile.modulePath in mutationModules) {
                imports.firstOrNull { importName -> matchesAnyPrefix(importName, forbiddenSyncImports) }
            } else {
                null
            }
        val forbiddenSyncOwnerImport =
            if (ownedFile.modulePath == ":data:sync") {
                imports.firstOrNull { importName ->
                    matchesAnyPrefix(
                        importName,
                        listOf(
                            "io.morgan.mio.data.collection",
                            "io.morgan.mio.data.progress",
                            "io.morgan.mio.sync.work",
                            "androidx.work",
                        ),
                    )
                }
            } else {
                null
            }
        val localDataSourceDeclaration =
            ownedFile.file.classes(true, true).firstOrNull { declaration ->
                declaration.name.endsWith("PendingOperationLocalDataSource")
            }
        val writerDeclaration =
            ownedFile.file.declarations(true, true).firstOrNull { declaration ->
                (declaration as? KoNameProvider)?.name == "PendingOperationWriter"
            }
        val writerIsInterface =
            ownedFile.file.interfaces(true).any { declaration -> declaration.name == "PendingOperationWriter" }
        val writerPublicApiIssue =
            if (writerDeclaration != null) {
                val forbiddenText =
                    listOf(
                        "androidx.room",
                        "androidx.work",
                        "Room",
                        "Worker",
                        "Provider",
                        "Dao",
                        "Entity",
                        "LocalDataSource",
                        "SyncRepository",
                    ).firstOrNull { fragment -> ownedFile.file.text.contains(fragment) }
                when {
                    ownedFile.modulePath != ":data:sync" ->
                        "PendingOperationWriter must be declared only in :data:sync"

                    !writerIsInterface -> "PendingOperationWriter must be an interface"
                    forbiddenText != null ->
                        "PendingOperationWriter public API mentions forbidden type text '$forbiddenText'"

                    else -> null
                }
            } else {
                null
            }

        when {
            syncImport != null ->
                "mutation module may import only PendingOperationWriter from :data:sync; found '$syncImport'"

            forbiddenMutationImport != null ->
                "mutation module imported synchronization delivery internals through '$forbiddenMutationImport'"

            forbiddenSyncOwnerImport != null ->
                ":data:sync cannot import mutation data or Worker code '$forbiddenSyncOwnerImport'"

            localDataSourceDeclaration != null && ownedFile.modulePath != ":data:sync" ->
                "pending-operation local data source '${localDataSourceDeclaration.name}' must live in :data:sync"

            writerPublicApiIssue != null -> writerPublicApiIssue
            else -> null
        }
    }

    if (writerDeclarations.any { ownedFile -> ownedFile.modulePath != ":data:sync" }) {
        throw AssertionError(
            "Pending operation boundaries: PendingOperationWriter declaration escaped :data:sync",
        )
    }
}

internal fun enforceSynchronizationBoundaries(files: List<OwnedKotlinFile>) {
    assertNoViolations("Synchronization boundaries", files) { ownedFile ->
        val imports = ownedFile.file.imports.map { import -> import.name }
        val isWorker = ownedFile.file.classes(true, true).any { declaration -> declaration.name.endsWith("Worker") }
        val workerImport =
            if (ownedFile.modulePath == ":sync:work") {
                imports.firstOrNull { import ->
                    matchesAnyPrefix(
                        import,
                        listOf(
                            "androidx.room",
                            "io.morgan.mio.core.database",
                            "io.morgan.mio.data.provider",
                            "io.morgan.mio.data.sync",
                            "retrofit2",
                            "okhttp3",
                        ),
                    )
                }
            } else {
                null
            }
        val workerPolicy =
            if (ownedFile.modulePath == ":sync:work" && isWorker) {
                listOf(
                    Regex("\\b(?:remote|current|candidate)\\.(?:progress|status)\\b"),
                    Regex("\\bPendingOperation(?:Group|Kind|State)?\\b"),
                    Regex("\\bReconcilePendingOperationUseCase\\b"),
                ).firstOrNull { pattern -> pattern.containsMatchIn(ownedFile.file.text) }?.pattern
            } else {
                null
            }
        val dataWorkImport =
            if (ownedFile.modulePath.startsWith(":data:")) {
                imports.firstOrNull { import ->
                    matchesAnyPrefix(import, listOf("io.morgan.mio.sync.work", "androidx.work"))
                }
            } else {
                null
            }
        val dataMutationImport =
            if (ownedFile.modulePath == ":data:sync") {
                imports.firstOrNull { import ->
                    matchesAnyPrefix(import, listOf("io.morgan.mio.data.progress", "io.morgan.mio.data.collection"))
                }
            } else {
                null
            }
        val workProviderImport =
            if (ownedFile.modulePath == ":sync:work") {
                imports.firstOrNull { import ->
                    matchesAnyPrefix(
                        import,
                        listOf("io.morgan.mio.data.provider", "io.morgan.mio.data.sync", "io.morgan.mio.core.database"),
                    )
                }
            } else {
                null
            }
        val schedulerAndroidImport =
            if (ownedFile.modulePath.startsWith(":domain:") && ownedFile.file.text.contains("Scheduler")) {
                imports.firstOrNull { import ->
                    matchesAnyPrefix(import, listOf("android", "androidx", "retrofit2", "okhttp3"))
                }
            } else {
                null
            }
        val misplacedSynchronizationDeclaration =
            ownedFile.file
                .declarations(true, true)
                .firstOrNull { declaration ->
                    (declaration as? KoNameProvider)?.name in
                        setOf(
                            "SyncRepository",
                            "SynchronizeAccountUseCase",
                            "ReconcilePendingOperationUseCase",
                            "ProjectSyncEntryUseCase",
                            "PlanOperationUndoUseCase",
                            "SyncWorkScheduler",
                        ) &&
                        ownedFile.modulePath != ":domain:sync"
                }?.let { declaration -> (declaration as KoNameProvider).name }

        when {
            workerImport != null ->
                "Worker may depend only on domain session/result ports; found '$workerImport'"
            workerPolicy != null ->
                "Worker must delegate reconciliation policy to domain/data ports; found '$workerPolicy'"
            dataWorkImport != null ->
                "data modules cannot depend on WorkManager implementation '$dataWorkImport'"
            dataMutationImport != null ->
                "sync data cannot depend on mutation data '$dataMutationImport'"
            workProviderImport != null ->
                "sync:work cannot depend on provider/database implementation '$workProviderImport'"
            schedulerAndroidImport != null ->
                "domain synchronization scheduling cannot import platform type '$schedulerAndroidImport'"
            misplacedSynchronizationDeclaration != null ->
                "synchronization declaration '$misplacedSynchronizationDeclaration' must live in :domain:sync"
            else -> null
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

private fun isCredentialBoundaryFile(ownedFile: OwnedKotlinFile): Boolean =
    ownedFile.modulePath == ":data:account" &&
        ownedFile.file.packagee?.name == "io.morgan.mio.data.account.credentials"

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
