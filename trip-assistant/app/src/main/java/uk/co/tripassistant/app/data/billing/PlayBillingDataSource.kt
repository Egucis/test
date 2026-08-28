package uk.co.tripassistant.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THE ONLY FILE IN THIS PROJECT THAT IMPORTS THE GOOGLE PLAY BILLING SDK.
 *
 * It is written against the Play Billing Library version pinned in app/build.gradle.kts (9.1.0,
 * the version spec section 4 names as current). The project was authored in an environment with no
 * access to Google's Maven repository, so this file could not be compiled against the real SDK —
 * if the library's surface differs, this is the one file to adjust, and nothing outside it needs
 * to change. See SPEC_COMPLIANCE.md.
 *
 * Two rules hold here:
 *  * a purchase token is never logged, never put in a crash report, and never leaves this class
 *    except to the entitlement backend over HTTPS (spec section 52);
 *  * prices are never invented — every figure shown to the driver comes from [ProductDetails]
 *    (spec section 4).
 */
@Singleton
class PlayBillingDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BillingDataSource {

    private val _availability = MutableStateFlow<BillingAvailability>(BillingAvailability.Connecting)
    override val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    private val _purchaseUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val purchaseUpdates: SharedFlow<Unit> = _purchaseUpdates.asSharedFlow()

    private val connectionLock = Mutex()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { _, _ ->
        // Deliberately does not carry the purchase: entitlement is always re-read from Play (and
        // then the backend) rather than trusted from a callback payload.
        _purchaseUpdates.tryEmit(Unit)
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    /** Connects if needed. Safe to call from anywhere; only one connection attempt runs at a time. */
    private suspend fun ensureConnected(): Boolean = connectionLock.withLock {
        if (client.isReady) {
            _availability.value = BillingAvailability.Ready
            return@withLock true
        }
        _availability.value = BillingAvailability.Connecting

        val connected = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _availability.value = if (ok) {
                    BillingAvailability.Ready
                } else {
                    BillingAvailability.Unavailable(result.debugMessage.ifBlank { "Google Play billing is unavailable" })
                }
                if (!connected.isCompleted) connected.complete(ok)
            }

            override fun onBillingServiceDisconnected() {
                _availability.value = BillingAvailability.Connecting
                if (!connected.isCompleted) connected.complete(false)
            }
        })
        connected.await()
    }

    override suspend fun subscriptionOffers(): List<SubscriptionOffer> {
        if (!ensureConnected()) return emptyList()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                SubscriptionProducts.all.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        val deferred = CompletableDeferred<List<ProductDetails>>()
        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                deferred.complete(productDetailsResult.productDetailsList.orEmpty())
            } else {
                deferred.complete(emptyList())
            }
        }
        return deferred.await().flatMap { it.toOffers() }
    }

    override suspend fun currentPurchase(): PurchaseRecord? {
        if (!ensureConnected()) return null

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val deferred = CompletableDeferred<List<Purchase>>()
        client.queryPurchasesAsync(params) { _, purchases -> deferred.complete(purchases) }

        return deferred.await()
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .maxByOrNull { it.purchaseTime }
            ?.let { purchase ->
                PurchaseRecord(
                    productId = purchase.products.firstOrNull() ?: SubscriptionProducts.MONTHLY,
                    purchaseToken = purchase.purchaseToken,
                    isAcknowledged = purchase.isAcknowledged,
                    isAutoRenewing = purchase.isAutoRenewing,
                    purchaseTimeMillis = purchase.purchaseTime
                )
            }
    }

    override suspend fun launchPurchase(activity: Activity, offer: SubscriptionOffer): Result<Unit> {
        if (!ensureConnected()) return Result.failure(IllegalStateException("Google Play billing is unavailable"))

        val details = productDetailsFor(offer.productId)
            ?: return Result.failure(IllegalStateException("Subscription is not available on this account"))

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.debugMessage.ifBlank { "Could not start the purchase" }))
        }
    }

    override suspend fun acknowledge(purchase: PurchaseRecord): Result<Unit> {
        if (purchase.isAcknowledged) return Result.success(Unit)
        if (!ensureConnected()) return Result.failure(IllegalStateException("Google Play billing is unavailable"))

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val deferred = CompletableDeferred<BillingResult>()
        client.acknowledgePurchase(params) { deferred.complete(it) }
        val result = deferred.await()
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            // Note the absence of the token in the message.
            Result.failure(IllegalStateException("Could not acknowledge the purchase"))
        }
    }

    private suspend fun productDetailsFor(productId: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val deferred = CompletableDeferred<ProductDetails?>()
        client.queryProductDetailsAsync(params) { _, productDetailsResult ->
            deferred.complete(productDetailsResult.productDetailsList?.firstOrNull())
        }
        return deferred.await()
    }

    /**
     * Flattens Play's pricing phases into the plain statement the subscription screen must make:
     * the trial, then the price, then how often it renews (spec section 4).
     */
    private fun ProductDetails.toOffers(): List<SubscriptionOffer> =
        subscriptionOfferDetails.orEmpty().mapNotNull { offerDetails ->
            val phases = offerDetails.pricingPhases.pricingPhaseList
            val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: return@mapNotNull null
            val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }

            SubscriptionOffer(
                productId = productId,
                basePlanId = offerDetails.basePlanId,
                offerId = offerDetails.offerId,
                offerToken = offerDetails.offerToken,
                title = name.ifBlank { title },
                formattedPrice = paidPhase.formattedPrice,
                billingPeriodIso = paidPhase.billingPeriod,
                freeTrialDays = trialPhase?.billingPeriod?.let { IsoPeriod.days(it) }
            )
        }
}
