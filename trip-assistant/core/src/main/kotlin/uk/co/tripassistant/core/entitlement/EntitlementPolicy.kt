package uk.co.tripassistant.core.entitlement

import kotlin.math.ceil

/**
 * Decides what the driver may do, from a cached entitlement snapshot and the current time
 * (spec sections 3 and 5).
 *
 * Pure and side-effect free so that every one of the billing scenarios in spec section 57 can be
 * a unit test rather than a manual Play Console exercise.
 *
 * Two rules hold in every branch:
 *  * settings and history are never gated here — losing a subscription must never lose data;
 *  * a previously confirmed subscription keeps working offline for a bounded period, then stops
 *    rather than trusting a stale cache indefinitely.
 */
object EntitlementPolicy {

    fun decide(
        snapshot: EntitlementSnapshot,
        nowMillis: Long,
        config: EntitlementConfig = EntitlementConfig()
    ): AccessDecision {
        val trialRemaining = trialDaysRemaining(snapshot, nowMillis, config)

        // The install trial is what makes onboarding frictionless (spec section 6): the driver
        // gets the complete app for the trial period without an account or a card.
        if (trialRemaining != null && trialRemaining > 0 && !hasPaidState(snapshot)) {
            return AccessDecision(
                level = AccessLevel.FULL,
                status = EntitlementStatus.TRIAL,
                trialDaysRemaining = trialRemaining
            )
        }

        // A null expiry means "Google Play reports a purchase but nothing has told us when it
        // ends" — which is the normal picture when the app talks to Play alone, with no backend
        // entitlement service configured. That is not a reason to lock a paying driver out; the
        // offline allowance below is what bounds how long an unconfirmed claim survives.
        val paidPeriodValid = snapshot.expiryTimeMillis?.let { it > nowMillis } ?: true

        val entitledByPlay = when (snapshot.status) {
            // A Play introductory free-trial phase reports as TRIAL and carries a real expiry.
            EntitlementStatus.TRIAL,
            EntitlementStatus.ACTIVE,
            EntitlementStatus.CANCELLED_STILL_VALID -> paidPeriodValid

            // Google keeps the driver entitled while it retries payment (spec section 4).
            EntitlementStatus.GRACE_PERIOD -> true

            EntitlementStatus.ON_HOLD,
            EntitlementStatus.PAUSED,
            EntitlementStatus.EXPIRED,
            EntitlementStatus.NONE -> false
        }

        if (!entitledByPlay) {
            return AccessDecision(
                level = AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED,
                status = if (trialRemaining != null && trialRemaining <= 0 && snapshot.status == EntitlementStatus.NONE) {
                    EntitlementStatus.EXPIRED
                } else {
                    snapshot.status
                },
                trialDaysRemaining = trialRemaining
            )
        }

        // Entitled — but only for as long as that entitlement has been confirmed recently enough.
        val lastVerified = snapshot.lastVerifiedAtMillis
            ?: return AccessDecision(
                level = AccessLevel.LOCKED_VERIFICATION_REQUIRED,
                status = snapshot.status,
                offlineDaysRemaining = 0,
                shouldReverify = true
            )

        val offlineFor = (nowMillis - lastVerified).coerceAtLeast(0L)
        if (offlineFor > config.maxOfflineMillis) {
            return AccessDecision(
                level = AccessLevel.LOCKED_VERIFICATION_REQUIRED,
                status = snapshot.status,
                offlineDaysRemaining = 0,
                shouldReverify = true
            )
        }

        return AccessDecision(
            level = AccessLevel.FULL,
            status = snapshot.status,
            offlineDaysRemaining = ceilDays(config.maxOfflineMillis - offlineFor),
            shouldReverify = offlineFor > config.reverifyAfterMillis
        )
    }

    /** Null when the install trial has not been started at all. Never negative. */
    fun trialDaysRemaining(
        snapshot: EntitlementSnapshot,
        nowMillis: Long,
        config: EntitlementConfig = EntitlementConfig()
    ): Int? {
        val started = snapshot.trialStartedAtMillis ?: return null
        val endsAt = started + config.trialDurationMillis
        return ceilDays(endsAt - nowMillis)
    }

    private fun hasPaidState(snapshot: EntitlementSnapshot): Boolean = when (snapshot.status) {
        EntitlementStatus.ACTIVE,
        EntitlementStatus.CANCELLED_STILL_VALID,
        EntitlementStatus.GRACE_PERIOD,
        EntitlementStatus.ON_HOLD,
        EntitlementStatus.PAUSED -> true

        EntitlementStatus.TRIAL -> snapshot.expiryTimeMillis != null
        EntitlementStatus.NONE, EntitlementStatus.EXPIRED -> false
    }

    private fun ceilDays(millis: Long): Int =
        if (millis <= 0L) 0 else ceil(millis.toDouble() / EntitlementConfig.DAY_MILLIS).toInt()
}
