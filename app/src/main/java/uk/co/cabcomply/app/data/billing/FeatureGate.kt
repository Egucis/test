package uk.co.cabcomply.app.data.billing

/**
 * Every Basic/Pro distinction in the app is looked up here, never hardcoded per-screen
 * (product spec section 53). CabComply Basic always keeps: one driver, one active vehicle,
 * daily checks, defects, basic mileage, basic documents and a local backup/restore safety net.
 */
enum class ProFeature(val title: String, val description: String) {
    MULTIPLE_VEHICLES(
        "Multiple vehicles",
        "Track more than one vehicle's checks, mileage and documents in one account."
    ),
    DOCUMENT_EXPIRY_REMINDERS(
        "Expiry reminders",
        "Get a notification before your MOT, insurance or licence expires."
    ),
    EXTENDED_MILEAGE_HMRC(
        "HMRC mileage tools",
        "Tax-year grouping, totals and export for business mileage claims."
    ),
    ADVANCED_REPORTING(
        "Custom-range reports",
        "Generate compliance reports for any date range, not just the current week."
    ),
    ADVANCED_BACKUP_EXPORT(
        "Advanced export",
        "Export mileage and records as CSV alongside your local backup."
    )
}

object FeatureGate {
    private const val BASIC_MAX_VEHICLES = 1

    fun isUnlocked(feature: ProFeature, snapshot: EntitlementSnapshot): Boolean = snapshot.tier.grantsProAccess

    /** Basic keeps existing vehicles beyond the limit visible and usable in history; it only stops new ones being made active. */
    fun maxActiveVehicles(snapshot: EntitlementSnapshot): Int =
        if (snapshot.tier.grantsProAccess) Int.MAX_VALUE else BASIC_MAX_VEHICLES
}
