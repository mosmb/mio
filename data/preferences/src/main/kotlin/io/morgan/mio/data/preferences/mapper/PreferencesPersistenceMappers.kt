package io.morgan.mio.data.preferences.mapper

import io.morgan.mio.core.datastore.AccountPreferencesRecord
import io.morgan.mio.core.datastore.DevicePreferencesRecord
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.MotionPreference

internal fun DevicePreferencesRecord.toDomain(): DevicePreferences =
    DevicePreferences(
        appearance = appearance.toDomainAppearance(),
        motion = motion.toDomainMotion(),
        hapticsEnabled = hapticsEnabled,
    )

internal fun AccountPreferencesRecord.toDomain(): AccountPreferences =
    AccountPreferences(
        accountId = AccountId(accountId),
        libraryMediaFilter = libraryMediaFilter.toDomainLibraryMediaFilter(),
    )

internal fun AppearancePreference.toStoredValue(): String =
    when (this) {
        AppearancePreference.SYSTEM -> "system"
        AppearancePreference.LIGHT -> "light"
        AppearancePreference.DARK -> "dark"
    }

internal fun MotionPreference.toStoredValue(): String =
    when (this) {
        MotionPreference.FOLLOW_SYSTEM -> "follow_system"
        MotionPreference.REDUCED -> "reduced"
    }

internal fun LibraryMediaFilter.toStoredValue(): String =
    when (this) {
        LibraryMediaFilter.ALL -> "all"
        LibraryMediaFilter.ANIME -> "anime"
        LibraryMediaFilter.MANGA -> "manga"
    }

private fun String.toDomainAppearance(): AppearancePreference =
    when (this) {
        "system" -> AppearancePreference.SYSTEM
        "light" -> AppearancePreference.LIGHT
        "dark" -> AppearancePreference.DARK
        else -> AppearancePreference.SYSTEM
    }

private fun String.toDomainMotion(): MotionPreference =
    when (this) {
        "follow_system" -> MotionPreference.FOLLOW_SYSTEM
        "reduced" -> MotionPreference.REDUCED
        else -> MotionPreference.FOLLOW_SYSTEM
    }

private fun String.toDomainLibraryMediaFilter(): LibraryMediaFilter =
    when (this) {
        "all" -> LibraryMediaFilter.ALL
        "anime" -> LibraryMediaFilter.ANIME
        "manga" -> LibraryMediaFilter.MANGA
        else -> LibraryMediaFilter.ALL
    }
