package io.morgan.mio.feature.you.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioBottomSheet
import io.morgan.mio.core.designsystem.component.MioIcon
import io.morgan.mio.core.designsystem.component.MioStatusRow
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.TitleLanguagePreference

@Composable
internal fun YouPreferencesSection(
    appearance: AppearancePreference,
    titleLanguage: TitleLanguagePreference,
    saveFailed: Boolean,
    onDismissError: () -> Unit,
    onAppearance: (AppearancePreference) -> Unit,
    onTitleLanguage: (TitleLanguagePreference) -> Unit,
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.you_preferences), style = MioTheme.typography.section)
        Surface(
            shape = MioTheme.shapes.lg,
            color = MioTheme.colors.surface,
            border = BorderStroke(1.dp, MioTheme.colors.border),
        ) {
            Column {
                PreferenceRow(
                    stringResource(R.string.you_appearance),
                    stringResource(appearance.labelRes()),
                    appearance.iconRes(),
                ) {
                    sheet =
                        "appearance"
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MioTheme.colors.border)
                PreferenceRow(
                    stringResource(R.string.you_title_language),
                    stringResource(titleLanguage.labelRes()),
                    R.drawable.you_menu_book,
                ) {
                    sheet =
                        "titles"
                }
            }
        }
        if (saveFailed) {
            Text(stringResource(R.string.you_appearance_failed), color = MioTheme.colors.error)
            TextButton(onClick = onDismissError) { Text(stringResource(R.string.you_dismiss)) }
        }
    }
    MioBottomSheet(
        visible = sheet != null,
        onDismissRequest = { sheet = null },
        title = stringResource(if (sheet == "titles") R.string.you_title_language else R.string.you_appearance),
        subtitle =
            stringResource(
                if (sheet ==
                    "titles"
                ) {
                    R.string.you_title_language_description
                } else {
                    R.string.you_appearance_description
                },
            ),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sheet == "titles") {
                TitleLanguagePreference.entries.forEach { preference ->
                    MioStatusRow(
                        id = preference.name,
                        label = stringResource(preference.labelRes()),
                        supportingText = stringResource(preference.descriptionRes()),
                        leadingIconRes =
                            if (preference ==
                                TitleLanguagePreference.SYSTEM
                            ) {
                                R.drawable.you_smartphone
                            } else {
                                R.drawable.you_translate
                            },
                        selected = preference == titleLanguage,
                        onSelected = {
                            onTitleLanguage(preference)
                            sheet = null
                        },
                    )
                }
                Text(
                    stringResource(R.string.you_title_language_fallback),
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.textMuted,
                )
            } else {
                AppearancePreference.entries.forEach { preference ->
                    MioStatusRow(
                        id = preference.name,
                        label = stringResource(preference.labelRes()),
                        supportingText =
                            if (preference ==
                                AppearancePreference.SYSTEM
                            ) {
                                stringResource(R.string.you_follow_device_appearance)
                            } else {
                                null
                            },
                        leadingIconRes = preference.iconRes(),
                        selected = preference == appearance,
                        onSelected = {
                            onAppearance(preference)
                            sheet = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    label: String,
    value: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 76.dp,
                ).clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MioIcon(icon, contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MioTheme.typography.bodyStrong)
            Text(value, style = MioTheme.typography.body, color = MioTheme.colors.textMuted)
        }
        MioIcon(R.drawable.you_chevron_right, contentDescription = null)
    }
}

private fun AppearancePreference.iconRes(): Int =
    when (this) {
        AppearancePreference.SYSTEM -> R.drawable.you_smartphone
        AppearancePreference.LIGHT -> R.drawable.you_light_mode
        AppearancePreference.DARK -> R.drawable.you_dark_mode
    }

private fun TitleLanguagePreference.labelRes(): Int =
    when (this) {
        TitleLanguagePreference.SYSTEM -> R.string.you_appearance_system
        TitleLanguagePreference.ENGLISH -> R.string.you_title_english
        TitleLanguagePreference.JAPANESE -> R.string.you_title_japanese
        TitleLanguagePreference.ROMAJI -> R.string.you_title_romaji
    }

private fun TitleLanguagePreference.descriptionRes(): Int =
    when (this) {
        TitleLanguagePreference.SYSTEM -> R.string.you_follow_device_language
        TitleLanguagePreference.ENGLISH -> R.string.you_title_english_example
        TitleLanguagePreference.JAPANESE -> R.string.you_title_japanese_example
        TitleLanguagePreference.ROMAJI -> R.string.you_title_romaji_example
    }
