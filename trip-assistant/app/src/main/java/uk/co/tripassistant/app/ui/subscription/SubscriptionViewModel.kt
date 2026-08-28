package uk.co.tripassistant.app.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.billing.BillingAvailability
import uk.co.tripassistant.app.data.billing.BillingDataSource
import uk.co.tripassistant.app.data.billing.EntitlementRepository
import uk.co.tripassistant.app.data.billing.SubscriptionOffer
import uk.co.tripassistant.core.entitlement.AccessDecision
import javax.inject.Inject

data class SubscriptionUiState(
    val loading: Boolean = true,
    val availability: BillingAvailability = BillingAvailability.Connecting,
    val offers: List<SubscriptionOffer> = emptyList(),
    val access: AccessDecision? = null,
    val trialDaysConfigured: Int = 14,
    val backendVerified: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billing: BillingDataSource,
    private val entitlement: EntitlementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        SubscriptionUiState(
            trialDaysConfigured = entitlement.config.trialDurationDays,
            backendVerified = entitlement.hasBackendVerification
        )
    )
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            // Play tells us when a purchase completes; entitlement is then re-read from scratch
            // rather than inferred from the callback.
            billing.purchaseUpdates.collect { refreshEntitlement() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val offers = runCatching { billing.subscriptionOffers() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                loading = false,
                offers = offers,
                availability = billing.availability.value,
                access = entitlement.currentAccess()
            )
        }
    }

    fun purchase(activity: Activity, offer: SubscriptionOffer) {
        viewModelScope.launch {
            val result = billing.launchPurchase(activity, offer)
            result.exceptionOrNull()?.let { error ->
                _state.value = _state.value.copy(message = error.message ?: "Could not start the purchase")
            }
        }
    }

    /** "Restore purchases" (spec section 4) — the same verification, asked for on demand. */
    fun restorePurchases() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val access = runCatching { entitlement.restorePurchases() }.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                access = access ?: entitlement.currentAccess(),
                message = if (access == null) "Could not reach Google Play. Try again when you have a connection." else null
            )
        }
    }

    private fun refreshEntitlement() {
        viewModelScope.launch {
            runCatching { entitlement.refresh() }
            _state.value = _state.value.copy(access = entitlement.currentAccess())
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
