package uk.co.cabcomply.app.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uk.co.cabcomply.app.R
import uk.co.cabcomply.app.data.security.AppLockManager
import uk.co.cabcomply.app.data.security.PinManager
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import javax.inject.Inject

@HiltViewModel
class PinLockViewModel @Inject constructor(
    val pinManager: PinManager,
    val appLockManager: AppLockManager
) : ViewModel()

/** Full-screen lock overlay shown whenever AppLockManager reports the app is locked. */
@Composable
fun PinLockScreen(viewModel: PinLockViewModel = hiltViewModel()) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painterResource(R.drawable.ic_cabcomply_logo), contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("Enter your PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryActionButton(text = "Unlock", onClick = {
                if (viewModel.pinManager.verifyPin(pin)) {
                    viewModel.appLockManager.unlock()
                } else {
                    error = "Incorrect PIN. Please try again."
                }
            })
        }
    }
}

@HiltViewModel
class PinSetupChangeViewModel @Inject constructor(
    val pinManager: PinManager
) : ViewModel()

@Composable
fun PinSetupScreen(onDone: () -> Unit, onCancel: () -> Unit, viewModel: PinSetupChangeViewModel = hiltViewModel()) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Set up a PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
            label = { Text("Choose a 4–8 digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8); error = null },
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(text = "Save PIN", onClick = {
            if (pin.length < 4) {
                error = "PIN must be at least 4 digits."
            } else if (pin != confirm) {
                error = "PINs do not match."
            } else {
                viewModel.pinManager.setPin(pin)
                onDone()
            }
        })
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
fun PinChangeScreen(onDone: () -> Unit, onCancel: () -> Unit, viewModel: PinSetupChangeViewModel = hiltViewModel()) {
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Change PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = current,
            onValueChange = { current = it.filter { c -> c.isDigit() }.take(8); error = null },
            label = { Text("Current PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
            label = { Text("New PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8); error = null },
            label = { Text("Confirm new PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(text = "Save new PIN", onClick = {
            when {
                pin.length < 4 -> error = "New PIN must be at least 4 digits."
                pin != confirm -> error = "New PINs do not match."
                !viewModel.pinManager.changePin(current, pin) -> error = "Current PIN is incorrect."
                else -> onDone()
            }
        })
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(text = "Cancel", onClick = onCancel)
    }
}
