package uk.co.tripassistant.app.data.billing

/**
 * Subscription products (spec section 4).
 *
 * Ids only — never prices. Everything the driver sees about cost comes from Google Play's
 * ProductDetails at runtime, because a hard-coded price is wrong the moment it changes, wrong in
 * every currency but one, and against Play policy.
 */
object SubscriptionProducts {
    const val MONTHLY = "trip_assistant_monthly"
    const val ANNUAL = "trip_assistant_annual"

    val all = listOf(MONTHLY, ANNUAL)
}

/**
 * One purchasable offer, already resolved from Play's pricing phases into the four things the
 * subscription screen has to state plainly: what the trial is, what it costs afterwards, how
 * often it renews, and which product it is (spec section 4).
 */
data class SubscriptionOffer(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val title: String,
    /** Formatted by Play in the driver's own currency, e.g. "£4.99". */
    val formattedPrice: String,
    /** ISO-8601 billing period of the paid phase, e.g. "P1M". */
    val billingPeriodIso: String,
    /** Free trial length in days, or null when this offer has no trial phase. */
    val freeTrialDays: Int?
) {
    val billingPeriodLabel: String get() = IsoPeriod.describe(billingPeriodIso)
    val hasFreeTrial: Boolean get() = (freeTrialDays ?: 0) > 0
}

/** A subscription purchase as Google Play reports it. */
data class PurchaseRecord(
    val productId: String,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val purchaseTimeMillis: Long
)

/** Whether billing is usable at all on this device right now. */
sealed interface BillingAvailability {
    data object Connecting : BillingAvailability
    data object Ready : BillingAvailability
    data class Unavailable(val message: String) : BillingAvailability
}

/** Turns Play's ISO-8601 periods into something a driver reads without thinking. */
object IsoPeriod {

    /** "P1M" -> "month", "P1Y" -> "year", "P2W" -> "2 weeks". */
    fun describe(iso: String): String = when (iso.uppercase()) {
        "P1M" -> "month"
        "P3M" -> "3 months"
        "P6M" -> "6 months"
        "P1Y" -> "year"
        "P1W" -> "week"
        else -> days(iso)?.let { "$it days" } ?: iso
    }

    /** Days in a simple ISO-8601 period, for trial phases such as "P14D" or "P2W". */
    fun days(iso: String): Int? {
        val match = Regex("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$", RegexOption.IGNORE_CASE)
            .find(iso.trim()) ?: return null
        val (years, months, weeks, days) = match.destructured
        val total = (years.toIntOrNull() ?: 0) * 365 +
            (months.toIntOrNull() ?: 0) * 30 +
            (weeks.toIntOrNull() ?: 0) * 7 +
            (days.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }
}
