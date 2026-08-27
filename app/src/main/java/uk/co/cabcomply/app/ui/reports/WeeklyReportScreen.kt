package uk.co.cabcomply.app.ui.reports

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.cabcomply.app.data.pdf.ReportDataBuilder
import uk.co.cabcomply.app.data.pdf.WeeklyReportData
import uk.co.cabcomply.app.data.pdf.WeeklyReportPdfGenerator
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeeklyReportUiState(
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val report: WeeklyReportData? = null,
    val pdfFile: File? = null,
    val pageBitmaps: List<Bitmap> = emptyList(),
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

    /** Builds the week's data and immediately renders the actual sheet on screen — no extra tap needed. */
    private fun loadWeek() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, report = null, pdfFile = null, pageBitmaps = emptyList())
            val report = reportDataBuilder.buildWeeklyReport(vehicleId, _state.value.weekStart)
            _state.value = _state.value.copy(report = report)

            val file = withContext(Dispatchers.IO) { pdfGenerator.generate(report) }
            val bitmaps = withContext(Dispatchers.IO) { renderPdfPages(file) }
            _state.value = _state.value.copy(pdfFile = file, pageBitmaps = bitmaps, isLoading = false)
        }
    }

    fun previousWeek() { _state.value = _state.value.copy(weekStart = _state.value.weekStart.minusWeeks(1)); loadWeek() }
    fun nextWeek() { _state.value = _state.value.copy(weekStart = _state.value.weekStart.plusWeeks(1)); loadWeek() }

    private fun renderPdfPages(file: File, targetWidthPx: Int = 1080): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        }
        return bitmaps
    }
}

@Composable
fun WeeklyReportScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val report = state.report

    Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(8.dp))
            Text("Weekly Compliance Report", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::previousWeek) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week") }
            Text(
                report?.let { "${it.weekStartLabel} – ${it.weekEndLabel}" } ?: "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = viewModel::nextWeek) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next week") }
        }
        Spacer(Modifier.height(12.dp))

        if (state.isLoading || state.pageBitmaps.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing report…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.pageBitmaps) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Weekly compliance report page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(text = "Share / Print", onClick = {
                val file = state.pdfFile ?: return@PrimaryActionButton
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share weekly report"))
            })
            Spacer(Modifier.height(20.dp))
        }
    }
}
