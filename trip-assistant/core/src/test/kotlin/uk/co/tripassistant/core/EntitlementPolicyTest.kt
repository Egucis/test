package uk.co.tripassistant.core

import uk.co.tripassistant.core.entitlement.AccessLevel
import uk.co.tripassistant.core.entitlement.EntitlementConfig
import uk.co.tripassistant.core.entitlement.EntitlementPolicy
import uk.co.tripassistant.core.entitlement.EntitlementSnapshot
import uk.co.tripassistant.core.entitlement.EntitlementStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every billing scenario listed in spec section 57, as arithmetic rather than a Play Console run. */
class EntitlementPolicyTest {

    private val now = 1_800_000_000_000L
    private val day = EntitlementConfig.DAY_MILLIS
    private val config = EntitlementConfig()

    private fun decide(snapshot: EntitlementSnapshot, at: Long = now, cfg: EntitlementConfig = config) =
        EntitlementPolicy.decide(snapshot, at, cfg)

    @Test
    fun `a brand new install has not started anything`() {
        val decision = decide(EntitlementSnapshot())
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decision.level)
    }

    @Test
    fun `starting the trial unlocks the complete app`() {
        val decision = decide(EntitlementSnapshot(trialStartedAtMillis = now))
        assertEquals(AccessLevel.FULL, decision.level)
        assertEquals(EntitlementStatus.TRIAL, decision.status)
        assertEquals(14, decision.trialDaysRemaining)
    }

    @Test
    fun `the trial counts down`() {
        val decision = decide(EntitlementSnapshot(trialStartedAtMillis = now - 10 * day))
        assertEquals(AccessLevel.FULL, decision.level)
        assertEquals(4, decision.trialDaysRemaining)
    }

    @Test
    fun `an expired trial stops live evaluation`() {
        val decision = decide(EntitlementSnapshot(trialStartedAtMillis = now - 15 * day))
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decision.level)
        assertEquals(0, decision.trialDaysRemaining)
    }

    @Test
    fun `the trial length is configuration, not architecture`() {
        // Spec section 3: it must be possible to move to 7 days without a rebuild of anything.
        val sevenDays = EntitlementConfig(trialDurationDays = 7)
        val snapshot = EntitlementSnapshot(trialStartedAtMillis = now - 10 * day)
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decide(snapshot, cfg = sevenDays).level)
        assertEquals(AccessLevel.FULL, decide(snapshot).level)
    }

    @Test
    fun `a paid subscription verified today is live`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ACTIVE,
                productId = "trip_assistant_monthly",
                expiryTimeMillis = now + 20 * day,
                lastVerifiedAtMillis = now,
                autoRenewing = true
            )
        )
        assertEquals(AccessLevel.FULL, decision.level)
        assertFalse(decision.shouldReverify)
    }

    @Test
    fun `cancelling keeps access until the paid period runs out`() {
        val snapshot = EntitlementSnapshot(
            status = EntitlementStatus.CANCELLED_STILL_VALID,
            expiryTimeMillis = now + 3 * day,
            lastVerifiedAtMillis = now
        )
        assertEquals(AccessLevel.FULL, decide(snapshot).level)
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decide(snapshot, at = now + 4 * day).level)
    }

    @Test
    fun `a payment grace period keeps the driver working`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.GRACE_PERIOD,
                expiryTimeMillis = now - day,
                lastVerifiedAtMillis = now
            )
        )
        assertEquals(AccessLevel.FULL, decision.level)
    }

    @Test
    fun `account hold stops live evaluation`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ON_HOLD,
                expiryTimeMillis = now + 10 * day,
                lastVerifiedAtMillis = now
            )
        )
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decision.level)
    }

    @Test
    fun `a paused subscription stops live evaluation`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.PAUSED,
                expiryTimeMillis = now + 10 * day,
                lastVerifiedAtMillis = now
            )
        )
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decision.level)
    }

    // --- offline behaviour, spec section 5 -----------------------------------------------------

    @Test
    fun `a short spell offline does not interrupt a shift`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ACTIVE,
                expiryTimeMillis = now + 20 * day,
                lastVerifiedAtMillis = now - 3 * day
            )
        )
        assertEquals(AccessLevel.FULL, decision.level)
        assertEquals(4, decision.offlineDaysRemaining)
        assertTrue(decision.shouldReverify, "the app should refresh entitlement when it can")
    }

    @Test
    fun `too long offline requires verification`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ACTIVE,
                expiryTimeMillis = now + 20 * day,
                lastVerifiedAtMillis = now - 8 * day
            )
        )
        assertEquals(AccessLevel.LOCKED_VERIFICATION_REQUIRED, decision.level)
    }

    @Test
    fun `the offline allowance is configurable`() {
        val snapshot = EntitlementSnapshot(
            status = EntitlementStatus.ACTIVE,
            expiryTimeMillis = now + 20 * day,
            lastVerifiedAtMillis = now - 8 * day
        )
        assertEquals(
            AccessLevel.FULL,
            decide(snapshot, cfg = EntitlementConfig(maxOfflineDays = 14)).level
        )
    }

    @Test
    fun `a purchase that has never been verified is not trusted`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ACTIVE,
                expiryTimeMillis = now + 20 * day,
                lastVerifiedAtMillis = null
            )
        )
        assertEquals(AccessLevel.LOCKED_VERIFICATION_REQUIRED, decision.level)
        assertTrue(decision.shouldReverify)
    }

    @Test
    fun `access comes straight back once entitlement is confirmed again`() {
        val stale = EntitlementSnapshot(
            status = EntitlementStatus.ACTIVE,
            expiryTimeMillis = now + 20 * day,
            lastVerifiedAtMillis = now - 9 * day
        )
        assertEquals(AccessLevel.LOCKED_VERIFICATION_REQUIRED, decide(stale).level)
        assertEquals(AccessLevel.FULL, decide(stale.copy(lastVerifiedAtMillis = now)).level)
    }

    @Test
    fun `resubscribing after an expiry restores access`() {
        val expired = EntitlementSnapshot(
            status = EntitlementStatus.EXPIRED,
            expiryTimeMillis = now - 5 * day,
            lastVerifiedAtMillis = now
        )
        assertEquals(AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED, decide(expired).level)

        val resubscribed = expired.copy(
            status = EntitlementStatus.ACTIVE,
            expiryTimeMillis = now + 30 * day
        )
        assertEquals(AccessLevel.FULL, decide(resubscribed).level)
    }

    @Test
    fun `a Play purchase with no known expiry is still honoured`() {
        // With no backend entitlement service configured the app only knows "Play reports an
        // active subscription". That must keep working; the offline allowance still bounds it.
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.ACTIVE,
                productId = "trip_assistant_monthly",
                expiryTimeMillis = null,
                lastVerifiedAtMillis = now
            )
        )
        assertEquals(AccessLevel.FULL, decision.level)
    }

    @Test
    fun `a Play introductory trial phase is honoured after the install trial has gone`() {
        val decision = decide(
            EntitlementSnapshot(
                status = EntitlementStatus.TRIAL,
                expiryTimeMillis = now + 9 * day,
                lastVerifiedAtMillis = now,
                trialStartedAtMillis = now - 60 * day
            )
        )
        assertEquals(AccessLevel.FULL, decision.level)
    }
}
