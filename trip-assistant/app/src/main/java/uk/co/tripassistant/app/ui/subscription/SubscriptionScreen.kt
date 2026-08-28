package uk.co.tripassistant.app.ui.subscription

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.tripassistant.app.data.billing.BillingAvailability
import uk.co.tripassistant.app.data.billing.SubscriptionOffer
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.core.entitlement.AccessLevel
import uk.co.tripassistant.core.entitlement.EntitlementStatus

/**
 * The subscription screen (spec section 4).
 *
 * Every figure comes from Google Play at runtime, and the four things Play requires to be stated
 * plainly — trial length, price afterwards, billing frequency and how to cancel — are on the card
 * next to the button, not buried in a link.
 */
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Subscription", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        CurrentStateCard(state)
        VerticalSpace(12)

        when {
            state.loading -> {
                SectionCard { CircularProgressIndicator() }
            }

            state.availability is BillingAvailability.Unavailable -> {
                SectionCard {
                    Text("Google Play is unavailable", style = MaterialTheme.typography.titleMedium)
                    VerticalSpace(6)
                    Text(
                        (state.availability as BillingAvailability.Unavailable).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VerticalSpace(12)
                    OutlinedButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            state.offers.isEmpty() -> {
                SectionCard {
                    Text("No subscription available", style = MaterialTheme.typography.titleMedium)
                    VerticalSpace(6)
                    Text(
                        "Google Play did not return a subscription for this account. This is normal " +
                            "on a build that is not installed from Play.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VerticalSpace(12)
                    OutlinedButton(onClick = viewModel::load) { Text("Try again") }
                }
            }

            else -> state.offers.forEach { offer ->
                OfferCard(
                    offer = offer,
                    onSubscribe = {
                        (context as? Activity)?.let { viewModel.purchase(it, offer) }
                    }
                )
                VerticalSpace(12)
            }
        }

        state.message?.let { message ->
            SectionCard {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                VerticalSpace(8)
                TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
            }
            VerticalSpace(12)
        }

        SectionCard {
            SectionHeading("Managing your subscription")
            Text(
                "Subscriptions renew automatically until you cancel. You can cancel at any time in " +
                    "the Google Play Store under Payments and subscriptions; cancelling stops the " +
                    "next renewal and you keep access until the period you have paid for ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(12)
            Text(
                if (state.backendVerified) {
                    "Your subscription is confirmed with Google Play and with Trip Assistant's " +
                        "entitlement service."
                } else {
                    "Your subscription is confirmed directly with Google Play on this device."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(12)
            OutlinedButton(onClick = viewModel::restorePurchases) { Text("Restore purchases") }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Terms and privacy")
            Text(
                "The Terms of Use and Privacy Policy apply to your subscription. The Privacy Policy " +
                    "explains screen reading, on-device processing and what is stored — a summary is " +
                    "in Settings › Privacy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }
}

@Composable
private fun CurrentStateCard(state: SubscriptionUiState) {
    val access = state.access
    SectionCard {
        SectionHeading("Your access")
        val headline = when {
            access == null -> "Checking…"
            access.status == EntitlementStatus.TRIAL && access.trialDaysRemaining != null ->
                "Free trial · ${access.trialDaysRemaining} ${if (access.trialDaysRemaining == 1) "day" else "days"} left"

            access.level == AccessLevel.FULL -> "Subscribed"
            access.level == AccessLevel.LOCKED_VERIFICATION_REQUIRED -> "Verification required"
            else -> "No active subscription"
        }
        Text(headline, style = MaterialTheme.typography.titleLarge)

        if (access != null && access.level == AccessLevel.FULL && access.offlineDaysRemaining != null) {
            VerticalSpace(8)
            Text(
                "Works offline for up to ${access.offlineDaysRemaining} more " +
                    if (access.offlineDaysRemaining == 1) "day." else "days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfferCard(offer: SubscriptionOffer, onSubscribe: () -> Unit) {
    SectionCard {
        Text(offer.title, style = MaterialTheme.typography.titleLarge)
        VerticalSpace(10)

        if (offer.hasFreeTrial) {
            Text(
                "${offer.freeTrialDays} days free, then ${offer.formattedPrice} per ${offer.billingPeriodLabel}",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                "${offer.formattedPrice} per ${offer.billingPeriodLabel}",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        VerticalSpace(6)
        Text(
            "Renews automatically every ${offer.billingPeriodLabel} until cancelled. " +
                if (offer.hasFreeTrial) {
                    "You will not be charged during the free trial, and you can cancel before it ends."
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        VerticalSpace(14)
        Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth()) {
            Text(if (offer.hasFreeTrial) "Start free trial" else "Subscribe")
        }
    }
}
