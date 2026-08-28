package uk.co.tripassistant.app.ui.tester

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.tripassistant.app.ui.components.LabelledValue
import uk.co.tripassistant.app.ui.components.RecommendationChip
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.ThinDivider
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.app.ui.components.metricStatusColor
import uk.co.tripassistant.app.ui.components.metricStatusLabel
import uk.co.tripassistant.core.format.Formats

/** Spec section 43: try an offer against your own rules without waiting for a real one. */
@Composable
fun RuleTesterScreen(
    onBack: () -> Unit,
    viewModel: RuleTesterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Test my rules", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(6)
        Text(
            "Scored against ${state.profile?.name ?: "your active profile"}, exactly as the overlay would.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSpace(16)

        SectionCard {
            SectionHeading("The offer")
            NumberField("Fare (£)", state.fare, viewModel::setFare)
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    NumberField("Pickup miles", state.pickupMiles, viewModel::setPickupMiles)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    NumberField("Pickup minutes", state.pickupMinutes, viewModel::setPickupMinutes)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    NumberField("Trip miles", state.tripMiles, viewModel::setTripMiles)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    NumberField("Trip minutes", state.tripMinutes, viewModel::setTripMinutes)
                }
            }
            NumberField("Rider rating (blank if not shown)", state.riderRating, viewModel::setRiderRating)
            VerticalSpace(12)
            Button(onClick = viewModel::evaluate, modifier = Modifier.fillMaxWidth()) {
                Text("Evaluate")
            }
        }

        VerticalSpace(12)

        val evaluation = state.evaluation
        if (evaluation != null) {
            SectionCard {
                SectionHeading("What the overlay would show")
                RecommendationChip(evaluation.recommendation)
                VerticalSpace(12)

                val metrics = evaluation.metrics
                if (metrics != null) {
                    Text(
                        text = listOfNotNull(
                            Formats.poundsPerMile(metrics.poundsPerMile),
                            metrics.poundsPerHour?.let { Formats.poundsPerHour(it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.displaySmall
                    )
                    VerticalSpace(12)
                    LabelledValue("Total distance", Formats.miles(metrics.totalMiles))
                    metrics.totalMinutes?.let { LabelledValue("Total time", Formats.minutes(it)) }
                    LabelledValue("Pickup share", Formats.percent(metrics.pickupPercentage))
                    LabelledValue(
                        "Passenger £/mile",
                        Formats.poundsPerMile(metrics.passengerPoundsPerMile)
                    )
                }

                if (evaluation.reasons.isNotEmpty()) {
                    VerticalSpace(12)
                    ThinDivider()
                    VerticalSpace(10)
                    SectionHeading("Why")
                    evaluation.reasons.forEach { reason ->
                        Text(reason.headline, style = MaterialTheme.typography.titleMedium)
                        Text(
                            reason.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        VerticalSpace(8)
                    }
                }
            }

            if (evaluation.metricResults.isNotEmpty()) {
                VerticalSpace(12)
                SectionCard {
                    SectionHeading("Rule by rule")
                    evaluation.metricResults.forEach { result ->
                        LabelledValue(
                            label = result.ruleId.displayName,
                            value = "${metricStatusLabel(result.status)} · " +
                                (result.actual?.let { Formats.actual(result.ruleId.unit, it) } ?: "not shown"),
                            valueColor = metricStatusColor(result.status)
                        )
                    }
                }
            }
        }

        if (state.validationIssues.isNotEmpty()) {
            VerticalSpace(12)
            SectionCard {
                SectionHeading("Why this could not be scored")
                state.validationIssues.forEach { issue ->
                    Text(
                        issue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
