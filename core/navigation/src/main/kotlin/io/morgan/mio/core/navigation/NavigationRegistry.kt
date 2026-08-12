package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import java.util.Collections
import kotlin.reflect.KClass

public class NavigationRegistry public constructor(
    onboardingDestination: OnboardingDestination,
    topLevelDestinations: Set<TopLevelDestination>,
    entryBuilders: Set<NavEntryBuilder>,
) {
    private val entryBuilders: List<NavEntryBuilder> = Collections.unmodifiableList(entryBuilders.toList())

    public val onboardingDestination: OnboardingDestination = onboardingDestination
    public val topLevelDestinations: List<TopLevelDestination>

    init {
        val destinationsBySlot = topLevelDestinations.groupBy(TopLevelDestination::slot)
        destinationsBySlot
            .filterValues { destinations -> destinations.size > 1 }
            .keys
            .forEach { slot ->
                require(false) { "Duplicate top-level destination for slot $slot" }
            }

        val missingSlots = TopLevelSlot.entries.filterNot(destinationsBySlot::containsKey)
        require(missingSlots.isEmpty()) {
            "Missing top-level destination for slot ${missingSlots.first()}"
        }

        this.topLevelDestinations =
            Collections.unmodifiableList(
                TopLevelSlot.entries.map { slot ->
                    destinationsBySlot.getValue(slot).single()
                },
            )

        val rootKeyTypes: List<KClass<out NavKey>> =
            listOf(onboardingDestination.key::class) +
                this.topLevelDestinations.map(TopLevelDestination::key).map { key -> key::class }
        require(rootKeyTypes.size == rootKeyTypes.toSet().size) {
            "Onboarding and top-level destination key types must be distinct: $rootKeyTypes"
        }

        entryBuilders.forEach { builder ->
            require(builder.destinationKeyTypes.isNotEmpty()) {
                "Navigation entry builder ${builder::class.qualifiedName} must register at least one key type"
            }
        }

        val buildersByKeyType =
            entryBuilders
                .flatMap { builder ->
                    builder.destinationKeyTypes.map { keyType -> keyType to builder }
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        buildersByKeyType
            .filterValues { builders -> builders.size > 1 }
            .forEach { (keyType, builders) ->
                require(false) {
                    "Duplicate navigation entry builder registration for key type ${keyType.qualifiedName}: " +
                        builders.joinToString { builder -> builder::class.qualifiedName.orEmpty() }
                }
            }

        val registeredKeyTypes = buildersByKeyType.keys
        val requiredKeyTypes = rootKeyTypes.toSet()
        val missingKeyTypes = requiredKeyTypes - registeredKeyTypes
        require(missingKeyTypes.isEmpty()) {
            "Missing navigation entry builder registration for key type ${missingKeyTypes.first().qualifiedName}"
        }
    }

    public fun entryProvider(navigator: MioNavigator): (NavKey) -> NavEntry<NavKey> =
        entryProvider {
            entryBuilders.forEach { builder ->
                with(builder) { build(navigator) }
            }
        }
}
