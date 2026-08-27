package uk.co.cabcomply.app.data.billing

/**
 * Central subscription state. The rest of the app queries [EntitlementManager] for this rather
 * than talking to Play Billing directly (product spec section 56).
 */
enum class EntitlementTier {
    BASIC,
    PRO_TRIAL,
    PRO_ACTIVE,
    /** Cached state could not be freshly verified (e.g. offline) but was Pro recently — access is kept, briefly. */
    GRACE,
    PRO_EXPIRED
}

val EntitlementTier.grantsProAccess: Boolean
    get() = this == EntitlementTier.PRO_TRIAL || this == EntitlementTier.PRO_ACTIVE || this == EntitlementTier.GRACE

data class EntitlementSnapshot(
    val tier: EntitlementTier,
    val proSinceMillis: Long? = null,
    val trialEndsAtMillis: Long? = null,
    val lastVerifiedAtMillis: Long? = null
)
