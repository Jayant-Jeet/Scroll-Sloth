package com.halfbloodcoder.scrollsloth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from the Figma handoff. Material's [androidx.compose.material3.ColorScheme] has no
 * slots for things like the heat-map levels or the reflection card, so screens read these directly
 * through [SlothTheme.colors]; the Material scheme is derived from them for stock components.
 */
@Immutable
data class SlothColors(
    val isDark: Boolean,
    /** Page background. */
    val background: Color,
    val card: Color,
    /** Pills, icon boxes, the selected nav item. */
    val cardRaised: Color,
    val divider: Color,
    val meterTrack: Color,
    /** Primary text. */
    val text: Color,
    val muted: Color,
    /** Hero card and other lavender fills. */
    val lavender: Color,
    val lavenderInk: Color,
    val lavenderMuted: Color,
    /** Lavender used as text or a meter fill on [background] or [card]. */
    val lavenderText: Color,
    /** Selected pills and switches. */
    val mint: Color,
    val mintInk: Color,
    /** Mint used as text or an icon tint on [background] or [card]. */
    val mintText: Color,
    /** Platform shares: Instagram, YouTube, Facebook. */
    val instagram: Color,
    val youtube: Color,
    val facebook: Color,
    val reflection: Color,
    val reflectionBorder: Color,
    val reflectionBody: Color,
    /** Track behind pill toggles and fact pills inside the reflection card. */
    val inset: Color,
    val destructive: Color,
    val futureCell: Color,
    /** Heat-map fills, level 0 (no reels) to 4 (a heavy day or more). */
    val heat: List<Color>,
    /** Text colour on each [heat] level. */
    val onHeat: List<Color>,
    val nav: Color,
    val heroDivider: Color,
    val trackingPill: Color,
) {
    companion object {
        val Splash = Color(0xFFFFF4DE)
        val SplashRingTrack = Color(0xFF9DEDD6)
        val SplashRingIndicator = Color(0xFF102C49)
    }
}

private val Navy = Color(0xFF08152C)
private val Cream = Color(0xFFF8F4EA)
private val Lavender = Color(0xFFCCB9F5)
private val LavenderInk = Color(0xFF28213F)
private val LavenderMuted = Color(0xFF65587C)
private val Mint = Color(0xFFA2E8D1)
private val MintInk = Color(0xFF123F38)
private val HeatDeep = Color(0xFF716092)

val DarkSlothColors = SlothColors(
    isDark = true,
    background = Navy,
    card = Color(0xFF13223C),
    cardRaised = Color(0xFF1C2E48),
    divider = Color(0xFF283951),
    meterTrack = Color(0xFF31425C),
    text = Cream,
    muted = Color(0xFFA9B5CC),
    lavender = Lavender,
    lavenderInk = LavenderInk,
    lavenderMuted = LavenderMuted,
    lavenderText = Lavender,
    mint = Mint,
    mintInk = MintInk,
    mintText = Mint,
    instagram = Lavender,
    youtube = Mint,
    facebook = Color(0xFFA9B5CC),
    reflection = Color(0xFF142F36),
    reflectionBorder = Mint.copy(alpha = 0.15f),
    reflectionBody = Color(0xFFC0D3CF),
    inset = Navy,
    destructive = Color(0xFFF4AFB5),
    futureCell = Color(0xFF0D1A30),
    heat = listOf(Color(0xFF1C2E48), Lavender, Color(0xFFBAA9E6), Color(0xFFA89AC8), HeatDeep),
    onHeat = listOf(Cream, LavenderInk, LavenderInk, LavenderInk, Cream),
    nav = Color(0xFF13223C),
    heroDivider = Color(0x2E54466F),
    trackingPill = Color.White.copy(alpha = 0.23f),
)

/** The handoff's light palette: warm page, white cards, navy text, the same lavender and mint accents. */
val LightSlothColors = SlothColors(
    isDark = false,
    background = Color(0xFFFFF9EE),
    card = Color.White,
    cardRaised = Color(0xFFF4ECDD),
    divider = Color(0xFFEAE0CE),
    meterTrack = Color(0xFFE6DCC8),
    text = Color(0xFF17233A),
    muted = Color(0xFF647089),
    lavender = Lavender,
    lavenderInk = LavenderInk,
    lavenderMuted = LavenderMuted,
    lavenderText = Color(0xFF6A55A6),
    mint = Mint,
    mintInk = MintInk,
    mintText = Color(0xFF1C7560),
    instagram = Color(0xFF9F86DB),
    youtube = Color(0xFF5DC4A3),
    facebook = Color(0xFF8A94AA),
    reflection = Color(0xFFE5F6F0),
    reflectionBorder = Color(0xFF5DC4A3).copy(alpha = 0.35f),
    reflectionBody = Color(0xFF3B5551),
    inset = Color.White,
    destructive = Color(0xFFB3404A),
    futureCell = Color(0xFFFFF9EE),
    heat = listOf(Color(0xFFF1E8D8), Color(0xFFE3D7FA), Lavender, Color(0xFFA89AC8), HeatDeep),
    onHeat = listOf(Color(0xFF17233A), LavenderInk, LavenderInk, LavenderInk, Cream),
    nav = Color.White,
    heroDivider = Color(0x2E54466F),
    trackingPill = Color.White.copy(alpha = 0.45f),
)

val LocalSlothColors = staticCompositionLocalOf { DarkSlothColors }
