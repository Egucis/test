package uk.co.cabcomply.app.ui.reports

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.pdf.ReportDataBuilder
import uk.co.cabcomply.app.data.pdf.WeeklyReportData
import uk.co.cabcomply.app.data.pdf.WeeklyReportPdfGenerator
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeeklyReportUiState(
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val report: WeeklyReportData? = null,
    val pdfFile: File? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportDataBuilder: ReportDataBuilder,
    private val pdfGenerator: WeeklyReportPdfGenerator
) : ViewModel() {

    private val vehicleId: String = savedStateHandle.get<String>("vehicleId").orEmpty()
    private val _state = MutableStateFlow(WeeklyReportUiState())
    val state: StateFlow<WeeklyReportUiState> = _state

    init { loadWeek() }

    private fun loadWeek() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, pdfFile = null)
            val report = reportDataBuilder.buildWeeklyReport(vehicleId, _state.value.weekStart)
            _state.value = _state.value.copy(report = report, isLoading = false)
        }
    }

    fun previousWeek() { _state.value = _state.value.copy(weekStart = _state.value.weekStart.minusWeeks(1)); loadWeek() }
    fun nextWeek() { _state.value = _state.value.copy(weekStart = _state.value.weekStart.plusWeeks(1)); loadWeek() }

    fun generatePdf() {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            val file = pdfGenerator.generate(report)
            _state.value = _state.value.copy(pdfFile = file)
        }
    }
}

@Composable
fun WeeklyReportScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val report = state.report

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(8.dp))
            Text("Weekly Compliance Report", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::previousWeek) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week") }
            Text(
                report?.let { "${it.weekStartLabel} – ${it.weekEndLabel}" } ?: "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = viewModel::nextWeek) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next week") }
        }
        Spacer(Modifier.height(16.dp))

        if (state.isLoading || report == null) {
            CircularProgressIndicator()
        } else {
            SectionCard {
                Text("${report.vehicleRegistration} · ${report.vehicleMakeModel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Driver: ${report.driverName}", style = MaterialTheme.typography.bodyMedium)
                Text("Authority: ${report.licensingAuthorityName ?: "Not set"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                val completed = report.days.count { it.completed }
                Text("Checks completed: $completed of 7", style = MaterialTheme.typography.bodyMedium)
                Text("Mileage: ${report.mileageTotalMiles} miles (${report.mileageBusinessMiles} business)", style = MaterialTheme.typography.bodyMedium)
                Text("Defects: ${report.defects.size}", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            if (state.pdfFile == null) {
                PrimaryActionButton(text = "Generate PDF", onClick = viewModel::generatePdf)
            } else {
                PrimaryActionButton(text = "Share PDF", onClick = {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.pdfFile!!)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share weekly report"))
                })
            }
        }
    }
}
