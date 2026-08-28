package uk.co.tripassistant.app.data.prefs

/** Light / dark / follow the system (spec section 47). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How long evaluated offers are kept (spec section 34).
 *
 * [DISABLED] is a retention choice rather than a separate switch, so "history off" and "keep for
 * 90 days" can never disagree with each other.
 */
enum class HistoryRetention(val days: Int?, val label: String) {
    DISABLED(null, "History disabled"),
    DAYS_30(30, "30 days"),
    DAYS_90(90, "90 days"),
    DAYS_365(365, "1 year"),
    KEEP_ALL(null, "Keep all");

    val isEnabled: Boolean get() = this != DISABLED
    val prunes: Boolean get() = days != null
}

/** Overlay footprint (spec section 27). */
enum class OverlaySize(val label: String) { COMPACT("Compact"), NORMAL("Normal") }

/** Which edge the overlay snaps to when it is reset (spec section 27). */
enum class OverlaySide(val label: String) { LEFT("Left"), RIGHT("Right") }

/**
 * Everything the driver can configure that is not a rule.
 *
 * Alerts are per recommendation and independently switchable, and sound and haptics are separate
 * switches, because a driver may want a buzz for a good offer and silence for everything else
 * (spec section 28).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val retention: HistoryRetention = HistoryRetention.DAYS_90,
    val overlaySize: OverlaySize = OverlaySize.NORMAL,
    val overlaySide: OverlaySide = OverlaySide.RIGHT,
    val overlayX: Int = OVERLAY_POSITION_UNSET,
    val overlayY: Int = OVERLAY_POSITION_UNSET,
    val hapticOnGood: Boolean = true,
    val hapticOnBorderline: Boolean = false,
    val hapticOnPoor: Boolean = false,
    val soundOnGood: Boolean = false,
    val soundOnBorderline: Boolean = false,
    val soundOnPoor: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val privacyAckVersion: Int = 0
) {
    val historyEnabled: Boolean get() = retention.isEnabled

    companion object {
        const val OVERLAY_POSITION_UNSET = Int.MIN_VALUE

        /** Bump when the privacy explanation materially changes, to ask for consent again. */
        const val CURRENT_PRIVACY_VERSION = 1
    }
}
