package io.morgan.mio.buildlogic

/** A dependency declaration reduced to the parts needed by the verification rules. */
public data class DeclaredDependency(
    val identifier: String,
    val projectPath: String? = null,
    val group: String? = null,
    val name: String? = null,
    val version: String? = null,
    val requiredVersion: String? = null,
    val preferredVersion: String? = null,
    val strictVersion: String? = null,
)

public data class DependencyRuleViolation(
    val dependency: String,
    val reason: String,
)

public object DependencyRules {
    public val productionProjectDependencies: Map<String, Set<String>> =
        mapOf(
            ":app" to
                setOf(
                    ":core:common",
                    ":core:database",
                    ":core:datastore",
                    ":core:network",
                    ":core:logging",
                    ":core:ui",
                    ":data:provider:api",
                    ":domain:model",
                    ":domain:account",
                    ":domain:media",
                    ":domain:collection",
                    ":domain:preferences",
                    ":feature:onboarding:impl",
                    ":feature:today:impl",
                    ":feature:library:impl",
                    ":feature:discover:impl",
                    ":feature:you:impl",
                    ":feature:media:impl",
                    ":feature:progress:impl",
                    ":data:account",
                    ":data:media",
                    ":data:collection",
                    ":data:progress",
                    ":data:sync",
                    ":data:preferences",
                    ":data:provider:anilist",
                    ":data:provider:myanimelist",
                    ":core:navigation",
                    ":core:designsystem",
                    ":sync:work",
                ),
            ":feature:onboarding:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:today:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:library:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:discover:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:you:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:media:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:progress:api" to setOf(":core:navigation", ":domain:model"),
            ":feature:onboarding:impl" to
                setOf(
                    ":feature:onboarding:api",
                    ":core:common",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:account",
                    ":domain:sync",
                    ":domain:model",
                ),
            ":feature:today:impl" to
                setOf(
                    ":feature:today:api",
                    ":core:common",
                    ":feature:media:api",
                    ":feature:library:api",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:queue",
                    ":domain:media",
                    ":domain:progress",
                    ":domain:collection",
                    ":domain:preferences",
                    ":domain:model",
                ),
            ":feature:library:impl" to
                setOf(
                    ":feature:library:api",
                    ":core:common",
                    ":feature:media:api",
                    ":feature:progress:api",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:collection",
                    ":domain:progress",
                    ":domain:preferences",
                    ":domain:model",
                ),
            ":feature:discover:impl" to
                setOf(
                    ":feature:discover:api",
                    ":core:common",
                    ":feature:media:api",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:media",
                    ":domain:collection",
                    ":domain:model",
                ),
            ":feature:you:impl" to
                setOf(
                    ":feature:you:api",
                    ":core:common",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:account",
                    ":domain:sync",
                    ":domain:preferences",
                    ":domain:model",
                ),
            ":feature:media:impl" to
                setOf(
                    ":feature:media:api",
                    ":feature:progress:api",
                    ":core:common",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:media",
                    ":domain:collection",
                    ":domain:progress",
                    ":domain:model",
                ),
            ":feature:progress:impl" to
                setOf(
                    ":feature:progress:api",
                    ":core:designsystem",
                    ":core:ui",
                    ":core:navigation",
                    ":domain:progress",
                    ":domain:collection",
                    ":domain:sync",
                    ":domain:model",
                ),
            ":data:account" to
                setOf(
                    ":domain:account",
                    ":domain:model",
                    ":core:database",
                    ":core:common",
                    ":data:provider:api",
                ),
            ":data:media" to
                setOf(
                    ":domain:media",
                    ":domain:model",
                    ":core:database",
                    ":core:common",
                    ":data:provider:api",
                ),
            ":data:collection" to
                setOf(
                    ":domain:collection",
                    ":domain:model",
                    ":core:database",
                    ":core:common",
                    ":data:provider:api",
                    ":data:sync",
                ),
            ":data:progress" to
                setOf(
                    ":domain:progress",
                    ":domain:collection",
                    ":domain:model",
                    ":core:database",
                    ":data:provider:api",
                    ":data:sync",
                ),
            ":data:sync" to
                setOf(
                    ":domain:sync",
                    ":domain:model",
                    ":core:database",
                    ":core:common",
                    ":data:provider:api",
                ),
            ":data:preferences" to
                setOf(
                    ":domain:preferences",
                    ":domain:model",
                    ":core:datastore",
                    ":core:common",
                ),
            ":data:provider:anilist" to
                setOf(
                    ":data:provider:api",
                    ":core:network",
                ),
            ":data:provider:myanimelist" to setOf(":data:provider:api", ":core:network"),
            ":core:ui" to setOf(":core:designsystem"),
            ":sync:work" to setOf(":domain:sync", ":domain:model", ":core:common"),
            ":domain:model" to emptySet(),
            ":domain:account" to setOf(":domain:model"),
            ":domain:media" to setOf(":domain:model"),
            ":domain:collection" to setOf(":domain:model"),
            ":domain:progress" to emptySet(),
            ":domain:queue" to setOf(":domain:media", ":domain:collection", ":domain:model"),
            ":domain:sync" to setOf(":domain:model", ":domain:collection"),
            ":domain:preferences" to setOf(":domain:model"),
            ":data:provider:api" to emptySet(),
            ":core:common" to emptySet(),
            ":core:testing" to
                setOf(
                    ":domain:model",
                    ":domain:account",
                    ":domain:media",
                    ":domain:collection",
                    ":domain:preferences",
                    ":data:provider:api",
                ),
            ":architecture-tests" to emptySet(),
            ":core:database" to setOf(":core:common"),
            ":core:datastore" to setOf(":core:common"),
            ":core:network" to setOf(":core:common", ":core:logging"),
            ":core:logging" to setOf(":core:common"),
            ":core:designsystem" to emptySet(),
            ":core:navigation" to emptySet(),
        )

