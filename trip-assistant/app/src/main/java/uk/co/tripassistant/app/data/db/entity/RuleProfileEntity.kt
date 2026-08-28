package uk.co.tripassistant.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.RuleSetting

/**
 * A stored rule profile (spec sections 18, 19 and 42).
 *
 * The columns are spelled out one rule at a time rather than serialised into JSON so that a
 * profile stays queryable and a future migration can alter a single rule without rewriting every
 * row.
 */
@Entity(tableName = "rule_profiles")
data class RuleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "amber_tolerance_percent") val amberTolerancePercent: Double,

    @ColumnInfo(name = "min_pounds_per_mile") val minPoundsPerMile: Double,
    @ColumnInfo(name = "min_pounds_per_mile_enabled") val minPoundsPerMileEnabled: Boolean,
    @ColumnInfo(name = "min_pounds_per_mile_importance") val minPoundsPerMileImportance: RuleImportance,

    @ColumnInfo(name = "min_pounds_per_hour") val minPoundsPerHour: Double,
    @ColumnInfo(name = "min_pounds_per_hour_enabled") val minPoundsPerHourEnabled: Boolean,
    @ColumnInfo(name = "min_pounds_per_hour_importance") val minPoundsPerHourImportance: RuleImportance,

    @ColumnInfo(name = "min_fare") val minFare: Double,
    @ColumnInfo(name = "min_fare_enabled") val minFareEnabled: Boolean,
    @ColumnInfo(name = "min_fare_importance") val minFareImportance: RuleImportance,

    @ColumnInfo(name = "max_pickup_miles") val maxPickupMiles: Double,
    @ColumnInfo(name = "max_pickup_miles_enabled") val maxPickupMilesEnabled: Boolean,
    @ColumnInfo(name = "max_pickup_miles_importance") val maxPickupMilesImportance: RuleImportance,

    @ColumnInfo(name = "max_pickup_minutes") val maxPickupMinutes: Double,
    @ColumnInfo(name = "max_pickup_minutes_enabled") val maxPickupMinutesEnabled: Boolean,
    @ColumnInfo(name = "max_pickup_minutes_importance") val maxPickupMinutesImportance: RuleImportance,

    @ColumnInfo(name = "max_pickup_percentage") val maxPickupPercentage: Double,
    @ColumnInfo(name = "max_pickup_percentage_enabled") val maxPickupPercentageEnabled: Boolean,
    @ColumnInfo(name = "max_pickup_percentage_importance") val maxPickupPercentageImportance: RuleImportance,

    @ColumnInfo(name = "min_rider_rating") val minRiderRating: Double,
    @ColumnInfo(name = "min_rider_rating_enabled") val minRiderRatingEnabled: Boolean,
    @ColumnInfo(name = "min_rider_rating_importance") val minRiderRatingImportance: RuleImportance,

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {

    fun toDomain(): RuleProfile = RuleProfile(
        id = id,
        name = name,
        isActive = isActive,
        amberTolerancePercent = amberTolerancePercent,
        createdAt = createdAt,
        updatedAt = updatedAt,
        rules = mapOf(
            RuleId.MIN_POUNDS_PER_MILE to RuleSetting(minPoundsPerMileEnabled, minPoundsPerMileImportance, minPoundsPerMile),
            RuleId.MIN_POUNDS_PER_HOUR to RuleSetting(minPoundsPerHourEnabled, minPoundsPerHourImportance, minPoundsPerHour),
            RuleId.MIN_FARE to RuleSetting(minFareEnabled, minFareImportance, minFare),
            RuleId.MAX_PICKUP_MILES to RuleSetting(maxPickupMilesEnabled, maxPickupMilesImportance, maxPickupMiles),
            RuleId.MAX_PICKUP_MINUTES to RuleSetting(maxPickupMinutesEnabled, maxPickupMinutesImportance, maxPickupMinutes),
            RuleId.MAX_PICKUP_PERCENT to RuleSetting(maxPickupPercentageEnabled, maxPickupPercentageImportance, maxPickupPercentage),
            RuleId.MIN_RIDER_RATING to RuleSetting(minRiderRatingEnabled, minRiderRatingImportance, minRiderRating)
        )
    )

    companion object {
        fun fromDomain(profile: RuleProfile): RuleProfileEntity {
            fun rule(id: RuleId): RuleSetting =
                profile.rules[id] ?: RuleSetting(enabled = false, importance = RuleImportance.OFF, target = 0.0)

            val perMile = rule(RuleId.MIN_POUNDS_PER_MILE)
            val perHour = rule(RuleId.MIN_POUNDS_PER_HOUR)
            val fare = rule(RuleId.MIN_FARE)
            val pickupMiles = rule(RuleId.MAX_PICKUP_MILES)
            val pickupMinutes = rule(RuleId.MAX_PICKUP_MINUTES)
            val pickupPercent = rule(RuleId.MAX_PICKUP_PERCENT)
            val rating = rule(RuleId.MIN_RIDER_RATING)

            return RuleProfileEntity(
                id = profile.id,
                name = profile.name,
                isActive = profile.isActive,
                amberTolerancePercent = profile.amberTolerancePercent,
                minPoundsPerMile = perMile.target,
                minPoundsPerMileEnabled = perMile.enabled,
                minPoundsPerMileImportance = perMile.importance,
                minPoundsPerHour = perHour.target,
                minPoundsPerHourEnabled = perHour.enabled,
                minPoundsPerHourImportance = perHour.importance,
                minFare = fare.target,
                minFareEnabled = fare.enabled,
                minFareImportance = fare.importance,
                maxPickupMiles = pickupMiles.target,
                maxPickupMilesEnabled = pickupMiles.enabled,
                maxPickupMilesImportance = pickupMiles.importance,
                maxPickupMinutes = pickupMinutes.target,
                maxPickupMinutesEnabled = pickupMinutes.enabled,
                maxPickupMinutesImportance = pickupMinutes.importance,
                maxPickupPercentage = pickupPercent.target,
                maxPickupPercentageEnabled = pickupPercent.enabled,
                maxPickupPercentageImportance = pickupPercent.importance,
                minRiderRating = rating.target,
                minRiderRatingEnabled = rating.enabled,
                minRiderRatingImportance = rating.importance,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt
            )
        }
    }
}
