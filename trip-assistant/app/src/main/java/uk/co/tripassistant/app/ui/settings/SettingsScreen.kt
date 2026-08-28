package uk.co.tripassistant.app.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import uk.co.tripassistant.app.data.prefs.HistoryRetention
import uk.co.tripassistant.app.data.prefs.OverlaySide
import uk.co.tripassistant.app.data.prefs.OverlaySize
import uk.co.tripassistant.app.data.prefs.ThemeMode
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.ThinDivider
import uk.co.tripassistant.app.ui.components.VerticalSpace

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSubscription: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        SectionCard {
            SectionHeading("Appearance")
            ChipRow(
                options = ThemeMode.entries.map { it to it.label() },
                selected = settings.themeMode,
                onSelect = viewModel::setTheme
            )
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Overlay")
            Text("Size", style = MaterialTheme.typography.bodyMedium)
            VerticalSpace(6)
            ChipRow(
                options = OverlaySize.entries.map { it to it.label },
                selected = settings.overlaySize,
                onSelect = viewModel::setOverlaySize
            )
            VerticalSpace(12)
            Text("Preferred side", style = MaterialTheme.typography.bodyMedium)
            VerticalSpace(6)
            ChipRow(
                options = OverlaySide.entries.map { it to it.label },
                selected = settings.overlaySide,
                onSelect = viewModel::setOverlaySide
            )
            VerticalSpace(12)
            OutlinedButton(onClick = viewModel::resetOverlayPosition) {
                Text("Reset overlay position")
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Alerts")
            Text(
                "Short and unobtrusive. Sound and vibration are separate, and each recommendation " +
                    "can be switched on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(10)
            SwitchRow("Vibrate on GOOD", settings.hapticOnGood) { viewModel.setHaptics(good = it) }
            SwitchRow("Vibrate on BORDERLINE", settings.hapticOnBorderline) { viewModel.setHaptics(borderline = it) }
            SwitchRow("Vibrate on POOR", settings.hapticOnPoor) { viewModel.setHaptics(poor = it) }
            ThinDivider()
            SwitchRow("Sound on GOOD", settings.soundOnGood) { viewModel.setSounds(good = it) }
            SwitchRow("Sound on BORDERLINE", settings.soundOnBorderline) { viewModel.setSounds(borderline = it) }
            SwitchRow("Sound on POOR", settings.soundOnPoor) { viewModel.setSounds(poor = it) }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("History")
            Text(
                "$historyCount ${if (historyCount == 1) "offer" else "offers"} recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(10)
            ChipRow(
                options = HistoryRetention.entries.map { it to it.label },
                selected = settings.retention,
                onSelect = viewModel::setRetention
            )
            VerticalSpace(12)
            OutlinedButton(onClick = { confirmDelete = true }) {
                Text("Delete all trip history", color = MaterialTheme.colorScheme.error)
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Support")
            SwitchRow("Show diagnostics", settings.diagnosticsEnabled) {
                viewModel.setDiagnosticsEnabled(it)
            }
            Text(
                "Diagnostics show what the assistant read from the last screen. Useful when Uber " +
                    "changes its layout.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.diagnosticsEnabled) {
                VerticalSpace(10)
                OutlinedButton(onClick = onOpenDiagnostics) { Text("Open diagnostics") }
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Subscription and privacy")
            TextButton(onClick = onOpenSubscription, modifier = Modifier.fillMaxWidth()) {
                Text("Subscription", modifier = Modifier.fillMaxWidth())
            }
            ThinDivider()
            TextButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                Text("Privacy", modifier = Modifier.fillMaxWidth())
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("About")
            Text(
                "Trip Assistant is an independent tool for private-hire drivers. It is not produced, " +
                    "endorsed or supported by Uber, and it never accepts or declines a trip for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all trip history?") },
            text = {
                Text(
                    "This removes every recorded offer from this device. Your settings, profiles " +
                        "and subscription are not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAllHistory()
                }) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