    public val forbiddenExternalGroups: List<String> =
        listOf(
            "androidx.navigation",
            "androidx.paging",
            "com.google.dagger",
            "io.insert-koin",
            "org.koin",
            "com.google.code.gson",
            "com.squareup.moshi",
            "com.fasterxml.jackson",
            "io.reactivex",
            "io.reactivex.rxjava2",
            "io.reactivex.rxjava3",
        )

    public fun isTestConfiguration(configurationName: String): Boolean =
        configurationName.contains("test", ignoreCase = true) ||
            configurationName.contains("androidTest", ignoreCase = true) ||
            configurationName.contains("testFixtures", ignoreCase = true)

    public fun violationsFor(
        owningProjectPath: String,
        configurationName: String,
        dependencies: Iterable<DeclaredDependency>,
    ): List<DependencyRuleViolation> {
        val declarations = dependencies.toList()
        val violations =
            buildList {
                if (!isTestConfiguration(configurationName)) {
                    val allowed = productionProjectDependencies[owningProjectPath]
                    if (allowed == null) {
                        add(
                            DependencyRuleViolation(
                                dependency = owningProjectPath,
                                reason = "owning project is not in the production dependency matrix",
                            ),
                        )
                    } else {
                        declarations
                            .filter { it.projectPath != null }
                            .map { it.projectPath!! }
                            .distinct()
                            .sorted()
                            .filterNot(allowed::contains)
                            .forEach { dependencyPath ->
                                add(
                                    DependencyRuleViolation(
                                        dependency = dependencyPath,
                                        reason =
                                            "project dependency is not allowed by the production dependency matrix",
                                    ),
                                )
                            }
                    }
                }

                declarations
                    .filter { it.projectPath == null }
                    .sortedBy { it.identifier }
                    .forEach { dependency ->
                        addAll(externalViolations(dependency))
                    }
            }

        return violations
    }

    private fun externalViolations(dependency: DeclaredDependency): List<DependencyRuleViolation> =
        buildList {
            val group = dependency.group
            if (group != null && forbiddenExternalGroups.any { group == it || group.startsWith("$it.") }) {
                add(
                    DependencyRuleViolation(
                        dependency = dependency.identifier,
                        reason = "external dependency group '$group' is forbidden",
                    ),
                )
            }

            dependency.versionSelectors().forEach { selector ->
                val reason = unstableVersionReason(selector)
                if (reason != null) {
                    add(
                        DependencyRuleViolation(
                            dependency = dependency.identifier,
                            reason = reason,
                        ),
                    )
                }
            }
        }

    private fun DeclaredDependency.versionSelectors(): List<String> =
        listOf(version, requiredVersion, preferredVersion, strictVersion)
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun unstableVersionReason(selector: String): String? {
        val isDynamic =
            selector == "+" ||
                selector.contains('+') ||
                selector.contains('*') ||
                selector.startsWith("latest.", ignoreCase = true) ||
                selector.startsWith('[') ||
                selector.startsWith('(') ||
                selector.contains(',')
        if (isDynamic) {
            return "version selector '$selector' is dynamic"
        }

        val isUnstable =
            selector.contains("SNAPSHOT", ignoreCase = true) ||
                selector.contains("alpha", ignoreCase = true) ||
                selector.contains("beta", ignoreCase = true) ||
                selector.contains("rc", ignoreCase = true) ||
                selector.contains("milestone", ignoreCase = true) ||
                Regex("(?i)(^|[.-])m\\d+($|[.-])").containsMatchIn(selector)
        return if (isUnstable) {
            "version selector '$selector' is not stable"
        } else {
            null
        }
    }
}
