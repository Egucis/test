package uk.co.cabcomply.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import uk.co.cabcomply.app.util.DateFormatting

/** A tap-to-open Material date picker, used for every expiry/issue date field in CabComply (UK dd/MM/yyyy display). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    valueMillis: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valueMillis?.let { DateFormatting.formatDate(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("DD/MM/YYYY") },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text("Select") }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = valueMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(state.selectedDateMillis)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
                DatePicker(state = state)
        }
    }
}
