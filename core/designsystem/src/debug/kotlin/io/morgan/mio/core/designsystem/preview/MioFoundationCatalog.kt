package io.morgan.mio.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.R
import io.morgan.mio.core.designsystem.accessibility.contrastRatioAgainst
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.layout.MioWindowInsets
import io.morgan.mio.core.designsystem.theme.MioColors
import io.morgan.mio.core.designsystem.theme.MioMotion
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.designsystem.theme.MioTypography
import java.util.Locale

@Composable
internal fun MioFoundationCatalog() {
    val colors: MioColors = MioTheme.colors
    val typography: MioTypography = MioTheme.typography
    val spacing = MioTheme.spacing
    val shapes = MioTheme.shapes
    val motion = MioTheme.motion
    val accessibility = MioTheme.accessibility
    val safeDrawing = MioWindowInsets.safeDrawing
    val horizontalAndTop = MioWindowInsets.horizontalAndTop
    val bottomSystemBars = MioWindowInsets.bottomSystemBars
    val ime = MioWindowInsets.ime
    val bottomSystemBarsOrIme = MioWindowInsets.bottomSystemBarsOrIme

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text("Mio foundation catalog", style = typography.title)
        Text(
            "${if (accessibility.reducedMotionEnabled) "Reduced" else "Ordinary"} motion · " +
                "haptics ${if (accessibility.hapticsEnabled) "enabled" else "disabled"}",
            style = typography.body,
        )
        Text(
            "safeDrawing=$safeDrawing · horizontalAndTop=$horizontalAndTop · " +
                "bottomSystemBars=$bottomSystemBars · ime=$ime · " +
                "bottomSystemBarsOrIme=$bottomSystemBarsOrIme",
            style = typography.caption,
        )
        HorizontalDivider(color = colors.border)

        CatalogSection("Safe inset guides") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderStrong, shapes.sm)
                    .padding(spacing.space1),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(safeDrawing)
                        .border(1.dp, colors.accent, shapes.xs)
                        .padding(spacing.space2),
                ) {
                    Text("Safe drawing content guide", style = typography.body)
                }
            }
        }

        CatalogSection("Colors") {
            val colorRoles =
                listOf(
                    "background" to colors.background,
                    "surface" to colors.surface,
                    "surfaceSoft" to colors.surfaceSoft,
                    "surfaceRaised" to colors.surfaceRaised,
                    "text" to colors.text,
                    "textMuted" to colors.textMuted,
                    "accent" to colors.accent,
                    "onAccent" to colors.onAccent,
                    "border" to colors.border,
                    "borderStrong" to colors.borderStrong,
                    "scrim" to colors.scrim,
                    "error" to colors.error,
                    "errorSurface" to colors.errorSurface,
                )
            colorRoles.forEach { (name, color) ->
                ColorRoleRow(name, color, colors.background, colors.text)
            }
            Text(
                "${if (accessibility.highContrastEnabled) "High contrast" else "Normal"} · " +
                    "muted ${colors.textMuted.contrastRatioAgainst(colors.background).formatRatio()} · " +
                    "border ${colors.border.alpha.formatPercent()}",
                style = typography.caption,
            )
        }

        CatalogSection("Typography") {
            TypographyRow("display", typography.display)
            TypographyRow("title", typography.title)
            TypographyRow("section", typography.section)
            TypographyRow("cardTitle", typography.cardTitle)
            TypographyRow("body", typography.body)
            TypographyRow("bodyStrong", typography.bodyStrong)
            TypographyRow("label", typography.label)
            TypographyRow("caption", typography.caption)
        }

        CatalogSection("Spacing, shapes, elevation") {
            listOf(
                "4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 dp",
                "xs 10 · sm 12 · md 16 · lg 24 · nav 22 · full 999 dp",
                "flat 0 · artwork 4 · floatingLow 6 · floatingHigh 8 dp",
            ).forEach { label -> Text(label, style = typography.body) }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Box(Modifier.size(48.dp).background(colors.surfaceSoft, shapes.xs))
                Box(Modifier.size(48.dp).background(colors.surfaceSoft, shapes.sm))
                Box(Modifier.size(48.dp).background(colors.surfaceSoft, shapes.md))
                Box(Modifier.size(48.dp).background(colors.surfaceSoft, shapes.lg))
            }
        }

        CatalogSection("Motion and accessibility") {
            MotionRow(motion)
            Text(
                "spatial motion ${if (accessibility.spatialMotionEnabled) "enabled" else "disabled"} · " +
                    "high contrast ${if (accessibility.highContrastEnabled) "enabled" else "disabled"}",
                style = typography.body,
            )
            Text(
                "haptics policy · current ${if (accessibility.hapticsEnabled) "enabled" else "disabled"} · " +
                    "disabled override performs no platform call",
                style = typography.caption,
            )
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(colors.accent, shapes.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text("48", style = typography.label)
            }
        }

        CatalogSection("Brand") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(colors.accent, shapes.sm)) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.mio_mark),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        colorFilter = ColorFilter.tint(colors.onAccent),
                    )
                }
                Spacer(Modifier.width(spacing.space4))
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.mio_wordmark),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
            }
        }

        CatalogSection("Rounded functional icons") {
            listOf(
                "Today outline" to MioIcons.TodayOutline,
                "Today filled" to MioIcons.TodayFilled,
                "Library outline" to MioIcons.LibraryOutline,
                "Library filled" to MioIcons.LibraryFilled,
                "Discover outline" to MioIcons.DiscoverOutline,
                "Discover filled" to MioIcons.DiscoverFilled,
                "You outline" to MioIcons.YouOutline,
                "You filled" to MioIcons.YouFilled,
                "Back" to MioIcons.Back,
                "Close" to MioIcons.Close,
                "Search" to MioIcons.Search,
                "Sort" to MioIcons.Sort,
                "Add" to MioIcons.Add,
                "Remove" to MioIcons.Remove,
                "Check" to MioIcons.Check,
                "Refresh" to MioIcons.Refresh,
                "Error" to MioIcons.Error,
            ).forEach { (name, icon) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(colors.text),
                    )
                    Spacer(Modifier.width(spacing.space2))
                    Text(name, style = typography.body)
                }
            }
        }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space2)) {
        Text(title, style = MioTheme.typography.section)
        content()
    }
}

