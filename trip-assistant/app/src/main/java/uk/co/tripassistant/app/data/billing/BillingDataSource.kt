package uk.co.tripassistant.app.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the rest of the app is allowed to know about Google Play Billing.
 *
 * The interface exists so that exactly one file imports the Play SDK. That keeps a library
 * upgrade to a single file, and lets the entitlement logic be reasoned about (and tested) without
 * a Play connection.
 */
interface BillingDataSource {

    val availability: StateFlow<BillingAvailability>

    /** Emits whenever Play reports a purchase change, so entitlement can be refreshed. */
    val purchaseUpdates: Flow<Unit>

    /** Prices and trial phases straight from Play (spec section 4). */
    suspend fun subscriptionOffers(): List<SubscriptionOffer>

    /** The current subscription purchase, if there is one. */
    suspend fun currentPurchase(): PurchaseRecord?

    /** Starts Play's purchase flow. The result arrives via [purchaseUpdates]. */
    suspend fun launchPurchase(activity: Activity, offer: SubscriptionOffer): Result<Unit>

    /** Play requires a purchase to be acknowledged, or it is refunded automatically. */
    suspend fun acknowledge(purchase: PurchaseRecord): Result<Unit>
}
