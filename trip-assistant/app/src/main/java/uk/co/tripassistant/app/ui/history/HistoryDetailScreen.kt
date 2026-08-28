package uk.co.tripassistant.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.app.data.repository.HistoryRepository
import uk.co.tripassistant.app.ui.components.LabelledValue
import uk.co.tripassistant.app.ui.components.RecommendationChip
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.app.util.Timestamps
import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.OfferOutcome
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    history: HistoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val offerId: Long = savedStateHandle.get<String>("offerId")?.toLongOrNull() ?: 0L

    val offer: StateFlow<EvaluatedOfferEntity?> = history.observeById(offerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Everything recorded about one offer (spec section 31). Notably: no screenshot, no addresses. */
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel()
) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        val current = offer
        if (current == null) {
            Text("Offer not found", style = MaterialTheme.typography.titleMedium)
            VerticalSpace(12)
            TextButton(onClick = onBack) { Text("Back") }
            return@Column
        }

        Text(Formats.money(current.fare), style = MaterialTheme.typography.headlineLarge)
        Text(
            Timestamps.dateTime(current.timestamp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSpace(12)
        RecommendationChip(current.recommendation)
        VerticalSpace(16)

        SectionCard {
            SectionHeading("What it was worth")
            LabelledValue("£ per mile", Formats.poundsPerMile(current.poundsPerMile), emphasise = true)
            current.poundsPerHour?.let {
                LabelledValue("£ per hour", Formats.poundsPerHourPrecise(it), emphasise = true)
            }
            LabelledValue("Passenger £ per mile", Formats.poundsPerMile(current.passengerPoundsPerMile))
            LabelledValue("Pickup share of the journey", Formats.percent(current.pickupPercentage))
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("The offer")
            LabelledValue("Pickup distance", Formats.miles(current.pickupMiles))
            current.pickupMinutes?.let { LabelledValue("Pickup time", Formats.minutes(it)) }
            LabelledValue("Trip distance", Formats.miles(current.tripMiles))
            current.tripMinutes?.let { LabelledValue("Trip time", Formats.minutes(it)) }
            LabelledValue("Total distance", Formats.miles(current.totalMiles))
            current.totalMinutes?.let { LabelledValue("Total time", Formats.minutes(it)) }
            LabelledValue("Rider rating", current.riderRating?.let { Formats.rating(it) } ?: "Not shown")
        }

        current.allReasons?.let { reasons ->
            VerticalSpace(12)
            SectionCard {
                SectionHeading("Why this recommendation")
                reasons.split("\n").forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    VerticalSpace(4)
                }
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Record")
            LabelledValue("Profile used", current.profileName)
            LabelledValue("Outcome", outcomeLabel(current.outcome))
            LabelledValue("How much was read", confidenceLabel(current.confidence))
            current.parserVersion?.let { LabelledValue("Screen layout", it) }
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }
}

/** Spec section 30: an unknown outcome is stated as unknown, not dressed up as a decline. */
private fun outcomeLabel(outcome: OfferOutcome): String = when (outcome) {
    OfferOutcome.SEEN -> "Seen"
    OfferOutcome.ACCEPTED -> "Accepted"
    OfferOutcome.NOT_ACCEPTED -> "Not accepted"
    OfferOutcome.UNKNOWN_OUTCOME -> "Not known"
}

private fun confidenceLabel(confidence: OfferConfidence): String = when (confidence) {
    OfferConfidence.HIGH -> "Fully read"
    OfferConfidence.PARTIAL -> "Partly read"
    OfferConfidence.LOW -> "Could not be read reliably"
}
