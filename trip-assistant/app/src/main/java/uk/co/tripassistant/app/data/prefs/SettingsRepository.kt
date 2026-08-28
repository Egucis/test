package uk.co.tripassistant.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Non-sensitive preferences (spec section 41).
 *
 * Nothing here is ever gated on subscription state: losing a subscription must not lose settings
 * (spec section 3).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val RETENTION = stringPreferencesKey("history_retention")
        val OVERLAY_SIZE = stringPreferencesKey("overlay_size")
        val OVERLAY_SIDE = stringPreferencesKey("overlay_side")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
        val HAPTIC_GOOD = booleanPreferencesKey("haptic_good")
        val HAPTIC_BORDERLINE = booleanPreferencesKey("haptic_borderline")
        val HAPTIC_POOR = booleanPreferencesKey("haptic_poor")
        val SOUND_GOOD = booleanPreferencesKey("sound_good")
        val SOUND_BORDERLINE = booleanPreferencesKey("sound_borderline")
        val SOUND_POOR = booleanPreferencesKey("sound_poor")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val PRIVACY_ACK = intPreferencesKey("privacy_ack_version")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = prefs[Keys.THEME].toEnum(defaults.themeMode),
            retention = prefs[Keys.RETENTION].toEnum(defaults.retention),
            overlaySize = prefs[Keys.OVERLAY_SIZE].toEnum(defaults.overlaySize),
            overlaySide = prefs[Keys.OVERLAY_SIDE].toEnum(defaults.overlaySide),
            overlayX = prefs[Keys.OVERLAY_X] ?: defaults.overlayX,
            overlayY = prefs[Keys.OVERLAY_Y] ?: defaults.overlayY,
            hapticOnGood = prefs[Keys.HAPTIC_GOOD] ?: defaults.hapticOnGood,
            hapticOnBorderline = prefs[Keys.HAPTIC_BORDERLINE] ?: defaults.hapticOnBorderline,
            hapticOnPoor = prefs[Keys.HAPTIC_POOR] ?: defaults.hapticOnPoor,
            soundOnGood = prefs[Keys.SOUND_GOOD] ?: defaults.soundOnGood,
            soundOnBorderline = prefs[Keys.SOUND_BORDERLINE] ?: defaults.soundOnBorderline,
            soundOnPoor = prefs[Keys.SOUND_POOR] ?: defaults.soundOnPoor,
            diagnosticsEnabled = prefs[Keys.DIAGNOSTICS] ?: defaults.diagnosticsEnabled,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            privacyAckVersion = prefs[Keys.PRIVACY_ACK] ?: defaults.privacyAckVersion
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME, mode.name)

    suspend fun setRetention(retention: HistoryRetention) = put(Keys.RETENTION, retention.name)

    suspend fun setOverlaySize(size: OverlaySize) = put(Keys.OVERLAY_SIZE, size.name)

    suspend fun setOverlaySide(side: OverlaySide) = put(Keys.OVERLAY_SIDE, side.name)

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.settingsDataStore.edit {
            it[Keys.OVERLAY_X] = x
            it[Keys.OVERLAY_Y] = y
        }
    }

    /** Forgets a dragged position so the overlay returns to its default corner. */
    suspend fun resetOverlayPosition() {
        context.settingsDataStore.edit {
            it.remove(Keys.OVERLAY_X)
            it.remove(Keys.OVERLAY_Y)
        }
    }

    suspend fun setHaptics(good: Boolean? = null, borderline: Boolean? = null, poor: Boolean? = null) {
        context.settingsDataStore.edit { prefs ->
            good?.let { prefs[Keys.HAPTIC_GOOD] = it }
            borderline?.let { prefs[Keys.HAPTIC_BORDERLINE] = it }
            poor?.let { prefs[Keys.HAPTIC_POOR] = it }
        }
    }

    suspend fun setSounds(good: Boolean? = null, borderline: Boolean? = null, poor: Boolean? = null) {
        context.settingsDataStore.edit { prefs ->
            good?.let { prefs[Keys.SOUND_GOOD] = it }
            borderline?.let { prefs[Keys.SOUND_BORDERLINE] = it }
            poor?.let { prefs[Keys.SOUND_POOR] = it }
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) = put(Keys.DIAGNOSTICS, enabled)

    suspend fun setOnboardingComplete(complete: Boolean) = put(Keys.ONBOARDING_COMPLETE, complete)

    suspend fun acknowledgePrivacy(version: Int = AppSettings.CURRENT_PRIVACY_VERSION) =
        put(Keys.PRIVACY_ACK, version)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
        this?.let { name -> runCatching { enumValueOf<E>(name) }.getOrNull() } ?: fallback
}
