package uk.co.cabcomply.app.ui.defects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.AttachmentEntity
import uk.co.cabcomply.app.data.db.entity.AttachmentOwnerType
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.files.PhotoStorage
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.DateFormatting
import java.io.File
import javax.inject.Inject

data class DefectDetailUiState(
    val defect: DefectEntity? = null,
    val evidencePhotos: List<AttachmentEntity> = emptyList(),
    val resolutionNote: String = "",
    val resolutionPhotoPath: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DefectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val defectRepository: DefectRepository,
    private val photoStorage: PhotoStorage
) : ViewModel() {

    private val defectId: String = savedStateHandle.get<String>("defectId").orEmpty()
    private val _state = MutableStateFlow(DefectDetailUiState())
    val state: StateFlow<DefectDetailUiState> = _state

    init {
        viewModelScope.launch {
            defectRepository.observeById(defectId).collect { defect ->
                _state.value = _state.value.copy(defect = defect, isLoading = false)
            }
        }
        viewModelScope.launch {
            defectRepository.observeAttachments(AttachmentOwnerType.DEFECT, defectId).collect { photos ->
                _state.value = _state.value.copy(evidencePhotos = photos)
            }
        }
    }

    fun onResolutionNoteChange(v: String) { _state.value = _state.value.copy(resolutionNote = v) }

    fun addResolutionPhoto(uri: Uri) {
        viewModelScope.launch {
            val stored = runCatching { photoStorage.importPhoto(uri) }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(resolutionPhotoPath = stored.relativePath)
        }
    }

    fun resolve(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            defectRepository.resolveDefect(
                id = defectId,
                resolutionNote = s.resolutionNote.ifBlank { null },
                resolutionPhotoRelativePaths = listOfNotNull(s.resolutionPhotoPath)
            )
            onDone()
        }
    }
}

@Composable
fun DefectDetailScreen(onBack: () -> Unit, viewModel: DefectDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.addResolutionPhoto(it) }
    }

    val defect = state.defect
    if (state.isLoading || defect == null) {
        Column(Modifier.fillMaxSize()) { Text("Loading…", modifier = Modifier.padding(20.dp)) }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(defect.checklistItemNameSnapshot, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Reported ${DateFormatting.formatDate(defect.reportedAt)}", style = MaterialTheme.typography.bodyMedium)
                }
                StatusChip(
                    text = if (defect.status == DefectStatus.OPEN) "Open" else "Resolved",
                    tone = if (defect.status == DefectStatus.OPEN) StatusTone.DANGER else StatusTone.SUCCESS
                )
            }
        }
        item {
            SectionCard {
                Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(defect.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.evidencePhotos.isNotEmpty()) {
            item {
                SectionCard {
                    Text("Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.evidencePhotos.forEach { photo ->
                            AsyncImage(
                                model = File(context.filesDir, photo.filePath),
                                contentDescription = "Defect evidence photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }
            }
        }
        if (defect.status == DefectStatus.OPEN) {
            item {
                SectionCard {
                    Text("Resolve this defect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.resolutionNote,
                        onValueChange = viewModel::onResolutionNoteChange,
                        label = { Text("Resolution note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text(if (state.resolutionPhotoPath == null) "Add resolution photo (optional)" else "Photo added")
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryActionButton(text = "Mark as Resolved", onClick = { viewModel.resolve(onBack) })
                }
            }
        } else {
            item {
                SectionCard {
                    Text("Resolution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Resolved ${defect.resolvedAt?.let { DateFormatting.formatDateTime(it) } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                    defect.resolutionNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
