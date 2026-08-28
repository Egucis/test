package uk.co.tripassistant.app.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.repository.ProfileRepository
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.RuleSetting
import javax.inject.Inject

/** One editable rule row. The target is text while it is being typed. */
data class RuleEditState(
    val ruleId: RuleId,
    val enabled: Boolean,
    val importance: RuleImportance,
    val targetText: String
) {
    val target: Double? get() = targetText.replace(',', '.').trim().toDoubleOrNull()
    val isValid: Boolean get() = !enabled || (target?.let { it > 0.0 } == true)
}

data class ProfileEditUiState(
    val loading: Boolean = true,
    val id: Long = 0L,
    val name: String = "",
    val isActive: Boolean = false,
    val tolerancePercentText: String = "10",
    val rules: List<RuleEditState> = emptyList(),
    val canDelete: Boolean = false,
    val finished: Boolean = false
) {
    val tolerancePercent: Double?
        get() = tolerancePercentText.replace(',', '.').trim().toDoubleOrNull()

    val isValid: Boolean
        get() = name.isNotBlank() &&
            rules.all { it.isValid } &&
            (tolerancePercent?.let { it in 0.0..90.0 } == true)

    val hasActiveRule: Boolean
        get() = rules.any { it.enabled && it.importance != RuleImportance.OFF }
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val profileId: Long = savedStateHandle.get<String>("profileId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private var loaded: RuleProfile? = null

    init {
        viewModelScope.launch {
            val profile = repository.byId(profileId)
            if (profile == null) {
                _state.value = ProfileEditUiState(loading = false, finished = true)
                return@launch
            }
            loaded = profile
            _state.value = ProfileEditUiState(
                loading = false,
                id = profile.id,
                name = profile.name,
                isActive = profile.isActive,
                tolerancePercentText = trimNumber(profile.amberTolerancePercent),
                rules = RuleId.displayOrder.map { ruleId ->
                    val setting = profile.rules[ruleId]
                        ?: RuleSetting(enabled = false, importance = RuleImportance.OFF, target = 0.0)
                    RuleEditState(
                        ruleId = ruleId,
                        enabled = setting.enabled,
                        importance = setting.importance,
                        targetText = trimNumber(setting.target)
                    )
                },
                canDelete = repository.observeProfiles().first().size > 1
            )
        }
    }

    fun setName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    fun setTolerance(text: String) {
        _state.value = _state.value.copy(tolerancePercentText = text)
    }

    fun setRuleEnabled(ruleId: RuleId, enabled: Boolean) = updateRule(ruleId) {
        // Switching a rule on with its importance still OFF would leave it silently ignored, so
        // enabling promotes it to SOFT — the driver can make it HARD explicitly.
        it.copy(
            enabled = enabled,
            importance = if (enabled && it.importance == RuleImportance.OFF) RuleImportance.SOFT else it.importance
        )
    }

    fun setRuleImportance(ruleId: RuleId, importance: RuleImportance) = updateRule(ruleId) {
        it.copy(importance = importance, enabled = importance != RuleImportance.OFF)
    }

    fun setRuleTarget(ruleId: RuleId, text: String) = updateRule(ruleId) { it.copy(targetText = text) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val original = loaded ?: return
        if (!current.isValid) return

        viewModelScope.launch {
            repository.save(
                original.copy(
                    name = current.name.trim(),
                    amberTolerancePercent = current.tolerancePercent ?: 10.0,
                    rules = current.rules.associate { rule ->
                        rule.ruleId to RuleSetting(
                            enabled = rule.enabled,
                            importance = rule.importance,
                            target = rule.target ?: 0.0
                        )
                    }
                )
            )
            onSaved()
        }
    }

    fun makeActive() {
        viewModelScope.launch {
            repository.setActive(profileId)
            _state.value = _state.value.copy(isActive = true)
        }
    }

    fun delete(onDeleted: () -> Unit, onRefused: () -> Unit) {
        viewModelScope.launch {
            if (repository.delete(profileId)) onDeleted() else onRefused()
        }
    }

    private fun updateRule(ruleId: RuleId, transform: (RuleEditState) -> RuleEditState) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.map { if (it.ruleId == ruleId) transform(it) else it }
        )
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
