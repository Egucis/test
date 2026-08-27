package uk.co.cabcomply.app.ui.mileage

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.data.mileage.HmrcRateRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.repository.MileageRepository
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import uk.co.cabcomply.app.util.HmrcMileageRates
import uk.co.cabcomply.app.util.UkTaxYear
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MileageUiState(
    val activeVehicleId: String? = null,
    val entries: List<MileageEntryEntity> = emptyList(),
    val flagged: List<MileageEntryEntity> = emptyList(),
    val rateProfile: HmrcMileageRates.RateProfile = HmrcMileageRates.defaultProfile(UkTaxYear.forDate(LocalDate.now()).startYear)
) {
    private val zone = ZoneId.systemDefault()

    val currentTaxYearTotal: Int get() {
        val tax = UkTaxYear.forDate(LocalDate.now())
        return entries.filter { inTaxYear(it, tax) && it.endMileage != null }.sumOf { it.endMileage!! - it.startMileage }
    }
    val currentTaxYearBusiness: Int get() {
        val tax = UkTaxYear.forDate(LocalDate.now())
        return businessEntriesChronological(tax).sumOf { it.endMileage!! - it.startMileage }
    }

    /** HMRC's tiered allowance for this tax year's business mileage so far, using [rateProfile]. */
    val currentTaxYearAllowancePence: Int get() {
        val tax = UkTaxYear.forDate(LocalDate.now())
        var cumulativeMiles = 0
        var totalPence = 0
        businessEntriesChronological(tax).forEach { entry ->
            val miles = entry.endMileage!! - entry.startMileage
            totalPence += HmrcMileageRates.estimateAllowancePence(rateProfile, cumulativeMiles, miles)
            cumulativeMiles += miles
        }
        return totalPence
    }

    /** The individual HMRC allowance for each of today's completed business mileage entries, keyed by entry id. */
    val todaysAllowanceByEntryId: Map<String, Int> get() {
        val tax = UkTaxYear.forDate(LocalDate.now())
        val today = LocalDate.now()
        var cumulativeMiles = 0
        val result = mutableMapOf<String, Int>()
        businessEntriesChronological(tax).forEach { entry ->
            val miles = entry.endMileage!! - entry.startMileage
            val pence = HmrcMileageRates.estimateAllowancePence(rateProfile, cumulativeMiles, miles)
            if (Instant.ofEpochMilli(entry.entryDate).atZone(zone).toLocalDate() == today) {
                result[entry.id] = pence
            }
            cumulativeMiles += miles
        }
        return result
    }

    // Flagged entries (invalid range, overlap, or an implausibly large jump) are excluded from
    // HMRC totals - an inconsistent reading shouldn't be claimed against until the driver fixes it.
    private fun businessEntriesChronological(tax: UkTaxYear): List<MileageEntryEntity> =
        entries
            .filter { inTaxYear(it, tax) && it.endMileage != null && it.purpose == MileagePurpose.BUSINESS && !it.isFlagged }
            .sortedBy { it.startedAt }

    private fun inTaxYear(entry: MileageEntryEntity, tax: UkTaxYear): Boolean =
        entry.entryDate >= tax.startMillis(zone) && entry.entryDate < tax.endMillisExclusive(zone)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MileageViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    mileageRepository: MileageRepository,
    private val hmrcRateRepository: HmrcRateRepository,
    clock: AppClock
) : ViewModel() {

    val state: StateFlow<MileageUiState> = vehicleRepository.observeActiveVehicle()
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(MileageUiState())
            } else {
                val taxYearStart = UkTaxYear.forDate(LocalDate.now()).startYear
                combine(
                    mileageRepository.observeFiltered(vehicle.id, null, null),
                    mileageRepository.observeFlagged(),
                    hmrcRateRepository.observeProfile(taxYearStart)
                ) { entries, flagged, rateProfile ->
                    MileageUiState(vehicle.id, entries, flagged.filter { it.vehicleId == vehicle.id }, rateProfile)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MileageUiState())

    fun saveRateOverride(taxYearStart: Int, profile: HmrcMileageRates.RateProfile) {
        viewModelScope.launch { hmrcRateRepository.setProfile(taxYearStart, profile) }
    }

    fun resetRateOverride(taxYearStart: Int) {
        viewModelScope.launch { hmrcRateRepository.resetToDefault(taxYearStart) }
    }
}

