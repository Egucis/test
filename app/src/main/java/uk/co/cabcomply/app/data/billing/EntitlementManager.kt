package uk.co.cabcomply.app.data.billing

import android.app.Activity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.util.AppClock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_TIER = stringPreferencesKey("tier")
private val KEY_PRO_SINCE = longPreferencesKey("pro_since")
private val KEY_LAST_VERIFIED = longPreferencesKey("last_verified")

private val GRACE_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(3)
private val EXPIRE_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(14)
private val TRIAL_LENGTH_MILLIS = TimeUnit.DAYS.toMillis(7)

/**
 * The single source of truth for Basic/Pro state. Every screen reads [entitlement] instead of
 * touching Play Billing itself (product spec section 56). Downgrading never deletes data —
 * this class only ever changes what is *unlocked*, never what is stored (product spec section 55/90).
 */
@Singleton
class EntitlementManager @Inject constructor(
    @ApplicationContext context: android.content.Context,
    private val billingRepository: BillingRepository,
    private val clock: AppClock
) {
    private val dataStore = context.entitlementDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entitlement = MutableStateFlow(EntitlementSnapshot(EntitlementTier.BASIC))
    val entitlement: StateFlow<EntitlementSnapshot> = _entitlement

    init {
        scope.launch {
            _entitlement.value = readCached()
            billingRepository.purchaseUpdatesFlow.collect { purchases ->
                applyPurchases(purchases)
            }
        }
    }

    suspend fun refresh() {
        val purchases = billingRepository.queryActiveSubscriptionPurchases()
        if (purchases == null) {
            _entitlement.value = degradeForUnverifiedState(readCached())
        } else {
            applyPurchases(purchases)
        }
    }

    suspend fun startPurchaseFlow(activity: Activity): Boolean {
        val details = billingRepository.queryProSubscriptionDetails() ?: return false
        return billingRepository.launchPurchaseFlow(activity, details)
    }

    suspend fun restorePurchases() = refresh()

    private suspend fun applyPurchases(purchases: List<Purchase>) {
        val proPurchase = purchases.firstOrNull { purchase ->
            purchase.products.contains(BillingRepository.PRO_MONTHLY_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (proPurchase == null) {
            persist(EntitlementTier.BASIC, proSince = null)
            return
        }

        billingRepository.acknowledgePurchase(proPurchase)

        val now = clock.nowMillis()
        val proSince = proPurchase.purchaseTime
        val tier = if (now - proSince < TRIAL_LENGTH_MILLIS) EntitlementTier.PRO_TRIAL else EntitlementTier.PRO_ACTIVE
        persist(tier, proSince)
    }

    private suspend fun persist(tier: EntitlementTier, proSince: Long?) {
        val now = clock.nowMillis()
        dataStore.edit { prefs ->
            prefs[KEY_TIER] = tier.name
            prefs[KEY_LAST_VERIFIED] = now
            if (proSince != null) prefs[KEY_PRO_SINCE] = proSince else prefs.remove(KEY_PRO_SINCE)
        }
        _entitlement.value = EntitlementSnapshot(
            tier = tier,
            proSinceMillis = proSince,
            trialEndsAtMillis = proSince?.plus(TRIAL_LENGTH_MILLIS),
            lastVerifiedAtMillis = now
        )
    }

    private suspend fun readCached(): EntitlementSnapshot {
        val prefs = dataStore.data.first()
        val tier = prefs[KEY_TIER]?.let { runCatching { EntitlementTier.valueOf(it) }.getOrNull() } ?: EntitlementTier.BASIC
        val proSince = prefs[KEY_PRO_SINCE]
        val lastVerified = prefs[KEY_LAST_VERIFIED]
        return EntitlementSnapshot(tier, proSince, proSince?.plus(TRIAL_LENGTH_MILLIS), lastVerified)
    }

    /** Called when Play could not be reached (e.g. no signal) — Pro access is kept briefly, then fails safe to Basic. */
    private fun degradeForUnverifiedState(cached: EntitlementSnapshot): EntitlementSnapshot {
        if (!cached.tier.grantsProAccess) return cached
        val lastVerified = cached.lastVerifiedAtMillis ?: return cached
        val elapsed = clock.nowMillis() - lastVerified
        return when {
            elapsed <= GRACE_WINDOW_MILLIS -> cached
            elapsed <= EXPIRE_WINDOW_MILLIS -> cached.copy(tier = EntitlementTier.GRACE)
            else -> cached.copy(tier = EntitlementTier.PRO_EXPIRED)
        }
    }
}
