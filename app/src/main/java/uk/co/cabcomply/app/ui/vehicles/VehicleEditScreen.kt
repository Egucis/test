package uk.co.cabcomply.app.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity
import uk.co.cabcomply.app.data.repository.AuthorityRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.ui.components.DateField
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import javax.inject.Inject

data class VehicleEditFields(
    val vehicleId: String? = null,
    val registration: String = "",
    val make: String = "",
    val model: String = "",
    val licensingAuthorityId: String? = null,
    val plateNumber: String = "",
    val currentOdometer: String = "",
    val licenceExpiryDate: Long? = null,
    val error: String? = null,
    val isSaved: Boolean = false
)

data class VehicleEditUiState(
    val authorities: List<LicensingAuthorityEntity> = emptyList(),
    val fields: VehicleEditFields = VehicleEditFields()
)

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val authorityRepository: AuthorityRepository
) : ViewModel() {

    private val vehicleIdArg: String? = savedStateHandle.get<String>("vehicleId")?.ifBlank { null }
    private val fields = MutableStateFlow(VehicleEditFields(vehicleId = vehicleIdArg))

    val state: StateFlow<VehicleEditUiState> = combine(
        authorityRepository.observeAuthorities(),
        fields
    ) { authorities, f -> VehicleEditUiState(authorities, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleEditUiState())

    init {
        if (vehicleIdArg != null) {
            viewModelScope.launch {
                vehicleRepository.getById(vehicleIdArg)?.let { v ->
                    fields.value = VehicleEditFields(
                        vehicleId = v.id,
                        registration = v.registration,
                        make = v.make,
                        model = v.model,
                        licensingAuthorityId = v.licensingAuthorityId,
                        plateNumber = v.plateNumber.orEmpty(),
                        currentOdometer = v.currentOdometer.toString(),
                        licenceExpiryDate = v.licenceExpiryDate
                    )
                }
            }
        }
    }

    fun onRegistrationChange(v: String) { fields.value = fields.value.copy(registration = v, error = null) }
    fun onMakeChange(v: String) { fields.value = fields.value.copy(make = v, error = null) }
    fun onModelChange(v: String) { fields.value = fields.value.copy(model = v, error = null) }
    fun onAuthorityChange(v: String) { fields.value = fields.value.copy(licensingAuthorityId = v) }
    fun onPlateNumberChange(v: String) { fields.value = fields.value.copy(plateNumber = v) }
    fun onOdometerChange(v: String) { fields.value = fields.value.copy(currentOdometer = v.filter { it.isDigit() }, error = null) }
    fun onExpiryChange(v: Long?) { fields.value = fields.value.copy(licenceExpiryDate = v) }

    fun save() {
        val f = fields.value
        if (f.registration.isBlank() || f.make.isBlank() || f.model.isBlank()) {
            fields.value = f.copy(error = "Enter registration, make and model before saving this vehicle.")
            return
        }
        val odometer = f.currentOdometer.toIntOrNull()
        if (odometer == null) {
            fields.value = f.copy(error = "Enter the current odometer reading before saving.")
            return
        }
        viewModelScope.launch {
            vehicleRepository.saveVehicle(
                id = f.vehicleId,
                registration = f.registration,
                make = f.make,
                model = f.model,
                licensingAuthorityId = f.licensingAuthorityId,
                plateNumber = f.plateNumber,
                licenceExpiryDate = f.licenceExpiryDate,
                currentOdometer = odometer
            )
            fields.value = fields.value.copy(isSaved = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: VehicleEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val f = state.fields
    var authorityExpanded by remember { mutableStateOf(false) }

    if (f.isSaved) {
        onDone()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(if (f.vehicleId == null) "Add vehicle" else "Edit vehicle", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        SectionCard {
            OutlinedTextField(
                value = f.registration,
                onValueChange = viewModel::onRegistrationChange,
                label = { Text("Registration number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = f.make, onValueChange = viewModel::onMakeChange, label = { Text("Make") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = f.model, onValueChange = viewModel::onModelChange, label = { Text("Model") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            val selectedAuthority = state.authorities.firstOrNull { it.id == f.licensingAuthorityId }
            ExposedDropdownMenuBox(expanded = authorityExpanded, onExpandedChange = { authorityExpanded = it }) {
                OutlinedTextField(
                    value = selectedAuthority?.name ?: "Not set",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Licensing authority") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authorityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = authorityExpanded, onDismissRequest = { authorityExpanded = false }) {
                    state.authorities.forEach { authority ->
                        DropdownMenuItem(text = { Text(authority.name) }, onClick = { viewModel.onAuthorityChange(authority.id); authorityExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = f.plateNumber,
                onValueChange = viewModel::onPlateNumberChange,
                label = { Text("Vehicle licence / plate number (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = f.currentOdometer,
                onValueChange = viewModel::onOdometerChange,
                label = { Text("Current odometer (miles)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            DateField(label = "Licence expiry (optional)", valueMillis = f.licenceExpiryDate, onValueChange = viewModel::onExpiryChange)
        }

        f.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(text = "Save", onClick = viewModel::save)
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Cancel", onClick = onCancel)
    }
}
