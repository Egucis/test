package uk.co.tripassistant.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.prefs.AppSettings
import uk.co.tripassistant.app.data.prefs.HistoryRetention
import uk.co.tripassistant.app.data.prefs.OverlaySide
import uk.co.tripassistant.app.data.prefs.OverlaySize
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.app.data.prefs.ThemeMode
import uk.co.tripassistant.app.data.repository.HistoryRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: HistoryRepository
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val historyCount: StateFlow<Int> = history.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setTheme(mode: ThemeMode) = launch { settings.setThemeMode(mode) }

    fun setRetention(retention: HistoryRetention) = launch {
        settings.setRetention(retention)
        // Applying it straight away means the setting does what it says, now, rather than at the
        // next launch (spec section 34).
        history.applyRetention()
    }

    fun setOverlaySize(size: OverlaySize) = launch { settings.setOverlaySize(size) }

    fun setOverlaySide(side: OverlaySide) = launch { settings.setOverlaySide(side) }

    fun resetOverlayPosition() = launch { settings.resetOverlayPosition() }

    fun setHaptics(good: Boolean? = null, borderline: Boolean? = null, poor: Boolean? = null) =
        launch { settings.setHaptics(good, borderline, poor) }

    fun setSounds(good: Boolean? = null, borderline: Boolean? = null, poor: Boolean? = null) =
        launch { settings.setSounds(good, borderline, poor) }

    fun setDiagnosticsEnabled(enabled: Boolean) = launch { settings.setDiagnosticsEnabled(enabled) }

    /** Spec section 34: history goes, settings and subscription stay. */
    fun deleteAllHistory() = launch { history.deleteAll() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
