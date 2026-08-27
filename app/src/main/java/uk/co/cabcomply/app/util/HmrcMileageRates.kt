package uk.co.cabcomply.app.util

/**
 * HMRC's approved mileage allowance payment (AMAP) rates for cars/vans: 45p per business mile
 * for the first 10,000 miles in a tax year, 25p per mile after that. Kept entirely separate from
 * [uk.co.cabcomply.app.data.db.entity.MileageEntryEntity] so a future rate change only affects
 * how existing mileage is *valued*, never the raw recorded miles themselves (product spec
 * section 30). The rate is expressed as a [RateProfile] rather than hardcoded constants so a
 * driver can correct it from the Mileage screen the day HMRC changes it, without waiting on an
 * app update — see [uk.co.cabcomply.app.data.mileage.HmrcRateRepository].
 */
object HmrcMileageRates {
    data class RateProfile(
        val tier1Pence: Int,
        val tier2Pence: Int,
        val thresholdMiles: Int
    )

    /** CabComply's built-in default, applied whenever a driver hasn't overridden a tax year. */
    fun defaultProfile(taxYearStart: Int): RateProfile = RateProfile(
        tier1Pence = 45,
        tier2Pence = 25,
        thresholdMiles = 10_000
    )

    /**
     * The allowance, in pence, for [milesInThisSegment] business miles that follow
     * [milesAlreadyClaimedThisTaxYear] miles already claimed earlier in the same tax year -
     * splitting the segment across [profile]'s two tiers where it crosses the threshold.
     */
    fun estimateAllowancePence(profile: RateProfile, milesAlreadyClaimedThisTaxYear: Int, milesInThisSegment: Int): Int {
        if (milesInThisSegment <= 0) return 0
        val remainingAtTier1 = (profile.thresholdMiles - milesAlreadyClaimedThisTaxYear).coerceIn(0, milesInThisSegment)
        val milesAtTier2 = milesInThisSegment - remainingAtTier1
        return remainingAtTier1 * profile.tier1Pence + milesAtTier2 * profile.tier2Pence
    }

    fun formatPence(pence: Int): String {
        val pounds = pence / 100
        val remainder = pence % 100
        return "£$pounds.${remainder.toString().padStart(2, '0')}"
    }
}