@Composable
private fun ColorRoleRow(
    name: String,
    color: Color,
    background: Color,
    contentColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(color, MioTheme.shapes.xs))
        Spacer(Modifier.width(8.dp))
        Text(
            "$name · ${color.contrastRatioAgainst(background).formatRatio()} contrast",
            color = contentColor,
            style = MioTheme.typography.caption,
        )
    }
}

@Composable
private fun TypographyRow(
    name: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    Column {
        Text(name, style = style)
        Text(
            "Gabarito ${style.fontWeight?.weight ?: 400} · ${style.fontSize.value} sp · " +
                "${style.lineHeight.value} sp · ${style.letterSpacing.value} em",
            style = MioTheme.typography.caption,
        )
    }
}

@Composable
private fun MotionRow(motion: MioMotion) {
    Text(
        "press ${motion.pressDownMillis} ms / ${motion.pressScale} · " +
            "release ${motion.pressRelease.responseSeconds}s / ${motion.pressRelease.dampingRatio} · " +
            "tab ${motion.rootTabSelection.responseSeconds}s / ${motion.rootTabSelection.dampingRatio}",
        style = MioTheme.typography.body,
    )
    Text(
        "navigation ${motion.nestedNavigationMillis} ms / ${motion.nestedNavigationOffset} · " +
            "sheet ${motion.bottomSheet.responseSeconds}s / ${motion.bottomSheet.dampingRatio} · " +
            "scrim ${motion.scrimMillis} ms · reduced opacity ${motion.reducedMotionCrossfadeMillis} ms",
        style = MioTheme.typography.caption,
    )
}

private fun Float.formatRatio(): String = "%.2f".format(Locale.ROOT, this)

private fun Float.formatPercent(): String = "%.0f%%".format(Locale.ROOT, this * 100f)

@Preview(name = "393 × 852 light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 dark", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { MioFoundationCatalog() }
}

@Preview(name = "320 × 693 light", widthDp = 320, heightDp = 693, showBackground = true)
@Composable
internal fun MioFoundationCatalogCompactPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 light at fontScale 2.0", widthDp = 393, heightDp = 852, fontScale = 2f)
@Composable
internal fun MioFoundationCatalogLargeTextPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 high-contrast light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogHighContrastLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.HighContrastLight) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 high-contrast dark", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogHighContrastDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.HighContrastDark) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 reduced-motion light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogReducedMotionLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionLight) { MioFoundationCatalog() }
}

@Preview(name = "393 × 852 reduced-motion dark", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioFoundationCatalogReducedMotionDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionDark) { MioFoundationCatalog() }
}
