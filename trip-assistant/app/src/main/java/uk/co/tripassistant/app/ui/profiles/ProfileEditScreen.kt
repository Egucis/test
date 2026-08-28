package uk.co.tripassistant.app.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleUnit

/**
 * Editing one profile (spec section 19).
 *
 * Every rule has the three controls the spec calls for: on or off, a target, and how much it
 * matters. The importance chips are worded the way a driver would say it, because "SOFT" and
 * "HARD" mean nothing on their own.
 */
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteRefused by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    if (state.loading) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Edit profile", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        SectionCard {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Profile name") },
                singleLine = true,
                isError = state.name.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            VerticalSpace(12)
            if (state.isActive) {
                Text(
                    "This is the active profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = viewModel::makeActive) { Text("Make this the active profile") }
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Close-call tolerance")
            Text(
                "How far below a minimum — or above a maximum — still counts as a close call rather " +
                    "than a failure. 10% is the default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(12)
            OutlinedTextField(
                value = state.tolerancePercentText,
                onValueChange = viewModel::setTolerance,
                label = { Text("Tolerance (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.tolerancePercent?.let { it in 0.0..90.0 } != true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        VerticalSpace(16)
        Text("Rules", style = MaterialTheme.typography.titleLarge)
        VerticalSpace(8)

        state.rules.forEach { rule ->
            RuleCard(
                rule = rule,
                onEnabledChange = { viewModel.setRuleEnabled(rule.ruleId, it) },
                onImportanceChange = { viewModel.setRuleImportance(rule.ruleId, it) },
                onTargetChange = { viewModel.setRuleTarget(rule.ruleId, it) }
            )
            VerticalSpace(10)
        }

        if (!state.hasActiveRule) {
            SectionCard {
                Text(
                    "No rules are switched on. With this profile active, every readable offer would " +
                        "come out GOOD.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            VerticalSpace(10)
        }

        VerticalSpace(10)
        Button(
            onClick = { viewModel.save(onBack) },
            enabled = state.isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save profile")
        }

        VerticalSpace(6)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }

        if (state.canDelete) {
            VerticalSpace(6)
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete profile", color = MaterialTheme.colorScheme.error)
            }
        }
        VerticalSpace(20)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this profile?") },
            text = { Text("Your trip history is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack, onRefused = { deleteRefused = true })
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }

    if (deleteRefused) {
        AlertDialog(
            onDismissRequest = { deleteRefused = false },
            title = { Text("Can't delete the last profile") },
            text = { Text("The assistant always needs one profile to score offers against.") },
            confirmButton = { TextButton(onClick = { deleteRefused = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RuleEditState,
    onEnabledChange: (Boolean) -> Unit,
    onImportanceChange: (RuleImportance) -> Unit,
    onTargetChange: (String) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.ruleId.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    ruleExplanation(rule.ruleId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
        }

        if (!rule.enabled) return@SectionCard

        VerticalSpace(12)
        OutlinedTextField(
            value = rule.targetText,
            onValueChange = onTargetChange,
            label = { Text(unitLabel(rule.ruleId.unit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !rule.isValid,
            modifier = Modifier.fillMaxWidth()
        )

        VerticalSpace(12)
        Text(
            "How much does this matter?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSpace(6)
        Row {
            FilterChip(
                selected = rule.importance == RuleImportance.SOFT,
                onClick = { onImportanceChange(RuleImportance.SOFT) },
                label = { Text("Guide") }
            )
            ChipSpacer()
            FilterChip(
                selected = rule.importance == RuleImportance.HARD,
                onClick = { onImportanceChange(RuleImportance.HARD) },
                label = { Text("Dealbreaker") }
            )
        }
        VerticalSpace(6)
        Text(
            text = when (rule.importance) {
                RuleImportance.HARD -> "Failing this alone makes the offer POOR."
                else -> "Counts towards the overall recommendation but cannot reject an offer on its own."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChipSpacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
}

private fun unitLabel(unit: RuleUnit): String = when (unit) {
    RuleUnit.POUNDS_PER_MILE -> "Minimum £ per mile"
    RuleUnit.POUNDS_PER_HOUR -> "Minimum £ per hour"
    RuleUnit.MILES -> "Miles"
    RuleUnit.MINUTES -> "Minutes"
    RuleUnit.RATING -> "Rating out of 5"
    RuleUnit.POUNDS -> "Pounds"
    RuleUnit.PERCENT -> "Percent"
}

private fun ruleExplanation(ruleId: RuleId): String = when (ruleId) {
    RuleId.MIN_POUNDS_PER_MILE -> "Fare divided by pickup plus trip miles"
    RuleId.MIN_POUNDS_PER_HOUR -> "Fare divided by pickup plus trip time"
    RuleId.MIN_FARE -> "The offered fare on its own"
    RuleId.MAX_PICKUP_MILES -> "How far you will drive unpaid to reach the rider"
    RuleId.MAX_PICKUP_MINUTES -> "How long that unpaid drive takes"
    RuleId.MAX_PICKUP_PERCENT -> "Pickup miles as a share of the whole journey"
    RuleId.MIN_RIDER_RATING -> "Only checked when Uber shows a rating"
}
