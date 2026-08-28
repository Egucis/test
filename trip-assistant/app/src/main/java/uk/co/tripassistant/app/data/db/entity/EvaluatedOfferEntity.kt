package uk.co.tripassistant.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.OfferOutcome
import uk.co.tripassistant.core.model.Recommendation

/**
 * One evaluated offer (spec sections 31 and 42).
 *
 * What is *not* here matters as much as what is: no screenshot, no rider name, no pickup or
 * destination address. Only the economics of the offer and the decision that was made about it
 * (spec sections 31 and 40).
 */
@Entity(
    tableName = "evaluated_offers",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["fingerprint", "timestamp"])
    ]
)
data class EvaluatedOfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,

    val fare: Double,
    @ColumnInfo(name = "pickup_miles") val pickupMiles: Double,
    @ColumnInfo(name = "trip_miles") val tripMiles: Double,
    @ColumnInfo(name = "total_miles") val totalMiles: Double,
    @ColumnInfo(name = "pickup_minutes") val pickupMinutes: Double?,
    @ColumnInfo(name = "trip_minutes") val tripMinutes: Double?,
    @ColumnInfo(name = "total_minutes") val totalMinutes: Double?,
    @ColumnInfo(name = "rider_rating") val riderRating: Double?,

    @ColumnInfo(name = "pounds_per_mile") val poundsPerMile: Double,
    @ColumnInfo(name = "passenger_pounds_per_mile") val passengerPoundsPerMile: Double,
    @ColumnInfo(name = "pounds_per_hour") val poundsPerHour: Double?,
    @ColumnInfo(name = "pickup_percentage") val pickupPercentage: Double,

    val recommendation: Recommendation,
    @ColumnInfo(name = "primary_reason") val primaryReason: String?,
    @ColumnInfo(name = "all_reasons") val allReasons: String?,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "profile_name") val profileName: String,
    val outcome: OfferOutcome,
    @ColumnInfo(name = "parser_version") val parserVersion: String?,
    val fingerprint: String,
    val confidence: OfferConfidence
)
