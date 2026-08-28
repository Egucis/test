package uk.co.tripassistant.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Trip Assistant's palette (spec section 45).
 *
 * Same design family as CabComply — the neutral ink and surface tones and the green/amber/red
 * status trio are shared, so the two apps look like they were made by the same people. The
 * identity is different on purpose: CabComply leads with its trust blue, this app leads with
 * green, because the one thing the driver is looking for is "is this trip worth it".
 */
val BrandGreen = Color(0xFF1B7F5A)
val BrandGreenDark = Color(0xFF0E4C36)
val BrandGreenLight = Color(0xFFE4F1EB)

val StatusGood = Color(0xFF1E8E5A)
val StatusBorderline = Color(0xFFC77700)
val StatusPoor = Color(0xFFB3261E)
val StatusUnknown = Color(0xFF5B6B73)

val BrandSurface = Color(0xFFFFFFFF)
val BrandBackground = Color(0xFFF5F7F8)
val BrandInk = Color(0xFF16232B)
val BrandInkMuted = Color(0xFF5B6B73)
val BrandOutline = Color(0xFFDDE3E6)

val BrandSurfaceDark = Color(0xFF11191E)
val BrandBackgroundDark = Color(0xFF0B1215)
val BrandInkOnDark = Color(0xFFE7EEF1)
val BrandInkMutedOnDark = Color(0xFFA9B7BC)
val BrandOutlineDark = Color(0xFF2A3439)
val BrandGreenOnDark = Color(0xFF63C79C)
val StatusPoorOnDark = Color(0xFFE6857D)
