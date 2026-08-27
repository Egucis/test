package uk.co.cabcomply.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import uk.co.cabcomply.app.data.security.BiometricAuth
import uk.co.cabcomply.app.data.security.PinManager

/** Shared PIN gate for sensitive actions — record edits/deletes, Officer Mode exit (product spec section 44). */
@Composable
fun PinChallengeDialog(
    pinManager: PinManager,
    title: String = "Enter PIN",
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricEnabled = pinManager.biometricUnlockEnabled && activity != null && BiometricAuth.isAvailable(context)

    fun promptBiometric() {
        if (activity == null) return
        BiometricAuth.prompt(
            activity = activity,
            title = title,
            onSuccess = onSuccess,
            onError = { error = it }
        )
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) promptBiometric()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (biometricEnabled) {
                    TextButton(onClick = { promptBiometric() }) { Text("Use biometric unlock") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pinManager.verifyPin(pin)) onSuccess() else error = "Incorrect PIN."
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
