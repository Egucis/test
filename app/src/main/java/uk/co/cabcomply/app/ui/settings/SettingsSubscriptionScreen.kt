package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.billing.EntitlementManager
import uk.co.cabcomply.app.data.billing.EntitlementTier
import uk.co.cabcomply.app.data.billing.ProFeature
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.util.DateFormatting
import javax.inject.Inject

@HiltViewModel
class SettingsSubscriptionViewModel @Inject constructor(
    val entitlementManager: EntitlementManager
) : ViewModel() {

    fun startTrialOrPurchase(activity: android.app.Activity) {
        viewModelScope.launch { entitlementManager.startPurchaseFlow(activity) }
    }

    fun restorePurchases() {
        viewModelScope.launch { entitlementManager.restorePurchases() }
    }
}

@Composable
fun SettingsSubscriptionScreen(viewModel: SettingsSubscriptionViewModel = hiltViewModel()) {
    val entitlement by viewModel.entitlementManager.entitlement.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Subscription", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionCard {
            val planLabel = when (entitlement.tier) {
                EntitlementTier.BASIC -> "CabComply Basic"
                EntitlementTier.PRO_TRIAL -> "CabComply Pro — Free trial"
                EntitlementTier.PRO_ACTIVE -> "CabComply Pro"
                EntitlementTier.GRACE -> "CabComply Pro — verifying"
                EntitlementTier.PRO_EXPIRED -> "Pro trial/subscription ended"
            }
            Text(planLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            entitlement.trialEndsAtMillis?.let {
                if (entitlement.tier == EntitlementTier.PRO_TRIAL) {
                    Text("Trial ends ${DateFormatting.formatDate(it)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (entitlement.tier == EntitlementTier.BASIC || entitlement.tier == EntitlementTier.PRO_EXPIRED) {
                Text(
                    "7-day free trial, then £4.95/month. Cancel any time — your data is never deleted if you stop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "Start free trial",
                    onClick = { (context as? android.app.Activity)?.let { viewModel.startTrialOrPurchase(it) } }
                )
            }
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(text = "Restore purchases", onClick = viewModel::restorePurchases)
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Text("What Pro unlocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            ProFeature.entries.forEach { feature ->
                Text("• ${feature.title}", fontWeight = FontWeight.Medium)
                Text(feature.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
