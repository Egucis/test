package uk.co.tripassistant.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.repository.ProfileRepository
import uk.co.tripassistant.core.model.RuleProfile
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<RuleProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActive(id) }
    }

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.create(name.trim().ifBlank { "Custom" })
            onCreated(id)
        }
    }
}
