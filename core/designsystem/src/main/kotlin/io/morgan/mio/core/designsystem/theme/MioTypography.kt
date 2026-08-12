package io.morgan.mio.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.morgan.mio.core.designsystem.R

internal val MioGabaritoFontFamily: FontFamily =
    FontFamily(
        Font(R.font.gabarito_variable, weight = FontWeight.Normal),
        Font(R.font.gabarito_variable, weight = FontWeight.Medium),
        Font(R.font.gabarito_variable, weight = FontWeight.SemiBold),
        Font(R.font.gabarito_variable, weight = FontWeight.Bold),
    )

private val MioPlatformTextStyle =
    PlatformTextStyle(
        includeFontPadding = false,
    )

private val MioLineHeightStyle =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

public fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

public val DefaultMioTypography: MioTypography =
    MioTypography(
        display =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        title =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.03).em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        section =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.025).em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        cardTitle =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.015).em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        body =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        bodyStrong =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        label =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
        caption =
            TextStyle(
                fontFamily = MioGabaritoFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.01.em,
                platformStyle = MioPlatformTextStyle,
                lineHeightStyle = MioLineHeightStyle,
            ),
    )

@Immutable
public data class MioTypography(
    public val display: TextStyle,
    public val title: TextStyle,
    public val section: TextStyle,
    public val cardTitle: TextStyle,
    public val body: TextStyle,
    public val bodyStrong: TextStyle,
    public val label: TextStyle,
    public val caption: TextStyle,
) {
    public val materialTypography: Typography =
        Typography(
            displayLarge = display,
            displayMedium = display,
            displaySmall = display,
            headlineLarge = title,
            headlineMedium = title,
            headlineSmall = section,
            titleLarge = title,
            titleMedium = cardTitle,
            titleSmall = bodyStrong,
            bodyLarge = body,
            bodyMedium = body,
            bodySmall = caption,
            labelLarge = bodyStrong,
            labelMedium = label,
            labelSmall = caption,
        )
}
