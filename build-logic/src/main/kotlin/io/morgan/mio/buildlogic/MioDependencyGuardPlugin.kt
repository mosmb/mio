package io.morgan.mio.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

public abstract class MioDependencyGuardExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        public val violations: ListProperty<String> = objects.listProperty(String::class.java)
    }

public abstract class ValidateMioDependenciesTask : DefaultTask() {
    @get:Input
    public abstract val violations: ListProperty<String>

    @TaskAction
    public fun validate() {
        val messages = violations.get()
        if (messages.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Mio dependency guard rejected declarations:")
                    messages.forEach(::appendLine)
                },
            )
        }
    }
}

public class MioDependencyGuardPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<MioDependencyGuardExtension>("mioDependencyGuard")
        val refresh = {
            extension.violations.set(project.collectDependencyViolations())
        }

        project.configurations.configureEach {
            dependencies.whenObjectAdded {
                refresh()
            }
            dependencyConstraints.whenObjectAdded {
                refresh()
            }
            refresh()

            incoming.beforeResolve {
                val messages = project.collectDependencyViolations()
                if (messages.isNotEmpty()) {
                    throw GradleException(
                        buildString {
                            appendLine("Mio dependency guard rejected declarations before resolution:")
                            messages.forEach(::appendLine)
                        },
                    )
                }
            }
        }

        project.tasks.register<ValidateMioDependenciesTask>("validateMioDependencies") {
            group = "verification"
            description = "Validates this module's direct project dependencies and external selectors."
            violations.convention(extension.violations)
        }
    }
}

internal fun Project.collectDependencyViolations(): List<String> =
    configurations
        .asSequence()
        .filter { configuration ->
            // AGP owns these internal alpha dependencies; they are not project declarations.
            configuration.isCanBeDeclared &&
                !configuration.name.startsWith("unified-test-platform-")
        }.flatMap { configuration ->
            val dependencies =
                buildList {
                    addAll(configuration.dependencies.mapNotNull { it.toDeclaredDependency() })
                    addAll(configuration.dependencyConstraints.map { it.toDeclaredDependency() })
                }
            DependencyRules
                .violationsFor(path, configuration.name, dependencies)
                .map { violation ->
                    RenderedViolation(
                        configuration = configuration.name,
                        dependency = violation.dependency,
                        reason = violation.reason,
                    )
                }
        }.sortedWith(compareBy<RenderedViolation> { it.configuration }.thenBy { it.dependency }.thenBy { it.reason })
        .map { violation -> "$path | ${violation.configuration} | ${violation.dependency} | ${violation.reason}" }
        .toList()

private data class RenderedViolation(
    val configuration: String,
    val dependency: String,
    val reason: String,
)

private fun org.gradle.api.artifacts.Dependency.toDeclaredDependency(): DeclaredDependency? =
    when (this) {
        is ProjectDependency ->
            DeclaredDependency(
                identifier = path,
                projectPath = path,
            )

        is ExternalModuleDependency ->
            DeclaredDependency(
                identifier = listOfNotNull(group, name).joinToString(":"),
                group = group,
                name = name,
                version = version,
                requiredVersion = versionConstraint.requiredVersion,
                preferredVersion = versionConstraint.preferredVersion,
                strictVersion = versionConstraint.strictVersion,
            )

        else -> null
    }

private fun DependencyConstraint.toDeclaredDependency(): DeclaredDependency =
    DeclaredDependency(
        identifier = "$group:$name (constraint)",
        group = group,
        name = name,
        requiredVersion = versionConstraint.requiredVersion,
        preferredVersion = versionConstraint.preferredVersion,
        strictVersion = versionConstraint.strictVersion,
    )
