package uk.co.tripassistant.app.data.billing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import uk.co.tripassistant.core.entitlement.AccessDecision
import uk.co.tripassistant.core.entitlement.EntitlementConfig
import uk.co.tripassistant.core.entitlement.EntitlementPolicy
import uk.co.tripassistant.core.entitlement.EntitlementSnapshot
import uk.co.tripassistant.core.entitlement.EntitlementStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single answer to "may live evaluation run right now?" (spec sections 3 and 5).
 *
 * Ordering matters here. The app asks Google Play what purchases exist, then — if an entitlement
 * service is configured — asks that service what state the purchase is actually in, because Play
 * on its own cannot distinguish a grace period from an account hold. The result is cached, and
 * [uk.co.tripassistant.core.entitlement.EntitlementPolicy] decides how long that cache is honoured
 * without a refresh.
 *
 * What this class never does: delete anything. A lapsed subscription stops live evaluation and
 * nothing else — settings and history stay exactly where they were (spec section 3).
 */
@Singleton
class EntitlementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billing: BillingDataSource,
    private val backend: EntitlementBackendClient,
    private val store: EntitlementStore
) {

    /** Business rules of spec sections 3 and 5, in one place so they can be changed as data. */
    val config = EntitlementConfig(
        trialDurationDays = 14,
        maxOfflineDays = 7,
        reverifyAfterHours = 24
    )

    val snapshot: Flow<EntitlementSnapshot> = store.snapshot

    /** True when entitlement is only as good as Google Play's word (spec section 5). */
    val hasBackendVerification: Boolean get() = backend.isConfigured

    /**
     * Re-evaluates on every snapshot change and on a slow tick, so a trial that runs out mid-shift
     * is noticed without the driver having to reopen a screen.
     */
    fun observeAccess(): Flow<AccessDecision> =
        combine(store.snapshot, minuteTicker()) { snapshot, _ ->
            EntitlementPolicy.decide(snapshot, System.currentTimeMillis(), config)
        }

    fun observeTrialDaysRemaining(): Flow<Int?> = store.snapshot.map {
        EntitlementPolicy.trialDaysRemaining(it, System.currentTimeMillis(), config)
    }

    suspend fun currentAccess(now: Long = System.currentTimeMillis()): AccessDecision =
        EntitlementPolicy.decide(store.current(), now, config)

    /** Called once on first launch: the trial starts when the driver opens the app, not on install. */
    suspend fun startTrialIfNeeded(now: Long = System.currentTimeMillis()) {
        store.startTrialIfNeeded(now)
    }

    /**
     * Asks Play (and the entitlement service, when configured) for the current state and caches it.
     *
     * Failure is not fatal and never clears the cache: a driver in a car park with no signal keeps
     * the entitlement they had, bounded by the offline allowance.
     */
    suspend fun refresh(now: Long = System.currentTimeMillis()): AccessDecision {
        val existing = store.current()
        val purchase = runCatching { billing.currentPurchase() }.getOrNull()

        if (purchase == null) {
            // No purchase on the account. That is a fact from Play, so it is worth recording — but
            // it must not wipe a trial start date or an install id.
            val status = if (existing.status == EntitlementStatus.NONE) {
                EntitlementStatus.NONE
            } else {
                EntitlementStatus.EXPIRED
            }
            store.save(
                existing.copy(
                    status = status,
                    productId = null,
                    expiryTimeMillis = null,
                    autoRenewing = false,
                    lastVerifiedAtMillis = now
                )
            )
            return currentAccess(now)
        }

        // Play requires acknowledgement or the purchase is automatically refunded.
        runCatching { billing.acknowledge(purchase) }

        val verified = if (backend.isConfigured) {
            backend.verify(
                purchase = purchase,
                installId = store.installId(),
                packageName = context.packageName
            ).getOrNull()
        } else {
            null
        }

        val resolved = verified ?: BackendEntitlement(
            // Without the Play Developer API, "purchased and renewing" is the most that can be
            // said honestly. Grace and hold are indistinguishable from here.
            status = if (purchase.isAutoRenewing) {
                EntitlementStatus.ACTIVE
            } else {
                EntitlementStatus.CANCELLED_STILL_VALID
            },
            expiryTimeMillis = null,
            autoRenewing = purchase.isAutoRenewing,
            productId = purchase.productId
        )

        val verificationSucceeded = !backend.isConfigured || verified != null
        store.save(
            existing.copy(
                status = resolved.status,
                productId = resolved.productId ?: purchase.productId,
                expiryTimeMillis = resolved.expiryTimeMillis,
                autoRenewing = resolved.autoRenewing,
                // A backend that could not be reached leaves the previous verification time in
                // place, which is what makes the offline allowance count down instead of resetting.
                lastVerifiedAtMillis = if (verificationSucceeded) now else existing.lastVerifiedAtMillis
            )
        )
        return currentAccess(now)
    }

    /** "Restore purchases" is the same question asked again (spec section 4). */
    suspend fun restorePurchases(): AccessDecision = refresh()

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}
