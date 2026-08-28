package uk.co.tripassistant.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.app.data.repository.OfferStats
import uk.co.tripassistant.app.ui.components.EmptyState
import uk.co.tripassistant.app.ui.components.LabelledValue
import uk.co.tripassistant.app.ui.components.RecommendationChip
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.ThinDivider
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.app.util.Timestamps
import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.OfferOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** History and daily analytics (spec sections 32 and 33). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenOffer: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(12)

        if (!state.historyEnabled) {
            SectionCard {
                Text("History is switched off", style = MaterialTheme.typography.titleMedium)
                VerticalSpace(6)
                Text(
                    "Nothing new is being recorded. Anything recorded before you switched it off is still here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VerticalSpace(12)
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            HistoryRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = {
                        if (range == HistoryRange.CUSTOM) pickingFrom = true else viewModel.setRange(range)
                    },
                    label = { Text(range.label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        VerticalSpace(8)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        if (state.range == HistoryRange.CUSTOM && state.customFrom != null && state.customTo != null) {
            VerticalSpace(8)
            Text(
                "${state.customFrom} to ${state.customTo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalSpace(16)
        StatsCard(state.stats)
        VerticalSpace(16)

        if (state.offers.isEmpty()) {
            EmptyState(
                title = "Nothing to show",
                detail = "No offers match these filters."
            )
        } else {
            state.offers.forEach { offer ->
                OfferRow(offer = offer, onClick = { onOpenOffer(offer.id) })
                VerticalSpace(10)
            }
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }

    if (pickingFrom) {
        DateChooser(
            title = "From",
            onDismiss = { pickingFrom = false },
            onChosen = { date ->
                pendingFrom = date
                pickingFrom = false
                pickingTo = true
            }
        )
    }

    if (pickingTo) {
        DateChooser(
            title = "To",
            onDismiss = { pickingTo = false },
            onChosen = { date ->
                pickingTo = false
                pendingFrom?.let { viewModel.setCustomRange(it, date) }
            }
        )
    }
}

/** Spec section 33 — and specifically, no acceptance rate the data cannot support. */
@Composable
private fun StatsCard(stats: OfferStats) {
    SectionCard {
        SectionHeading("In this period")
        if (stats.evaluated == 0) {
            Text(
                "No offers were evaluated.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        LabelledValue("Offers evaluated", stats.evaluated.toString(), emphasise = true)
        LabelledValue("Good", stats.good.toString())
        LabelledValue("Borderline", stats.borderline.toString())
        LabelledValue("Poor", stats.poor.toString())
        if (stats.unknown > 0) LabelledValue("Could not be read", stats.unknown.toString())

        VerticalSpace(10)
        ThinDivider()
        VerticalSpace(10)

        stats.averageFare?.let { LabelledValue("Average offered fare", Formats.money(it)) }
        stats.averagePoundsPerMile?.let { LabelledValue("Average offered £/mile", Formats.poundsPerMile(it)) }
        stats.averagePoundsPerHour?.let { LabelledValue("Average offered £/hour", Formats.poundsPerHourPrecise(it)) }
        stats.averagePickupMiles?.let { LabelledValue("Average pickup", Formats.miles(it)) }

        VerticalSpace(10)
        ThinDivider()
        VerticalSpace(10)
        Text(
            text = "Accepted outcome detected for ${stats.acceptedDetected} of ${stats.evaluated} offers",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "An offer disappearing does not prove it was declined, so outcomes are only " +
                "recorded when the screen clearly shows a trip was accepted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfferRow(offer: EvaluatedOfferEntity, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(Timestamps.time(offer.timestamp), style = MaterialTheme.typography.labelLarge)
                Text(Formats.money(offer.fare), style = MaterialTheme.typography.headlineMedium)
            }
            RecommendationChip(offer.recommendation)
        }

        VerticalSpace(10)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${Formats.miles(offer.totalMiles)} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(Formats.poundsPerMile(offer.poundsPerMile), style = MaterialTheme.typography.bodyMedium)
            offer.poundsPerHour?.let {
                Text(Formats.poundsPerHourPrecise(it), style = MaterialTheme.typography.bodyMedium)
            }
            offer.riderRating?.let {
                Text(Formats.rating(it), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (offer.outcome == OfferOutcome.ACCEPTED) {
            VerticalSpace(6)
            Text(
                "Accepted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        offer.primaryReason?.let { reason ->
            VerticalSpace(6)
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooser(title: String, onDismiss: () -> Unit, onChosen: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onChosen(
                        Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    )
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState, title = { Text(title, modifier = Modifier.padding(16.dp)) })
    }
}
