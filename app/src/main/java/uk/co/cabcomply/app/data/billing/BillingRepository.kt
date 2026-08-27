package uk.co.cabcomply.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Play Billing. [EntitlementManager] is the only consumer — no other part of
 * the app talks to BillingClient directly (product spec section 56).
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        const val PRO_MONTHLY_PRODUCT_ID = "cabcomply_pro_monthly"
    }

    private val purchaseUpdates = MutableSharedFlow<List<Purchase>>(replay = 0, extraBufferCapacity = 4)
    val purchaseUpdatesFlow: SharedFlow<List<Purchase>> = purchaseUpdates

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchaseUpdates.tryEmit(purchases)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases()
        .build()

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    suspend fun queryProSubscriptionDetails(): ProductDetails? {
        if (!ensureConnected()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_MONTHLY_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(productDetailsList.firstOrNull())
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    /** Active/pending subscription purchases, freshly queried from Play — null on connection failure (e.g. offline). */
    suspend fun queryActiveSubscriptionPurchases(): List<Purchase>? {
        if (!ensureConnected()) return null
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases)
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    suspend fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        if (!ensureConnected()) return false
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return true
        if (!ensureConnected()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        return suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(params) { billingResult ->
                continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }
}
