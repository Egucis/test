package uk.co.tripassistant.core.entitlement

/**
 * Subscription states the app has to survive (spec sections 4 and 57). These mirror the states
 * Google Play reports; nothing here is inferred from a local boolean.
 */
enum class EntitlementStatus {
    /** Never subscribed, never trialled. */
    NONE,

    /** Inside the free trial — either the install trial or a Play introductory offer phase. */
    TRIAL,

    /** Paid and auto-renewing. */
    ACTIVE,

    /** Cancelled, but the paid period has not run out yet — full access until it does. */
    CANCELLED_STILL_VALID,

    /** Payment failed; Google is retrying. Access continues (spec section 4). */
    GRACE_PERIOD,

    /** Payment failed past the grace period. Access stops, data does not. */
    ON_HOLD,

    /** Driver paused the subscription in Play. */
    PAUSED,

    /** Ran out. */
    EXPIRED
}

/**
 * Business rules that the product owner may want to change without an architecture change
 * (spec sections 3 and 5). Everything here is data, not a compile-time constant sprinkled
 * through the codebase.
 */
data class EntitlementConfig(
    /** Spec section 3: 14 days initially, changeable to 7 without rebuilding anything. */
    val trialDurationDays: Int = 14,
    /** Spec section 5: how long a previously confirmed subscription keeps working offline. */
    val maxOfflineDays: Int = 7,
    /** How stale a verification may get before the app tries to refresh it in the background. */
    val reverifyAfterHours: Int = 24
) {
    val trialDurationMillis: Long get() = trialDurationDays * DAY_MILLIS
    val maxOfflineMillis: Long get() = maxOfflineDays * DAY_MILLIS
    val reverifyAfterMillis: Long get() = reverifyAfterHours * HOUR_MILLIS

    companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}

/**
 * The last thing the app knows about entitlement. Cached locally so a tunnel or a dead signal
 * does not stop the assistant mid-shift, but never treated as authoritative forever
 * (spec section 42).
 */
data class EntitlementSnapshot(
    val status: EntitlementStatus = EntitlementStatus.NONE,
    val productId: String? = null,
    /** When the current paid/trial period runs out, as reported by Play or the backend. */
    val expiryTimeMillis: Long? = null,
    /** When entitlement was last confirmed against Play or the backend. Null means never. */
    val lastVerifiedAtMillis: Long? = null,
    /** First launch of the install trial. Null until the driver opens the app for the first time. */
    val trialStartedAtMillis: Long? = null,
    val autoRenewing: Boolean = false
)

/** What the driver can do right now. */
enum class AccessLevel {
    /** Live evaluation runs. */
    FULL,

    /** Trial finished, nothing bought. History and settings still open (spec section 3). */
    LOCKED_SUBSCRIPTION_REQUIRED,

    /** Subscribed, but offline too long to keep trusting the cache (spec section 5). */
    LOCKED_VERIFICATION_REQUIRED
}

data class AccessDecision(
    val level: AccessLevel,
    val status: EntitlementStatus,
    /** Whole days left of the free trial, or null when not on trial. */
    val trialDaysRemaining: Int? = null,
    /** Whole days of offline grace left before verification becomes mandatory. */
    val offlineDaysRemaining: Int? = null,
    /** True when the app should quietly try to re-verify as soon as it has connectivity. */
    val shouldReverify: Boolean = false
) {
    val isLive: Boolean get() = level == AccessLevel.FULL
}
