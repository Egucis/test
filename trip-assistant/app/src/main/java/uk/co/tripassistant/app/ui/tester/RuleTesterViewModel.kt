package uk.co.tripassistant.app.ui.tester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.repository.ProfileRepository
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.rules.RuleEngine
import uk.co.tripassistant.core.validation.OfferValidator
import uk.co.tripassistant.core.validation.ValidationOutcome
import javax.inject.Inject

data class RuleTesterUiState(
    val profile: RuleProfile? = null,
    val fare: String = "18.00",
    val pickupMiles: String = "2.0",
    val pickupMinutes: String = "6",
    val tripMiles: String = "8.0",
    val tripMinutes: String = "24",
    val riderRating: String = "4.91",
    val evaluation: OfferEvaluation? = null,
    val validationIssues: List<String> = emptyList()
)

/**
 * The rule tester of spec section 43.
 *
 * It deliberately runs the *same* path as the live overlay — validate, calculate, score — rather
 * than a simplified preview, so what the driver sees here is exactly what they would have seen on
 * a real offer, including an UNKNOWN if the numbers do not make sense together.
 */
@HiltViewModel
class RuleTesterViewModel @Inject constructor(
    private val profiles: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RuleTesterUiState())
    val state: StateFlow<RuleTesterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(profile = profiles.activeProfile())
            evaluate()
        }
    }

    fun setFare(value: String) = update { it.copy(fare = value) }
    fun setPickupMiles(value: String) = update { it.copy(pickupMiles = value) }
    fun setPickupMinutes(value: String) = update { it.copy(pickupMinutes = value) }
    fun setTripMiles(value: String) = update { it.copy(tripMiles = value) }
    fun setTripMinutes(value: String) = update { it.copy(tripMinutes = value) }
    fun setRiderRating(value: String) = update { it.copy(riderRating = value) }

    fun evaluate() {
        val current = _state.value
        val profile = current.profile ?: return

        val raw = RawOffer(
            fareGbp = current.fare.toNumber(),
            pickupMiles = current.pickupMiles.toNumber(),
            pickupMinutes = current.pickupMinutes.toNumber(),
            tripMiles = current.tripMiles.toNumber(),
            tripMinutes = current.tripMinutes.toNumber(),
            riderRating = current.riderRating.toNumber(),
            parserVersion = "RULE_TESTER"
        )

        when (val outcome = OfferValidator.validate(raw)) {
            is ValidationOutcome.Valid -> _state.value = current.copy(
                evaluation = RuleEngine.evaluate(outcome.offer, profile),
                validationIssues = emptyList()
            )

            is ValidationOutcome.Invalid -> _state.value = current.copy(
                evaluation = RuleEngine.unknown(outcome.reason, profile, parserVersion = "RULE_TESTER"),
                validationIssues = outcome.issues
            )
        }
    }

    private fun update(transform: (RuleTesterUiState) -> RuleTesterUiState) {
        _state.value = transform(_state.value)
    }

    /** Blank means "Uber did not show this", which is a case worth being able to test. */
    private fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()
}
