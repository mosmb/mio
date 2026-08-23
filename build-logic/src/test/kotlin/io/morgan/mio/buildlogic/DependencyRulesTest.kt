package io.morgan.mio.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyRulesTest {
    @Test
    fun matrixContainsAllCurrentLeafModulesAndRejectsUnknownPaths() {
        assertEquals(43, DependencyRules.productionProjectDependencies.size)
        assertEquals(
            7,
            DependencyRules.productionProjectDependencies.values.count { it.isEmpty() },
        )
        assertFalse(DependencyRules.productionProjectDependencies.containsKey(":new:module"))
        assertTrue(
            DependencyRules
                .violationsFor(
                    owningProjectPath = ":new:module",
                    configurationName = "implementation",
                    dependencies = emptyList(),
                ).isNotEmpty(),
        )
    }

    @Test
    fun allowsFeatureApiAndRejectsFeatureImplementationEdges() {
        assertTrue(
            violations(":feature:today:impl", "implementation", ":feature:media:api").isEmpty(),
        )
        assertTrue(
            violations(":feature:today:impl", "implementation", ":feature:media:impl")
                .any { it.dependency == ":feature:media:impl" },
        )
        assertTrue(
            violations(":feature:today:impl", "implementation", ":data:progress")
                .any { it.dependency == ":data:progress" },
        )
    }

    @Test
    fun enforcesDataDirectionAndProviderIsolationInTheGradleMatrix() {
        assertTrue(violations(":data:collection", "implementation", ":data:sync").isEmpty())
        assertTrue(violations(":data:progress", "implementation", ":data:sync").isEmpty())
        assertTrue(violations(":data:sync", "implementation", ":data:collection").isNotEmpty())
        assertTrue(violations(":data:sync", "implementation", ":data:progress").isNotEmpty())
        assertTrue(violations(":data:sync", "implementation", ":core:common").isEmpty())
        assertTrue(violations(":data:progress", "implementation", ":data:provider:api").isEmpty())
        assertTrue(violations(":data:progress", "implementation", ":data:provider:anilist").isNotEmpty())
        assertTrue(violations(":domain:model", "implementation", ":core:common").isNotEmpty())
    }

    @Test
    fun opensOnlyTheTaskElevenSyncDomainEdges() {
        assertTrue(violations(":domain:sync", "implementation", ":domain:model").isEmpty())
        assertTrue(violations(":domain:sync", "implementation", ":domain:collection").isEmpty())
        assertTrue(violations(":domain:sync", "implementation", ":data:sync").isNotEmpty())
        assertTrue(violations(":domain:sync", "implementation", ":core:database").isNotEmpty())
    }

    @Test
    fun opensOnlyTheTaskSeventeenTodayQueueEdges() {
        listOf(":domain:media", ":domain:collection", ":domain:model").forEach { dependency ->
            assertTrue(
                violations(":domain:queue", "implementation", dependency).isEmpty(),
                "Expected :domain:queue -> $dependency to be allowed",
            )
        }
        assertTrue(
            violations(":feature:today:impl", "implementation", ":domain:media").isEmpty(),
            "Expected :feature:today:impl -> :domain:media to be allowed",
        )
        listOf(
            ":data:media",
            ":core:database",
            ":feature:today:impl",
            ":domain:queue",
        ).forEach { dependency ->
            assertTrue(
                violations(":domain:queue", "implementation", dependency).isNotEmpty(),
                "Expected :domain:queue -> $dependency to remain forbidden",
            )
        }
        assertTrue(
            violations(":domain:media", "implementation", ":domain:queue").isNotEmpty(),
            "Expected the inverse :domain:media -> :domain:queue edge to remain forbidden",
        )
    }

    @Test
    fun opensOnlyTheTaskThreeDomainEdges() {
        listOf(":domain:account", ":domain:media", ":domain:collection", ":domain:preferences").forEach {
            assertTrue(violations(it, "implementation", ":domain:model").isEmpty())
        }
        assertTrue(violations(":domain:collection", "implementation", ":domain:media").isNotEmpty())

        listOf(
            ":domain:model",
            ":domain:account",
            ":domain:media",
            ":domain:collection",
            ":domain:preferences",
        ).forEach {
            assertTrue(violations(":core:testing", "implementation", it).isEmpty())
        }
        listOf(":data:account", ":feature:today:impl", ":core:common").forEach {
            assertTrue(violations(":core:testing", "implementation", it).isNotEmpty())
        }

        listOf(
            ":domain:account",
            ":domain:media",
            ":domain:collection",
            ":domain:preferences",
            ":core:testing",
        ).forEach {
            assertTrue(violations(":domain:model", "implementation", it).isNotEmpty())
        }
    }

    @Test
    fun opensOnlyTheTaskFiveTestingAndProviderEdges() {
        assertTrue(violations(":core:testing", "implementation", ":data:provider:api").isEmpty())
        assertTrue(violations(":core:testing", "implementation", ":core:network").isNotEmpty())

        listOf(":domain:model", ":core:common", ":data:media", ":data:provider:anilist").forEach {
            assertTrue(violations(":data:provider:api", "implementation", it).isNotEmpty())
        }

        listOf(
            ":data:provider:api",
            ":data:provider:anilist",
            ":data:account",
            ":domain:model",
            ":app",
        ).forEach {
            assertTrue(violations(":core:network", "implementation", it).isNotEmpty())
        }

        assertTrue(
            violations(":data:provider:anilist", "implementation", ":data:provider:api").isEmpty(),
        )
        assertTrue(
            violations(":data:provider:anilist", "implementation", ":core:network").isEmpty(),
        )
        assertTrue(
            violations(":data:provider:anilist", "implementation", ":data:provider:myanimelist").isNotEmpty(),
        )
        assertTrue(
            violations(":data:provider:myanimelist", "implementation", ":data:provider:api").isEmpty(),
        )
        assertTrue(
            violations(":data:provider:myanimelist", "implementation", ":core:network").isEmpty(),
        )
        assertTrue(
            violations(":data:provider:myanimelist", "implementation", ":data:provider:anilist").isNotEmpty(),
        )
    }

    @Test
    fun opensOnlyTheTaskSixCompositionEdges() {
        val taskSixEdges =
            mapOf(
                ":app" to
                    listOf(
                        ":core:common",
                        ":core:database",
                        ":core:datastore",
                        ":core:network",
                        ":data:provider:api",
                        ":domain:model",
                        ":domain:account",
                        ":domain:media",
                        ":domain:collection",
                        ":domain:preferences",
                    ),
                ":core:database" to listOf(":core:common"),
                ":core:datastore" to listOf(":core:common"),
                ":core:network" to listOf(":core:common"),
                ":data:account" to listOf(":core:common"),
                ":data:media" to listOf(":core:common"),
                ":data:collection" to listOf(":core:common"),
                ":data:preferences" to listOf(":core:common"),
            )

        taskSixEdges.forEach { (owner, dependencies) ->
            dependencies.forEach { dependency ->
                assertTrue(
                    violations(owner, "implementation", dependency).isEmpty(),
                    "Expected $owner -> $dependency to be allowed",
                )
            }
        }

        listOf(
            ":domain:model" to ":core:common",
            ":feature:today:impl" to ":app",
            ":data:provider:anilist" to ":data:account",
            ":core:database" to ":app",
            ":data:account" to ":data:media",
        ).forEach { (owner, dependency) ->
            assertTrue(
                violations(owner, "implementation", dependency).isNotEmpty(),
                "Expected $owner -> $dependency to remain forbidden",
            )
        }
    }

    @Test
    fun opensOnlyTheTaskSevenNavigationCompositionEdges() {
        listOf(
            ":feature:onboarding:impl",
            ":feature:today:impl",
            ":feature:library:impl",
            ":feature:discover:impl",
            ":feature:you:impl",
        ).forEach { owner ->
            assertTrue(
                violations(owner, "implementation", ":core:common").isEmpty(),
                "Expected $owner -> :core:common to be allowed",
            )
        }

        listOf(
            ":feature:media:impl",
            ":feature:progress:impl",
            ":feature:today:api",
        ).forEach { owner ->
            val violations = violations(owner, "implementation", ":core:common")
            if (owner == ":feature:media:impl") {
                assertTrue(violations.isEmpty(), "Expected task sixteen media common edge to be allowed")
            } else {
                assertTrue(violations.isNotEmpty(), "Expected $owner -> :core:common to remain forbidden")
            }
        }
        assertTrue(
            violations(":app", "implementation", ":feature:today:api").isNotEmpty(),
            "Expected :app -> :feature:today:api to remain forbidden",
        )
        assertTrue(
            violations(":feature:today:impl", "implementation", ":core:navigation").isEmpty(),
            "Expected the existing :feature:today:impl -> :core:navigation edge to remain allowed",
        )
    }

    @Test
    fun opensOnlyTheTaskTenPlatformServiceEdges() {
        assertTrue(violations(":app", "implementation", ":core:logging").isEmpty())
        assertTrue(violations(":app", "implementation", ":core:ui").isEmpty())
        assertTrue(violations(":core:logging", "implementation", ":core:common").isEmpty())
        assertTrue(violations(":core:network", "implementation", ":core:logging").isEmpty())
        assertTrue(violations(":sync:work", "implementation", ":core:common").isEmpty())

        listOf(
            ":core:logging" to ":core:network",
            ":core:ui" to ":app",
            ":feature:today:impl" to ":core:logging",
            ":data:progress" to ":sync:work",
            ":domain:model" to ":core:logging",
        ).forEach { (owner, dependency) ->
            assertTrue(
                violations(owner, "implementation", dependency).isNotEmpty(),
                "Expected $owner -> $dependency to remain forbidden",
            )
        }
    }

    @Test
    fun taskTwelveIntroducesNoProductionProjectEdge() {
        assertEquals(43, DependencyRules.productionProjectDependencies.size)
        assertTrue(
            DependencyRules
                .productionProjectDependencies
                .getValue(":feature:onboarding:impl")
                .containsAll(
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
                ),
        )
        assertTrue(
            DependencyRules
                .violationsFor(
                    owningProjectPath = ":feature:onboarding:impl",
                    configurationName = "implementation",
                    dependencies = listOf(DeclaredDependency(":app", projectPath = ":app")),
                ).isNotEmpty(),
        )
    }

    @Test
    fun testConfigurationsDoNotWidenTheProductionProjectMatrix() {
        assertTrue(
            violations(":domain:model", "testImplementation", ":data:progress").isEmpty(),
        )
        assertTrue(
            violations(":domain:model", "testImplementation", external("androidx.navigation", "navigation", "1.0.0"))
                .isNotEmpty(),
        )
    }

    @Test
    fun customDeclarableConfigurationsAreValidated() {
        assertTrue(
            violations(
                ":core:common",
                "ksp",
                external("com.google.code.gson", "gson", "2.+"),
            ).isNotEmpty(),
        )
        assertTrue(
            violations(":core:common", "annotationProcessor", ":data:progress").isNotEmpty(),
        )
    }

    @Test
    fun dependencyConstraintsAreValidated() {
        val project = ProjectBuilder.builder().withName("dependency-guard-fixture").build()
        project.configurations.create("customGuard")
        project.dependencies.constraints.add(
            "customGuard",
            "com.google.code.gson:gson:2.+",
        )

        val violations = project.collectDependencyViolations()

        assertTrue(violations.any { it.contains("com.google.code.gson:gson (constraint)") })
        assertTrue(violations.any { it.contains("external dependency group 'com.google.code.gson' is forbidden") })
        assertTrue(violations.any { it.contains("version selector '2.+' is dynamic") })
    }

    @Test
    fun forbidsEveryExternalLibraryFamilyButAllowsNavigation3() {
        DependencyRules.forbiddenExternalGroups.forEach { group ->
            assertTrue(
                violations(":core:common", "implementation", external(group, "artifact", "1.0.0"))
                    .any { it.reason.contains("forbidden") },
                "Expected $group to be rejected",
            )
        }
        assertTrue(
            violations(":core:navigation", "implementation", external("androidx.navigation3", "navigation", "1.0.0"))
                .isEmpty(),
        )
        assertTrue(
            violations(":core:navigation", "implementation", external("androidx.navigation", "navigation", "1.0.0"))
                .isNotEmpty(),
        )
    }

    @Test
    fun rejectsUnstableSelectorsAndAllowsStableOrBomManagedDependencies() {
        assertTrue(violations(":core:common", "implementation", external("com.example", "stable", "1.2.3")).isEmpty())
        assertTrue(violations(":core:common", "implementation", external("com.example", "bom-managed", null)).isEmpty())

        listOf("+", "1.+", "latest.release", "latest.integration", "[1.0,2.0)").forEach { selector ->
            assertTrue(
                violations(":core:common", "implementation", external("com.example", "artifact", selector))
                    .any { it.reason.contains("dynamic") },
                "Expected $selector to be rejected as dynamic",
            )
        }

        listOf("1.0.0-SNAPSHOT", "1.0.0-alpha01", "1.0.0-beta01", "1.0.0-rc01", "1.0.0-M1").forEach { selector ->
            assertTrue(
                violations(":core:common", "implementation", external("com.example", "artifact", selector))
                    .any { it.reason.contains("stable") },
                "Expected $selector to be rejected as unstable",
            )
        }
    }

    private fun violations(
        owner: String,
        configuration: String,
        dependency: String,
    ): List<DependencyRuleViolation> =
        violations(owner, configuration, DeclaredDependency(identifier = dependency, projectPath = dependency))

    private fun violations(
        owner: String,
        configuration: String,
        dependency: DeclaredDependency,
    ): List<DependencyRuleViolation> = DependencyRules.violationsFor(owner, configuration, listOf(dependency))

    private fun external(
        group: String,
        name: String,
        version: String?,
    ): DeclaredDependency =
        DeclaredDependency(
            identifier = "$group:$name",
            group = group,
            name = name,
            version = version,
        )
}
