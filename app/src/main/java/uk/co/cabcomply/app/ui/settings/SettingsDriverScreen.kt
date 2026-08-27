package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import javax.inject.Inject

data class SettingsDriverFields(val name: String = "", val authorityId: String? = null, val badgeNumber: String = "", val saved: Boolean = false)

@HiltViewModel
class SettingsDriverViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val authorityRepository: AuthorityRepository
) : ViewModel() {

    private val fields = MutableStateFlow(SettingsDriverFields())

    val state: StateFlow<Pair<List<LicensingAuthorityEntity>, SettingsDriverFields>> = combine(
        authorityRepository.observeAuthorities(), fields
    ) { authorities, f -> authorities to f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<LicensingAuthorityEntity>() to SettingsDriverFields())

    init {
        viewModelScope.launch {
            driverRepository.getProfile()?.let {
                fields.value = SettingsDriverFields(it.name, it.licensingAuthorityId, it.badgeNumber.orEmpty())
            }
        }
    }

    fun onNameChange(v: String) { fields.value = fields.value.copy(name = v) }
    fun onAuthorityChange(v: String) { fields.value = fields.value.copy(authorityId = v) }
    fun onBadgeChange(v: String) { fields.value = fields.value.copy(badgeNumber = v) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val f = fields.value
            driverRepository.saveProfile(f.name, f.authorityId, f.badgeNumber.ifBlank { null })
            fields.value = f.copy(saved = true)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDriverScreen(onSaved: () -> Unit, viewModel: SettingsDriverViewModel = hiltViewModel()) {
    val (authorities, f) = viewModel.state.collectAsState().value
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Driver", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        SectionCard {
            OutlinedTextField(value = f.name, onValueChange = viewModel::onNameChange, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            val selected = authorities.firstOrNull { it.id == f.authorityId }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected?.name ?: "Not set",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Licensing authority") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    authorities.forEach { a ->
                        DropdownMenuItem(text = { Text(a.name) }, onClick = { viewModel.onAuthorityChange(a.id); expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = f.badgeNumber, onValueChange = viewModel::onBadgeChange, label = { Text("Badge / licence number (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        PrimaryActionButton(text = "Save", onClick = { viewModel.save(onSaved) })
    }
}
