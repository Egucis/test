package uk.co.tripassistant.core.rules

import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.RuleSetting

/**
 * The starter profiles of spec section 18 — Normal, Busy and Quiet.
 *
 * These are seeds, not fixtures: the driver can edit every value, rename them or delete them.
 * They ship as SOFT rules throughout so a brand-new install never silently rejects work; making
 * a rule HARD is a deliberate choice the driver makes.
 */
object DefaultProfiles {

    const val DEFAULT_AMBER_TOLERANCE_PERCENT = 10.0

    const val NORMAL = "Normal"
    const val BUSY = "Busy"
    const val QUIET = "Quiet"

    fun normal(id: Long = 0L, now: Long = 0L): RuleProfile = profile(
        id = id,
        name = NORMAL,
        isActive = true,
        now = now,
        poundsPerMile = 1.50,
        poundsPerHour = 25.0,
        minFare = 6.0,
        maxPickupMiles = 4.0,
        maxPickupMinutes = 10.0,
        maxPickupPercent = 35.0,
        minRating = 4.70
    )

    fun busy(id: Long = 0L, now: Long = 0L): RuleProfile = profile(
        id = id,
        name = BUSY,
        isActive = false,
        now = now,
        poundsPerMile = 1.90,
        poundsPerHour = 32.0,
        minFare = 8.0,
        maxPickupMiles = 3.0,
        maxPickupMinutes = 8.0,
        maxPickupPercent = 30.0,
        minRating = 4.80
    )

    fun quiet(id: Long = 0L, now: Long = 0L): RuleProfile = profile(
        id = id,
        name = QUIET,
        isActive = false,
        now = now,
        poundsPerMile = 1.20,
        poundsPerHour = 18.0,
        minFare = 4.50,
        maxPickupMiles = 6.0,
        maxPickupMinutes = 15.0,
        maxPickupPercent = 45.0,
        minRating = 4.60,
        ratingEnabled = false
    )

    fun starterSet(now: Long = 0L): List<RuleProfile> = listOf(normal(now = now), busy(now = now), quiet(now = now))

    /** A blank profile for "create your own", seeded from Normal so nothing starts at zero. */
    fun custom(name: String, now: Long = 0L): RuleProfile =
        normal(now = now).copy(name = name, isActive = false, createdAt = now, updatedAt = now)

    private fun profile(
        id: Long,
        name: String,
        isActive: Boolean,
        now: Long,
        poundsPerMile: Double,
        poundsPerHour: Double,
        minFare: Double,
        maxPickupMiles: Double,
        maxPickupMinutes: Double,
        maxPickupPercent: Double,
        minRating: Double,
        ratingEnabled: Boolean = true
    ): RuleProfile = RuleProfile(
        id = id,
        name = name,
        isActive = isActive,
        amberTolerancePercent = DEFAULT_AMBER_TOLERANCE_PERCENT,
        createdAt = now,
        updatedAt = now,
        rules = mapOf(
            RuleId.MIN_POUNDS_PER_MILE to RuleSetting(true, RuleImportance.SOFT, poundsPerMile),
            RuleId.MIN_POUNDS_PER_HOUR to RuleSetting(true, RuleImportance.SOFT, poundsPerHour),
            RuleId.MIN_FARE to RuleSetting(true, RuleImportance.SOFT, minFare),
            RuleId.MAX_PICKUP_MILES to RuleSetting(true, RuleImportance.SOFT, maxPickupMiles),
            RuleId.MAX_PICKUP_MINUTES to RuleSetting(true, RuleImportance.SOFT, maxPickupMinutes),
            RuleId.MAX_PICKUP_PERCENT to RuleSetting(true, RuleImportance.SOFT, maxPickupPercent),
            RuleId.MIN_RIDER_RATING to RuleSetting(ratingEnabled, RuleImportance.SOFT, minRating)
        )
    )
}
