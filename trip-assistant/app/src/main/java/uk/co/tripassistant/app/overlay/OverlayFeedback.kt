package uk.co.tripassistant.app.overlay

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import uk.co.tripassistant.app.data.prefs.AppSettings
import uk.co.tripassistant.core.model.Recommendation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional alerts (spec section 28).
 *
 * Sound and haptics are separate switches and are set per recommendation, so a driver can ask for
 * a buzz on a good offer and nothing at all otherwise. Everything here is short and unobtrusive by
 * design: no speech, no repeated tones, nothing that competes for attention while driving
 * (spec sections 28 and 48).
 */
@Singleton
class OverlayFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private var toneGenerator: ToneGenerator? = null

    fun alert(recommendation: Recommendation, settings: AppSettings) {
        val haptic = when (recommendation) {
            Recommendation.GOOD -> settings.hapticOnGood
            Recommendation.BORDERLINE -> settings.hapticOnBorderline
            Recommendation.POOR -> settings.hapticOnPoor
            Recommendation.UNKNOWN -> false
        }
        val sound = when (recommendation) {
            Recommendation.GOOD -> settings.soundOnGood
            Recommendation.BORDERLINE -> settings.soundOnBorderline
            Recommendation.POOR -> settings.soundOnPoor
            Recommendation.UNKNOWN -> false
        }

        if (haptic) vibrate(recommendation)
        if (sound) beep(recommendation)
    }

    private fun vibrate(recommendation: Recommendation) {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        // A double tap for good, a single short tap for anything else — distinguishable without
        // looking, and over in a quarter of a second either way.
        val pattern = if (recommendation == Recommendation.GOOD) {
            longArrayOf(0, 35, 70, 35)
        } else {
            longArrayOf(0, 45)
        }
        runCatching {
            device.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun beep(recommendation: Recommendation) {
        runCatching {
            val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
                .also { toneGenerator = it }
            val tone = when (recommendation) {
                Recommendation.GOOD -> ToneGenerator.TONE_PROP_ACK
                Recommendation.POOR -> ToneGenerator.TONE_PROP_NACK
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            generator.startTone(tone, TONE_MILLIS)
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private companion object {
        const val VOLUME = 55
        const val TONE_MILLIS = 140
    }
}
