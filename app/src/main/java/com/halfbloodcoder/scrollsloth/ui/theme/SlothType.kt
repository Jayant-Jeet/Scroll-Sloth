package com.halfbloodcoder.scrollsloth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.halfbloodcoder.scrollsloth.R

/** Inter, bundled in res/font (OFL). Downloadable fonts would be a network call, which the app does not make. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** A text style in the handoff's units: px map 1:1 to sp, line height is a multiple of the size. */
fun slothText(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: Float? = null): TextStyle =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight?.em ?: TextUnit.Unspecified,
    )

/** Material styles in Inter, for stock components (dialogs, text fields, the debug screens). */
val SlothTypography: Typography = Typography().let { base ->
    fun TextStyle.inter() = copy(fontFamily = Inter)
    Typography(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}
