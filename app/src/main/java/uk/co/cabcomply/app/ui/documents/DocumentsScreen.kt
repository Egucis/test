package uk.co.cabcomply.app.ui.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType
import uk.co.cabcomply.app.data.repository.DocumentExpiryStatus
import uk.co.cabcomply.app.data.repository.DocumentRepository
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.repository.expiryStatusFor
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import javax.inject.Inject

data class DocumentsUiState(
    val tab: DocumentOwnerType = DocumentOwnerType.VEHICLE,
    val ownerId: String? = null,
    val documents: List<DocumentEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val driverRepository: DriverRepository,
    private val vehicleRepository: VehicleRepository,
    private val clock: AppClock
) : ViewModel() {

    private val tab = MutableStateFlow(DocumentOwnerType.VEHICLE)

    val state: StateFlow<DocumentsUiState> = tab.flatMapLatest { t ->
        val ownerIdFlow = if (t == DocumentOwnerType.VEHICLE) {
            vehicleRepository.observeActiveVehicle().map { it?.id }
        } else {
            driverRepository.observeProfile().map { it?.id }
        }
        ownerIdFlow.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(DocumentsUiState(t, null, emptyList()))
            else documentRepository.observeForOwner(t, ownerId).map { docs -> DocumentsUiState(t, ownerId, docs) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

    fun selectTab(t: DocumentOwnerType) { tab.value = t }
    fun nowMillis() = clock.nowMillis()
}

@Composable
fun DocumentsScreen(
    onAddDocument: (ownerType: String, ownerId: String) -> Unit,
    onEditDocument: (documentId: String, ownerType: String, ownerId: String) -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabIndex = if (state.tab == DocumentOwnerType.VEHICLE) 0 else 1

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { viewModel.selectTab(DocumentOwnerType.VEHICLE) }, text = { Text("Vehicle") })
            Tab(selected = tabIndex == 1, onClick = { viewModel.selectTab(DocumentOwnerType.DRIVER) }, text = { Text("Driver") })
        }

        Box(Modifier.fillMaxSize()) {
            if (state.documents.isEmpty()) {
                EmptyState(
                    title = "No documents added yet",
                    message = "Add MOT, insurance, licences and other compliance documents here.",
                    actionLabel = if (state.ownerId != null) "Add document" else null,
                    onAction = state.ownerId?.let { { onAddDocument(state.tab.name, it) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        val status = expiryStatusFor(doc.expiryDate, viewModel.nowMillis())
                        SectionCard(modifier = Modifier.clickable {
                            onEditDocument(doc.id, state.tab.name, state.ownerId.orEmpty())
                        }) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(doc.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        doc.expiryDate?.let { "Expires ${DateFormatting.formatDate(it)}" } ?: "No expiry set",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                when (status) {
                                    DocumentExpiryStatus.EXPIRED -> StatusChip("Expired", StatusTone.DANGER)
                                    DocumentExpiryStatus.EXPIRING_SOON -> StatusChip("Expiring soon", StatusTone.WARNING)
                                    DocumentExpiryStatus.VALID -> StatusChip("Valid", StatusTone.SUCCESS)
                                    DocumentExpiryStatus.NO_EXPIRY -> Unit
                                }
                            }
                        }
                    }
                }
            }

            if (state.ownerId != null) {
                FloatingActionButton(
                    onClick = { onAddDocument(state.tab.name, state.ownerId!!) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add document")
                }
            }
        }
    }
}
