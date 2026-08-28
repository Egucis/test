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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.ThinDivider
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleProfile

/** Spec section 18: several profiles, exactly one active, and the active one always obvious. */
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onOpenRuleTester: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Profiles", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(6)
        Text(
            "One profile is active at a time. The active profile is what the overlay uses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSpace(16)

        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                onMakeActive = { viewModel.setActive(profile.id) },
                onEdit = { onEditProfile(profile.id) }
            )
            VerticalSpace(10)
        }

        VerticalSpace(6)
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Text("New profile")
        }

        VerticalSpace(16)
        SectionCard {
            SectionHeading("Not sure about your thresholds?")
            Text(
                "Type in an offer and see exactly what the overlay would say, without waiting for a real one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(12)
            Button(onClick = onOpenRuleTester) { Text("Test my rules") }
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New profile") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName
                    creating = false
                    newName = ""
                    viewModel.create(name) { id -> onEditProfile(id) }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileRow(profile: RuleProfile, onMakeActive: () -> Unit, onEdit: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = profile.isActive, onClick = onMakeActive)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }

        if (profile.activeRules().isEmpty()) {
            VerticalSpace(8)
            ThinDivider()
            VerticalSpace(8)
            Text(
                "No rules are switched on, so every readable offer would come out GOOD.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun summary(profile: RuleProfile): String {
    val parts = buildList {
        profile.rule(RuleId.MIN_POUNDS_PER_MILE)?.takeIf { it.isActive }?.let {
            add("${Formats.moneyCompact(it.target)}/mi")
        }
        profile.rule(RuleId.MIN_POUNDS_PER_HOUR)?.takeIf { it.isActive }?.let {
            add("${Formats.moneyCompact(it.target)}/h")
        }
        profile.rule(RuleId.MAX_PICKUP_MILES)?.takeIf { it.isActive }?.let {
            add("pickup ≤ ${Formats.miles(it.target)}")
        }
    }
    return if (parts.isEmpty()) "No rules switched on" else parts.joinToString(" · ")
}
