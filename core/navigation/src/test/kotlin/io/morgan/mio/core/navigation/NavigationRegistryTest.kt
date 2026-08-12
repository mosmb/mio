package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class NavigationRegistryTest {
    @Test
    fun unorderedTopLevelContributionsUseTheDocumentedOrder() {
        val registry = registry()

        assertEquals(
            listOf(TopLevelSlot.TODAY, TopLevelSlot.LIBRARY, TopLevelSlot.DISCOVER, TopLevelSlot.YOU),
            registry.topLevelDestinations.map(TopLevelDestination::slot),
        )
    }

    @Test
    fun oneBuilderCanRegisterMultipleKeyClasses() {
        val builder =
            builder(
                RegistryOnboardingTestKey::class,
                RegistryTodayTestKey::class,
                RegistryLibraryTestKey::class,
                RegistryDiscoverTestKey::class,
                RegistryYouTestKey::class,
            ) {
                entry<RegistryOnboardingTestKey> { }
                entry<RegistryTodayTestKey> { }
                entry<RegistryLibraryTestKey> { }
                entry<RegistryDiscoverTestKey> { }
                entry<RegistryYouTestKey> { }
            }

        val registry = registry(entryBuilders = setOf(builder))

        assertEquals(5, builder.destinationKeyTypes.size)
        assertNotNull(registry.entryProvider(NoOpNavigator)(RegistryOnboardingTestKey))
        assertNotNull(registry.entryProvider(NoOpNavigator)(RegistryYouTestKey))
    }

    @Test
    fun duplicateSlotsFailWithTheSlotInTheMessage() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                registry(
                    topLevelDestinations =
                        setOf(
                            TopLevelDestination(TopLevelSlot.TODAY, RegistryTodayTestKey),
                            TopLevelDestination(TopLevelSlot.LIBRARY, RegistryLibraryTestKey),
                            TopLevelDestination(TopLevelSlot.LIBRARY, RegistryDiscoverTestKey),
                            TopLevelDestination(TopLevelSlot.DISCOVER, RegistryDiscoverTestKey),
                            TopLevelDestination(TopLevelSlot.YOU, RegistryYouTestKey),
                        ),
                )
            }

        assertTrue(failure.message.orEmpty().contains("LIBRARY"))
    }

    @Test
    fun missingSlotFails() {
        assertThrows(IllegalArgumentException::class.java) {
            registry(
                topLevelDestinations =
                    setOf(
                        TopLevelDestination(TopLevelSlot.TODAY, RegistryTodayTestKey),
                        TopLevelDestination(TopLevelSlot.LIBRARY, RegistryLibraryTestKey),
                        TopLevelDestination(TopLevelSlot.DISCOVER, RegistryDiscoverTestKey),
                    ),
            )
        }
    }

    @Test
    fun duplicateKeyClassRegistrationAcrossBuildersFails() {
        assertThrows(IllegalArgumentException::class.java) {
            registry(
                entryBuilders =
                    setOf(
                        builder(RegistryOnboardingTestKey::class) {
                            entry<RegistryOnboardingTestKey> { }
                        },
                        builder(RegistryOnboardingTestKey::class) {
                            entry<RegistryOnboardingTestKey> { }
                        },
                        builder(RegistryTodayTestKey::class) { entry<RegistryTodayTestKey> { } },
                        builder(RegistryLibraryTestKey::class) { entry<RegistryLibraryTestKey> { } },
                        builder(RegistryDiscoverTestKey::class) { entry<RegistryDiscoverTestKey> { } },
                        builder(RegistryYouTestKey::class) { entry<RegistryYouTestKey> { } },
                    ),
            )
        }
    }

    @Test
    fun missingOnboardingOrTopLevelBuilderFails() {
        assertThrows(IllegalArgumentException::class.java) {
            registry(
                entryBuilders =
                    setOf(
                        builder(RegistryTodayTestKey::class) { entry<RegistryTodayTestKey> { } },
                        builder(RegistryLibraryTestKey::class) { entry<RegistryLibraryTestKey> { } },
                        builder(RegistryDiscoverTestKey::class) { entry<RegistryDiscoverTestKey> { } },
                        builder(RegistryYouTestKey::class) { entry<RegistryYouTestKey> { } },
                    ),
            )
        }
    }

    @Test
    fun providerResolvesDeclaredKeysAndRejectsUnknownKeys() {
        val provider = registry().entryProvider(NoOpNavigator)

        listOf(
            RegistryOnboardingTestKey,
            RegistryTodayTestKey,
            RegistryLibraryTestKey,
            RegistryDiscoverTestKey,
            RegistryYouTestKey,
        ).forEach { key ->
            assertNotNull(provider(key))
        }
        assertThrows(Throwable::class.java) { provider(RegistryUnknownTestKey) }
    }

    private fun registry(
        topLevelDestinations: Set<TopLevelDestination> =
            setOf(
                TopLevelDestination(TopLevelSlot.YOU, RegistryYouTestKey),
                TopLevelDestination(TopLevelSlot.DISCOVER, RegistryDiscoverTestKey),
                TopLevelDestination(TopLevelSlot.LIBRARY, RegistryLibraryTestKey),
                TopLevelDestination(TopLevelSlot.TODAY, RegistryTodayTestKey),
            ),
        entryBuilders: Set<NavEntryBuilder> =
            setOf(
                builder(RegistryOnboardingTestKey::class) { entry<RegistryOnboardingTestKey> { } },
                builder(RegistryTodayTestKey::class) { entry<RegistryTodayTestKey> { } },
                builder(RegistryLibraryTestKey::class) { entry<RegistryLibraryTestKey> { } },
                builder(RegistryDiscoverTestKey::class) { entry<RegistryDiscoverTestKey> { } },
                builder(RegistryYouTestKey::class) { entry<RegistryYouTestKey> { } },
            ),
    ): NavigationRegistry =
        NavigationRegistry(
            onboardingDestination = OnboardingDestination(RegistryOnboardingTestKey),
            topLevelDestinations = topLevelDestinations,
            entryBuilders = entryBuilders,
        )

    private fun builder(
        vararg keyTypes: KClass<out NavKey>,
        registrations: EntryProviderScope<NavKey>.() -> Unit,
    ): NavEntryBuilder =
        TestEntryBuilder(
            destinationKeyTypes = keyTypes.toSet(),
            registrations = registrations,
        )

    private class TestEntryBuilder(
        override val destinationKeyTypes: Set<KClass<out NavKey>>,
        private val registrations: EntryProviderScope<NavKey>.() -> Unit,
    ) : NavEntryBuilder {
        override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator): Unit = registrations()
    }

    private object NoOpNavigator : MioNavigator {
        override fun navigate(destination: NavKey): Unit = Unit

        override fun goBack(): Boolean = false
    }
}

@Serializable
private data object RegistryOnboardingTestKey : NavKey

@Serializable
private data object RegistryTodayTestKey : NavKey

@Serializable
private data object RegistryLibraryTestKey : NavKey

@Serializable
private data object RegistryDiscoverTestKey : NavKey

@Serializable
private data object RegistryYouTestKey : NavKey

@Serializable
private data object RegistryUnknownTestKey : NavKey
