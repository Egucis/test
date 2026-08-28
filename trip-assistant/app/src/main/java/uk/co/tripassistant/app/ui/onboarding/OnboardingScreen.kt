package uk.co.tripassistant.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    /**
     * Finishing onboarding records the privacy acknowledgement, because the screen-reading card is
     * where it is actually explained (spec sections 38 and 39).
     */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.acknowledgePrivacy()
            settings.setOnboardingComplete(true)
            onDone()
        }
    }
}

private data class OnboardingCard(
    val title: String,
    val body: String,
    val continueLabel: String = "Continue"
)

/**
 * Onboarding, spec section 38.
 *
 * One idea per card, in plain words, before Android asks for anything. The screen-reading card is
 * the prominent disclosure Google Play requires for this kind of access, and it is shown before
 * any permission dialog rather than alongside one (spec section 39).
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val cards = remember {
        listOf(
            OnboardingCard(
                title = "Decide faster",
                body = "When Uber shows you a trip offer, Trip Assistant works out what it is really " +
                    "worth — £ per mile including the pickup, £ per hour, and how much of the journey " +
                    "is unpaid — and tells you GOOD, BORDERLINE or POOR against your own rules."
            ),
            OnboardingCard(
                title = "Screen reading",
                body = "To evaluate trip offers, Trip Assistant needs permission to view the Uber " +
                    "screen while the assistant is running.\n\n" +
                    "Screen images are analysed on this device and are not saved in normal " +
                    "operation. Nothing is uploaded, nothing is sold, and no screenshots are stored.\n\n" +
                    "When Android asks what to share, choosing the Uber Driver app rather than the " +
                    "whole screen keeps your notifications and other apps out of it entirely."
            ),
            OnboardingCard(
                title = "A small floating window",
                body = "The recommendation appears in a compact window above Uber. You can drag it " +
                    "anywhere, and it stays clear of Uber's own Accept button.\n\n" +
                    "Trip Assistant never taps Accept or Decline for you. The decision is always yours."
            ),
            OnboardingCard(
                title = "Your rules, your trial",
                body = "Start from the Normal, Busy or Quiet profiles and change any threshold you " +
                    "like. Every rule can be switched off, treated as a guide, or made a " +
                    "dealbreaker.\n\nYou get 14 days free. After that a subscription keeps live " +
                    "evaluation running — your settings and history stay yours either way.",
                continueLabel = "Get started"
            )
        )
    }

    var index by remember { mutableIntStateOf(0) }
    val card = cards[index]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Step ${index + 1} of ${cards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSpace(12)

        SectionCard {
            Text(card.title, style = MaterialTheme.typography.headlineMedium)
            VerticalSpace(12)
            Text(
                card.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalSpace(20)
        Button(
            onClick = {
                if (index < cards.lastIndex) index++ else viewModel.complete(onFinished)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(card.continueLabel)
        }

        if (index > 0) {
            VerticalSpace(4)
            TextButton(onClick = { index-- }, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
