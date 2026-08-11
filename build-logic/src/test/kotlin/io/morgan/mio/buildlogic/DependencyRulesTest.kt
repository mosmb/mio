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
            13,
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
        assertTrue(violations(":data:progress", "implementation", ":data:sync").isEmpty())
        assertTrue(violations(":data:sync", "implementation", ":data:progress").isNotEmpty())
        assertTrue(violations(":data:progress", "implementation", ":data:provider:api").isEmpty())
        assertTrue(violations(":data:progress", "implementation", ":data:provider:anilist").isNotEmpty())
        assertTrue(violations(":domain:model", "implementation", ":core:common").isNotEmpty())
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
        listOf(":data:account", ":feature:today:impl", ":core:common", ":data:provider:api").forEach {
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
