package uk.co.cabcomply.app.ui.dailycheck

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.ui.components.ConfirmDialog
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import java.io.File

@Composable
fun DailyCheckScreen(
    onDone: (inspectionId: String) -> Unit,
    onCancel: () -> Unit,
    onViewExisting: (inspectionId: String) -> Unit,
    viewModel: DailyCheckViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    state.completedInspectionId?.let {
        onDone(it)
        return
    }

    if (state.isLoading) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.existingCompletedInspection != null && !state.duplicateWarningDismissed) {
        ConfirmDialog(
            title = "Already completed today",
            message = "Today's vehicle check has already been completed for this vehicle. You can view the existing check or create another one.",
            confirmLabel = "Create another",
            onConfirm = { viewModel.dismissDuplicateWarning() },
            onDismiss = { onViewExisting(state.existingCompletedInspection!!.id) }
        )
    }

    Column(Modifier.fillMaxSize()) {
        DailyCheckTopBar(state, onCancel)
        when (state.step) {
            DailyCheckStep.ODOMETER -> OdometerStep(state, viewModel, onCancel)
            DailyCheckStep.CHECKLIST -> ChecklistStep(state, viewModel)
            DailyCheckStep.REVIEW -> ReviewStep(state, viewModel)
        }
    }
}

@Composable
private fun DailyCheckTopBar(state: DailyCheckUiState, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                if (state.isQuickCheck) "Quick Check" else "Daily Vehicle Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            state.vehicle?.let {
                Text("${it.registration} · ${it.make} ${it.model}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (state.step == DailyCheckStep.CHECKLIST) {
        LinearProgressIndicator(
            progress = { if (state.items.isEmpty()) 0f else state.checkedCount.toFloat() / state.items.size },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun OdometerStep(state: DailyCheckUiState, viewModel: DailyCheckViewModel, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        if (state.openDefectsFromBefore.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This vehicle has ${state.openDefectsFromBefore.size} unresolved defect" +
                            "${if (state.openDefectsFromBefore.size == 1) "" else "s"} from a previous check.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SectionCard {
            Text("Odometer reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.odometerText,
                onValueChange = viewModel::onOdometerChange,
                label = { Text("Current mileage") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.validationError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(text = "Continue to checklist", onClick = viewModel::continueFromOdometer)
    }
}

@Composable
private fun ChecklistStep(state: DailyCheckUiState, viewModel: DailyCheckViewModel) {
    val grouped = remember(state.items) { state.items.groupBy { it.category }.toList() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "${state.checkedCount} of ${state.items.size} checked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        grouped.forEach { (category, categoryItems) ->
            item {
                Text(
                    category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(categoryItems, key = { it.id }) { item ->
                ChecklistItemRow(item, viewModel)
            }
        }
        item {
            state.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(text = "Continue to review", onClick = viewModel::continueFromChecklist)
        }
    }
}

@Composable
private fun ChecklistItemRow(item: ChecklistItemUi, viewModel: DailyCheckViewModel) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.addDefectPhoto(item.id, it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.addDefectPhoto(item.id, it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraOutputUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                item.helpText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val okSelected = item.status == InspectionResultStatus.OK
            val defectSelected = item.status == InspectionResultStatus.DEFECT
            Button(
                onClick = { viewModel.markItemOk(item.id) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (okSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (okSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("OK")
            }
            Button(
                onClick = { viewModel.markItemDefect(item.id) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (defectSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (defectSelected) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Defect")
            }
        }

        if (item.status == InspectionResultStatus.DEFECT) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = item.defectDescription,
                onValueChange = { viewModel.onDefectDescriptionChange(item.id, it) },
                label = { Text("Describe the defect") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.defectThumbnailPaths.forEachIndexed { index, path ->
                    Box {
                        AsyncImage(
                            model = File(context.filesDir, path),
                            contentDescription = "Defect photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        IconButton(
                            onClick = { viewModel.removeDefectPhoto(item.id, index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val uri = createCameraOutputUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("Choose")
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(state: DailyCheckUiState, viewModel: DailyCheckViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        SectionCard {
            Text("Review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Vehicle: ${state.vehicle?.registration}")
            Text("Mileage: ${state.odometerText}")
            Text("OK items: ${state.items.count { it.status == InspectionResultStatus.OK }}")
            Text(
                "Defects: ${state.defectCount}",
                color = if (state.defectCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard {
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = state.driverConfirmed, onCheckedChange = viewModel::onConfirmChange)
            Text(
                "I confirm that I have physically inspected this vehicle and that this record accurately reflects its condition at the time of inspection.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        state.validationError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryActionButton(
            text = "Complete Vehicle Check",
            onClick = viewModel::completeInspection,
            enabled = state.driverConfirmed && !state.isSaving
        )
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Back to checklist", onClick = viewModel::backToChecklist)
    }
}

private fun createCameraOutputUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "defect_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
