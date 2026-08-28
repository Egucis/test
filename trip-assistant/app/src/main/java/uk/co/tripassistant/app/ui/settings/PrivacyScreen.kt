package uk.co.tripassistant.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.VerticalSpace
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {
    fun acknowledge(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.acknowledgePrivacy()
            onDone()
        }
    }
}

/**
 * The in-app privacy explanation (spec sections 39 and 50).
 *
 * This is the disclosure, not the Privacy Policy: the published policy lives on the web and is
 * linked from the subscription screen. What is written here has to match what the code actually
 * does, and it does — see the notes in AssistantService and OnDeviceTextRecognizer.
 */
@Composable
fun PrivacyScreen(
    onDone: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        PrivacyCard(
            title = "What is captured",
            body = "While the assistant is running, Android shares your screen — or, if you choose " +
                "it, just the Uber Driver window — with Trip Assistant. Android shows a notice the " +
                "whole time this is happening, and you can stop it at any moment."
        )
        VerticalSpace(12)

        PrivacyCard(
            title = "What happens to it",
            body = "Each frame is examined on this device to find the numbers on a trip offer, and " +
                "is then discarded. Frames are never written to storage and never leave the phone. " +
                "There is no cloud text recognition in this app."
        )
        VerticalSpace(12)

        PrivacyCard(
            title = "What is kept",
            body = "If history is switched on, the assistant stores the economics of each offer — " +
                "fare, distances, times, rider rating, the calculated rates, the recommendation and " +
                "the reason. It does not store screenshots, rider names, messages, or pickup and " +
                "destination addresses."
        )
        VerticalSpace(12)

        PrivacyCard(
            title = "What is sent anywhere",
            body = "Only subscription information. Google Play handles your payment, and a purchase " +
                "token may be sent to Trip Assistant's entitlement service over an encrypted " +
                "connection to confirm your subscription is active. Screen content is never sent, " +
                "never used for advertising and never sold."
        )
        VerticalSpace(12)

        PrivacyCard(
            title = "Deleting your data",
            body = "Settings › History lets you delete every recorded offer at once. Deleting your " +
                "history does not affect your settings or your subscription."
        )

        VerticalSpace(24)
        Button(
            onClick = { viewModel.acknowledge(onDone) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I understand")
        }
        VerticalSpace(24)
    }
}

@Composable
private fun PrivacyCard(title: String, body: String) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        VerticalSpace(8)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
