package uk.co.cabcomply.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.co.cabcomply.app.R
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.DateFormatting

@Composable
fun HomeScreen(
    onStartDailyCheck: (vehicleId: String) -> Unit,
    onQuickCheck: (vehicleId: String) -> Unit,
    onOpenTodayCheck: (inspectionId: String) -> Unit,
    onNavigateMileage: () -> Unit,
    onNavigateDefects: () -> Unit,
    onNavigateDocuments: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateOfficerMode: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (!state.isLoading && state.activeVehicle == null) {
        EmptyState(
            title = "No vehicle added yet",
            message = "Add your first vehicle to start using CabComply.",
            actionLabel = "Add vehicle",
            onAction = onAddVehicle,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HomeHeader(state, viewModel) }
        item {
            TodayCheckCard(
                state = state,
                onStart = { state.activeVehicle?.let { onStartDailyCheck(it.id) } },
                onQuickCheck = { state.activeVehicle?.let { onQuickCheck(it.id) } },
                onOpenExisting = { state.todayInspection?.let { onOpenTodayCheck(it.id) } }
            )
        }
        item {
            QuickActionsGrid(
                openDefects = state.openDefectCount,
                documentsNeedingAttention = state.documentsExpiringSoon + state.documentsExpired,
                onMileage = onNavigateMileage,
                onDefects = onNavigateDefects,
                onDocuments = onNavigateDocuments,
                onHistory = onNavigateHistory,
                onOfficerMode = onNavigateOfficerMode
            )
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, viewModel: HomeViewModel) {
    var showSwitcher by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_cabcomply_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("CabComply", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                state.activeVehicle?.let { "${it.registration} · ${it.make} ${it.model}" } ?: "No active vehicle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.otherVehicles.isNotEmpty()) {
            Box {
                IconButton(onClick = { showSwitcher = true }) {
                    Icon(Icons.Filled.Speed, contentDescription = "Switch vehicle")
                }
                DropdownMenu(expanded = showSwitcher, onDismissRequest = { showSwitcher = false }) {
                    state.otherVehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = { Text("${vehicle.registration} · ${vehicle.make} ${vehicle.model}") },
                            onClick = {
                                viewModel.setActiveVehicle(vehicle.id)
                                showSwitcher = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCheckCard(
    state: HomeUiState,
    onStart: () -> Unit,
    onQuickCheck: () -> Unit,
    onOpenExisting: () -> Unit
) {
    SectionCard {
        Text("Today's Vehicle Check", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val timeAndMileage = state.todayInspection?.let {
            "Completed at ${it.completedAt?.let { t -> DateFormatting.formatTime(t) } ?: "—"} · ${it.odometer} miles"
        }
        when (state.todayCheckState) {
            TodayCheckState.NOT_STARTED -> {
                StatusChip("Not completed", StatusTone.WARNING)
                Spacer(Modifier.height(16.dp))
                PrimaryActionButton(text = "Start Daily Check", onClick = onStart)
                Spacer(Modifier.height(8.dp))
                SecondaryActionButton(text = "Quick Check", onClick = onQuickCheck)
            }
            TodayCheckState.COMPLETED_CLEAN -> {
                StatusChip("Completed", StatusTone.SUCCESS)
                Spacer(Modifier.height(10.dp))
                timeAndMileage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onOpenExisting, modifier = Modifier.fillMaxWidth()) {
                    Text("View today's check")
                }
            }
            TodayCheckState.COMPLETED_WITH_DEFECT -> {
                StatusChip("Completed with defect", StatusTone.DANGER)
                Spacer(Modifier.height(10.dp))
                timeAndMileage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onOpenExisting, modifier = Modifier.fillMaxWidth()) {
                    Text("View today's check")
                }
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    openDefects: Int,
    documentsNeedingAttention: Int,
    onMileage: () -> Unit,
    onDefects: () -> Unit,
    onDocuments: () -> Unit,
    onHistory: () -> Unit,
    onOfficerMode: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickActionRow("Mileage", "Record start/end mileage", Icons.Filled.Speed, null, onMileage)
        QuickActionRow(
            "Defects",
            if (openDefects == 0) "No open defects" else "$openDefects open defect${if (openDefects == 1) "" else "s"}",
            Icons.Filled.ReportProblem,
            if (openDefects > 0) StatusTone.DANGER else null,
            onDefects
        )
        QuickActionRow(
            "Documents",
            if (documentsNeedingAttention == 0) "All up to date" else "$documentsNeedingAttention need attention",
            Icons.Filled.Description,
            if (documentsNeedingAttention > 0) StatusTone.WARNING else null,
            onDocuments
        )
        QuickActionRow("History & Reports", "Past checks and weekly reports", Icons.Filled.History, null, onHistory)
        QuickActionRow("Show to Officer", "Read-only view for inspections", Icons.Filled.Shield, null, onOfficerMode)
    }
}

@Composable
private fun QuickActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: StatusTone?,
    onClick: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tone != null) {
                val dotColor = when (tone) {
                    StatusTone.SUCCESS -> MaterialTheme.colorScheme.secondary
                    StatusTone.WARNING -> androidx.compose.ui.graphics.Color(0xFFC77700)
                    StatusTone.DANGER -> MaterialTheme.colorScheme.error
                    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = dotColor)
                }
            }
        }
    }
}
