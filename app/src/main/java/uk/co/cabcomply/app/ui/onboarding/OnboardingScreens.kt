package uk.co.cabcomply.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.co.cabcomply.app.R
import uk.co.cabcomply.app.data.seed.AuthoritySeedData
import uk.co.cabcomply.app.ui.components.DateField
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton

@Composable
fun OnboardingWelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cabcomply_logo),
            contentDescription = "CabComply logo",
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("CabComply", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Built by a driver, for drivers.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Complete daily vehicle checks, track defects and mileage, keep your documents in order, " +
                "and produce professional compliance reports — all in one place, and it works without signal.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))
        PrimaryActionButton(text = "Continue", onClick = onContinue)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDriverScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Your details", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Only what's useful for your records and reports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.driverName,
            onValueChange = viewModel::onDriverNameChange,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        val selectedAuthority = state.authorities.firstOrNull { it.id == state.selectedAuthorityId }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedAuthority?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Licensing authority") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.authorities.forEach { authority ->
                    DropdownMenuItem(
                        text = { Text(authority.name) },
                        onClick = {
                            viewModel.onAuthoritySelected(authority.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (state.selectedAuthorityId == AuthoritySeedData.CUSTOM_AUTHORITY_ID) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.customAuthorityName,
                onValueChange = viewModel::onCustomAuthorityNameChange,
                label = { Text("Name your licensing authority") },
                supportingText = { Text("This will be clearly labelled as a custom entry, not an official checklist.") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.badgeNumber,
            onValueChange = viewModel::onBadgeNumberChange,
            label = { Text("Badge / licence number (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        state.driverError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(32.dp))
        PrimaryActionButton(text = "Continue", onClick = { viewModel.saveDriverStep(onNext) }, enabled = !state.isSaving)
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Back", onClick = onBack)
    }
}

@Composable
fun OnboardingVehicleScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Your vehicle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add at least one vehicle to get started. You can add more later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.registration,
            onValueChange = viewModel::onRegistrationChange,
            label = { Text("Registration number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.make,
                onValueChange = viewModel::onMakeChange,
                label = { Text("Make") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = { Text("Model") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.plateNumber,
            onValueChange = viewModel::onPlateNumberChange,
            label = { Text("Vehicle licence / plate number (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.currentOdometer,
            onValueChange = viewModel::onOdometerChange,
            label = { Text("Current odometer (miles)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        DateField(
            label = "Vehicle licence expiry (optional)",
            valueMillis = state.licenceExpiryDate,
            onValueChange = viewModel::onLicenceExpiryChange
        )

        state.vehicleError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(32.dp))
        PrimaryActionButton(text = "Continue", onClick = { viewModel.saveVehicleStep(onNext) }, enabled = !state.isSaving)
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Back", onClick = onBack)
    }
}

@Composable
fun OnboardingSecurityScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Protect your records", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Optional. You can turn this on or off at any time in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.pinEnabled, onCheckedChange = viewModel::onPinEnabledChange)
            Text("Protect CabComply with a PIN")
        }

        if (state.pinEnabled) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.pin,
                onValueChange = viewModel::onPinChange,
                label = { Text("Choose a 4–8 digit PIN") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.pinConfirm,
                onValueChange = viewModel::onPinConfirmChange,
                label = { Text("Confirm PIN") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.pinError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(32.dp))
        PrimaryActionButton(text = "Continue", onClick = { viewModel.saveSecurityStep(onNext) })
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Back", onClick = onBack)
    }
}

@Composable
fun OnboardingFinishScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("You're all set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your driver profile and vehicle are ready. Your first daily check is just one tap from Home.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        PrimaryActionButton(text = "Go to Home", onClick = onFinish)
    }
}
