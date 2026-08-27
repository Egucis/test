package uk.co.cabcomply.app.ui.documents

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType
import uk.co.cabcomply.app.data.db.entity.DocumentType
import uk.co.cabcomply.app.data.files.PhotoStorage
import uk.co.cabcomply.app.data.repository.DocumentRepository
import uk.co.cabcomply.app.ui.components.ConfirmDialog
import uk.co.cabcomply.app.ui.components.DateField
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import javax.inject.Inject

data class DocumentEditUiState(
    val documentId: String? = null,
    val ownerType: DocumentOwnerType = DocumentOwnerType.VEHICLE,
    val ownerId: String = "",
    val documentType: DocumentType = DocumentType.OTHER,
    val title: String = "",
    val referenceNumber: String = "",
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val notes: String = "",
    val remindersEnabled: Boolean = true,
    val error: String? = null,
    val isSaved: Boolean = false,
    val showDeleteConfirm: Boolean = false
)

@HiltViewModel
class DocumentEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val photoStorage: PhotoStorage
) : ViewModel() {

    private val documentIdArg: String? = savedStateHandle.get<String>("documentId")?.ifBlank { null }
    private val ownerTypeArg = DocumentOwnerType.valueOf(savedStateHandle.get<String>("ownerType") ?: "VEHICLE")
    private val ownerIdArg: String = savedStateHandle.get<String>("ownerId").orEmpty()

    private val _state = MutableStateFlow(DocumentEditUiState(ownerType = ownerTypeArg, ownerId = ownerIdArg))
    val state: StateFlow<DocumentEditUiState> = _state
    private var pendingAttachmentPath: String? = null

    init {
        if (documentIdArg != null) {
            viewModelScope.launch {
                documentRepository.getById(documentIdArg)?.let { doc ->
                    _state.value = DocumentEditUiState(
                        documentId = doc.id,
                        ownerType = doc.ownerType,
                        ownerId = doc.ownerId,
                        documentType = doc.documentType,
                        title = doc.title,
                        referenceNumber = doc.referenceNumber.orEmpty(),
                        issueDate = doc.issueDate,
                        expiryDate = doc.expiryDate,
                        notes = doc.notes.orEmpty(),
                        remindersEnabled = doc.remindersEnabled
                    )
                }
            }
        }
    }

    fun onTypeChange(v: DocumentType) { _state.value = _state.value.copy(documentType = v) }
    fun onTitleChange(v: String) { _state.value = _state.value.copy(title = v, error = null) }
    fun onReferenceChange(v: String) { _state.value = _state.value.copy(referenceNumber = v) }
    fun onIssueDateChange(v: Long?) { _state.value = _state.value.copy(issueDate = v) }
    fun onExpiryDateChange(v: Long?) { _state.value = _state.value.copy(expiryDate = v) }
    fun onNotesChange(v: String) { _state.value = _state.value.copy(notes = v) }
    fun onRemindersChange(v: Boolean) { _state.value = _state.value.copy(remindersEnabled = v) }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val stored = runCatching { photoStorage.importPhoto(uri) }.getOrNull() ?: return@launch
            pendingAttachmentPath = stored.relativePath
        }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "Enter a name for this document before saving.")
            return
        }
        viewModelScope.launch {
            documentRepository.saveDocument(
                id = s.documentId,
                ownerType = s.ownerType,
                ownerId = s.ownerId,
                documentType = s.documentType,
                title = s.title,
                referenceNumber = s.referenceNumber.ifBlank { null },
                issueDate = s.issueDate,
                expiryDate = s.expiryDate,
                notes = s.notes.ifBlank { null },
                remindersEnabled = s.remindersEnabled,
                attachmentRelativePath = pendingAttachmentPath
            )
            _state.value = _state.value.copy(isSaved = true)
        }
    }

    fun requestDelete() { _state.value = _state.value.copy(showDeleteConfirm = true) }
    fun dismissDelete() { _state.value = _state.value.copy(showDeleteConfirm = false) }

    fun delete() {
        val id = _state.value.documentId ?: return
        viewModelScope.launch {
            documentRepository.deleteDocument(id)
            _state.value = _state.value.copy(isSaved = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var typeExpanded by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.addAttachment(it) }
    }

    if (state.isSaved) {
        onDone()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (state.documentId == null) "Add document" else "Edit document",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (state.documentId != null) {
                IconButton(onClick = viewModel::requestDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete document")
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionCard {
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = state.documentType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Document type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    DocumentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { viewModel.onTypeChange(type); typeExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Name / title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.referenceNumber,
                onValueChange = viewModel::onReferenceChange,
                label = { Text("Reference / licence number (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            DateField(label = "Issue date (optional)", valueMillis = state.issueDate, onValueChange = viewModel::onIssueDateChange)
            Spacer(Modifier.height(12.dp))
            DateField(label = "Expiry date (optional)", valueMillis = state.expiryDate, onValueChange = viewModel::onExpiryDateChange)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.remindersEnabled, onCheckedChange = viewModel::onRemindersChange)
                Text("Remind me before this expires")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                Text("Attach photo of document (optional)")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(text = "Save", onClick = viewModel::save)
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Cancel", onClick = onCancel)
    }

    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = "Remove document",
            message = "This will remove \"${state.title}\" from your records. This cannot be undone.",
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete
        )
    }
}
