package uk.co.cabcomply.app.ui.officer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.data.pdf.ReportDataBuilder
import uk.co.cabcomply.app.data.pdf.WeeklyReportPdfGenerator
import uk.co.cabcomply.app.data.repository.DocumentExpiryStatus
import uk.co.cabcomply.app.data.repository.DocumentRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.repository.expiryStatusFor
import uk.co.cabcomply.app.data.security.PinManager
import uk.co.cabcomply.app.ui.components.PinChallengeDialog
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import uk.co.cabcomply.app.util.PdfPageRenderer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class OfficerUiState(
    val vehicle: VehicleEntity? = null,
    val recentInspections: List<InspectionEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList()
)

/** The current week's official-format sheet, rasterised so it renders the instant Officer Mode
 *  opens - matching what a licensing officer expects to see on paper (product spec sections 38-40). */
data class OfficerSheetState(
    val pageBitmaps: List<android.graphics.Bitmap> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfficerViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    inspectionRepository: InspectionRepository,
    documentRepository: DocumentRepository,
    private val reportDataBuilder: ReportDataBuilder,
    private val pdfGenerator: WeeklyReportPdfGenerator,
    val pinManager: PinManager,
    private val clock: AppClock
) : ViewModel() {

    val nowMillis = clock.nowMillis()

    val state: StateFlow<OfficerUiState> = vehicleRepository.observeActiveVehicle()
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(OfficerUiState())
            else combine(
                inspectionRepository.observeHistory(vehicle.id, clock.nowMillis() - TimeUnit.DAYS.toMillis(30), clock.nowMillis()),
                documentRepository.observeForOwner(DocumentOwnerType.VEHICLE, vehicle.id)
            ) { inspections, documents ->
                OfficerUiState(vehicle, inspections.take(14), documents)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfficerUiState())

    private val _sheetState = MutableStateFlow(OfficerSheetState())
    val sheetState: StateFlow<OfficerSheetState> = _sheetState

    init {
        viewModelScope.launch {
            vehicleRepository.observeActiveVehicle().map { it?.id }.distinctUntilChanged().collect { vehicleId ->
                _sheetState.value = OfficerSheetState(isLoading = true)
                if (vehicleId == null) {
                    _sheetState.value = OfficerSheetState(isLoading = false)
                    return@collect
                }
                val weekStart = LocalDate.now(clock.zoneId()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val report = reportDataBuilder.buildWeeklyReport(vehicleId, weekStart)
                val bitmaps = withContext(Dispatchers.IO) {
                    PdfPageRenderer.renderPages(pdfGenerator.generate(report))
                }
                _sheetState.value = OfficerSheetState(pageBitmaps = bitmaps, isLoading = false)
            }
        }
    }
}

@Composable
fun OfficerModeScreen(
    onGenerateReport: (vehicleId: String) -> Unit,
    onExit: () -> Unit,
    viewModel: OfficerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState by viewModel.sheetState.collectAsState()
    var pinChallengeVisible by remember { mutableStateOf(false) }

    // The whole point of Officer Mode is that leaving it is deliberately hard (hold 5s, PIN if
    // enabled) - the system Back button/gesture must never be a silent bypass of that.
    BackHandler(enabled = true) {}

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Officer View — Read Only",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard {
                    Text("Vehicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(state.vehicle?.let { "${it.registration} · ${it.make} ${it.model}" } ?: "No vehicle", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Text("This week's compliance sheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (sheetState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(sheetState.pageBitmaps) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "This week's compliance sheet",
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Browse other weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Opens the full weekly report, with paging and share/print.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { state.vehicle?.let { onGenerateReport(it.id) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open weekly report") }
                }
            }
            item {
                Text("Recent checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.recentInspections, key = { it.id }) { inspection ->
                SectionCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(DateFormatting.formatDate(inspection.inspectionDate), style = MaterialTheme.typography.titleMedium)
                            Text("${inspection.odometer} miles", style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusChip("Completed", StatusTone.SUCCESS)
                    }
                }
            }
            item {
                Text("Documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.documents, key = { it.id }) { doc ->
                SectionCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(doc.title, style = MaterialTheme.typography.titleMedium)
                        when (expiryStatusFor(doc.expiryDate, viewModel.nowMillis)) {
                            DocumentExpiryStatus.EXPIRED -> StatusChip("Expired", StatusTone.DANGER)
                            DocumentExpiryStatus.EXPIRING_SOON -> StatusChip("Expiring soon", StatusTone.WARNING)
                            DocumentExpiryStatus.VALID -> StatusChip("Valid", StatusTone.SUCCESS)
                            DocumentExpiryStatus.NO_EXPIRY -> Unit
                        }
                    }
                }
            }
        }

        HoldToExitBar(onHeld = {
            if (viewModel.pinManager.recordProtectionEnabled) pinChallengeVisible = true else onExit()
        })
    }

    if (pinChallengeVisible) {
        PinChallengeDialog(
            pinManager = viewModel.pinManager,
            title = "Enter PIN to exit Officer Mode",
            onSuccess = { pinChallengeVisible = false; onExit() },
            onCancel = { pinChallengeVisible = false }
        )
    }
}

@Composable
private fun HoldToExitBar(onHeld: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val holdMillis = 5000L
            val stepMillis = 50L
            var elapsed = 0L
            while (isPressed && elapsed < holdMillis) {
                delay(stepMillis)
                elapsed += stepMillis
                progress = elapsed.toFloat() / holdMillis
            }
            if (elapsed >= holdMillis) {
                onHeld()
            }
        }
        if (!isPressed) progress = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.error
        )
        Text(
            "Hold for 5 seconds to exit Officer Mode",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
