package uk.co.cabcomply.app.ui.defects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.DateFormatting
import javax.inject.Inject

data class DefectsUiState(
    val filter: DefectStatus? = DefectStatus.OPEN,
    val defects: List<DefectEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DefectsViewModel @Inject constructor(
    private val defectRepository: DefectRepository
) : ViewModel() {

    private val filter = MutableStateFlow<DefectStatus?>(DefectStatus.OPEN)

    val state: StateFlow<DefectsUiState> = filter
        .flatMapLatest { f -> defectRepository.observeFiltered(f, null).map { defects -> DefectsUiState(f, defects) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefectsUiState())

    fun setFilter(f: DefectStatus?) { filter.value = f }
}

@Composable
fun DefectsScreen(onOpenDefect: (String) -> Unit, viewModel: DefectsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = state.filter == DefectStatus.OPEN, onClick = { viewModel.setFilter(DefectStatus.OPEN) }, label = { Text("Open") })
            FilterChip(selected = state.filter == DefectStatus.RESOLVED, onClick = { viewModel.setFilter(DefectStatus.RESOLVED) }, label = { Text("Resolved") })
            FilterChip(selected = state.filter == null, onClick = { viewModel.setFilter(null) }, label = { Text("All") })
        }

        if (state.defects.isEmpty()) {
            EmptyState(
                title = if (state.filter == DefectStatus.OPEN) "No open defects" else "No defects found",
                message = "Defects recorded during daily checks will appear here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.defects, key = { it.id }) { defect ->
                    SectionCard(modifier = Modifier.clickable { onOpenDefect(defect.id) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(defect.checklistItemNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(defect.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    DateFormatting.formatDate(defect.reportedAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(
                                text = if (defect.status == DefectStatus.OPEN) "Open" else "Resolved",
                                tone = if (defect.status == DefectStatus.OPEN) StatusTone.DANGER else StatusTone.SUCCESS
                            )
                        }
                    }
                }
            }
        }
    }
}