@Composable
fun MileageScreen(
    onAddEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: MileageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var hmrcExpanded by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    val taxYearStart = remember { UkTaxYear.forDate(LocalDate.now()).startYear }

    if (showRateDialog) {
        EditHmrcRateDialog(
            current = state.rateProfile,
            isDefault = state.rateProfile == HmrcMileageRates.defaultProfile(taxYearStart),
            onDismiss = { showRateDialog = false },
            onSave = { profile ->
                viewModel.saveRateOverride(taxYearStart, profile)
                showRateDialog = false
            },
            onResetToDefault = {
                viewModel.resetRateOverride(taxYearStart)
                showRateDialog = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val tax = UkTaxYear.forDate(java.time.LocalDate.now())
                SectionCard(modifier = Modifier.clickable { hmrcExpanded = !hmrcExpanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "HMRC Mileage · ${tax.label} · ${state.currentTaxYearBusiness} miles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (hmrcExpanded) {
                            IconButton(onClick = { showRateDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit HMRC rate")
                            }
                        }
                        Icon(if (hmrcExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                    if (hmrcExpanded) {
                        Spacer(Modifier.height(10.dp))
                        Text("Total miles this tax year: ${state.currentTaxYearTotal}", style = MaterialTheme.typography.bodyMedium)
                        Text("Business miles this tax year: ${state.currentTaxYearBusiness}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Estimated HMRC allowance: ${HmrcMileageRates.formatPence(state.currentTaxYearAllowancePence)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Based on HMRC's approved mileage rates: ${state.rateProfile.tier1Pence}p/mile for the first " +
                                "${state.rateProfile.thresholdMiles} business miles, ${state.rateProfile.tier2Pence}p/mile after. " +
                                "Tap the pencil to correct this if HMRC changes it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.flagged.isNotEmpty()) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.flagged.size} mileage entr${if (state.flagged.size == 1) "y needs" else "ies need"} review",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        state.flagged.forEach { entry ->
                            Text(
                                "${DateFormatting.formatDate(entry.entryDate)}: ${entry.flagReason}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable { onOpenEntry(entry.id) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            if (state.entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No mileage recorded yet",
                        message = "Record your start and end mileage to keep accurate records.",
                        actionLabel = "Add mileage",
                        onAction = onAddEntry,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    val todaysAllowancePence = state.todaysAllowanceByEntryId[entry.id]
                    SectionCard(modifier = Modifier.clickable { onOpenEntry(entry.id) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(DateFormatting.formatDate(entry.entryDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${entry.startMileage} → ${entry.endMileage ?: "in progress"}" +
                                        (entry.endMileage?.let { " (${it - entry.startMileage} miles)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (todaysAllowancePence != null) {
                                    Text(
                                        "HMRC allowance: ${HmrcMileageRates.formatPence(todaysAllowancePence)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            if (entry.isFlagged) {
                                StatusChip("Review", StatusTone.WARNING)
                            } else {
                                StatusChip(entry.purpose.name.lowercase().replaceFirstChar { it.uppercase() }, StatusTone.NEUTRAL)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add mileage")
        }
    }
}

@Composable
private fun EditHmrcRateDialog(
    current: HmrcMileageRates.RateProfile,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (HmrcMileageRates.RateProfile) -> Unit,
    onResetToDefault: () -> Unit
) {
    var tier1 by remember { mutableStateOf(current.tier1Pence.toString()) }
    var tier2 by remember { mutableStateOf(current.tier2Pence.toString()) }
    var threshold by remember { mutableStateOf(current.thresholdMiles.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HMRC mileage rate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Only change this if HMRC has published a different approved mileage allowance rate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tier1,
                    onValueChange = { tier1 = it.filter { c -> c.isDigit() } },
                    label = { Text("First-tier rate (pence/mile)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { c -> c.isDigit() } },
                    label = { Text("First-tier threshold (miles/year)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tier2,
                    onValueChange = { tier2 = it.filter { c -> c.isDigit() } },
                    label = { Text("Rate after threshold (pence/mile)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isDefault) {
                    TextButton(onClick = onResetToDefault) {
                        Text("Reset to CabComply default (45p/25p)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val profile = HmrcMileageRates.RateProfile(
                        tier1Pence = tier1.toIntOrNull()?.coerceAtLeast(0) ?: current.tier1Pence,
                        tier2Pence = tier2.toIntOrNull()?.coerceAtLeast(0) ?: current.tier2Pence,
                        thresholdMiles = threshold.toIntOrNull()?.coerceAtLeast(0) ?: current.thresholdMiles
                    )
                    onSave(profile)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
