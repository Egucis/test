package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.cabcomply.app.ui.components.SectionCard

private data class SettingsRow(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SettingsScreen(
    onOpenDriver: () -> Unit,
    onOpenVehicles: () -> Unit,
    onOpenDailyChecks: () -> Unit,
    onOpenDocumentsReminders: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val rows = listOf(
        SettingsRow("Driver", "Your details and licensing authority", Icons.Filled.Person, onOpenDriver),
        SettingsRow("Vehicles", "Manage vehicles and select active vehicle", Icons.Filled.DirectionsCar, onOpenVehicles),
        SettingsRow("Daily Checks", "Checklist and Quick Check preferences", Icons.Filled.Rule, onOpenDailyChecks),
        SettingsRow("Documents & Reminders", "Expiry reminders and notifications", Icons.Filled.Notifications, onOpenDocumentsReminders),
        SettingsRow("Security", "PIN protection and record protection", Icons.Filled.Security, onOpenSecurity),
        SettingsRow("Backup & Data", "Create or restore a backup", Icons.Filled.Backup, onOpenBackup),
        SettingsRow("Subscription", "Plan, trial and upgrade", Icons.Filled.Star, onOpenSubscription),
        SettingsRow("About", "Version, privacy and support", Icons.Filled.Info, onOpenAbout)
    )

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(rows) { row ->
            SectionCard(modifier = Modifier.clickable(onClick = row.onClick)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
