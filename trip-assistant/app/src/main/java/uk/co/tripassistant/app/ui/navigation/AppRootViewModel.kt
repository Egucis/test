package uk.co.tripassistant.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.tripassistant.app.data.prefs.AppSettings
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import javax.inject.Inject

/** Just enough state to choose the theme and the first screen. */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** Null only for the first frame, while DataStore is read. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
