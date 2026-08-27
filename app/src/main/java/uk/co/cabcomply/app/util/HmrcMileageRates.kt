package uk.co.cabcomply.app.util

/**
 * HMRC's approved mileage allowance payment (AMAP) rates for cars/vans: 45p per business mile
 * for the first 10,000 miles in a tax year, 25p per mile after that. Kept entirely separate from
 * [uk.co.cabcomply.app.data.db.entity.MileageEntryEntity] so a future rate change only affects
 * how existing mileage is *valued*, never the raw recorded miles themselves (product spec
 * section 30).
 */
object HmrcMileageRates {
    private const val TIER_1_RATE_PENCE = 45
    private const val TIER_2_RATE_PENCE = 25
    private const val TIER_1_THRESHOLD_MILES = 10_000

    /**
     * The allowance, in pence, for [milesInThisSegment] business miles that follow
     * [milesAlreadyClaimedThisTaxYear] miles already claimed earlier in the same tax year -
     * splitting the segment across the 45p/25p tiers where it crosses the 10,000-mile threshold.
     */
    fun estimateAllowancePence(milesAlreadyClaimedThisTaxYear: Int, milesInThisSegment: Int): Int {
        if (milesInThisSegment <= 0) return 0
        val remainingAtTier1 = (TIER_1_THRESHOLD_MILES - milesAlreadyClaimedThisTaxYear).coerceIn(0, milesInThisSegment)
        val milesAtTier2 = milesInThisSegment - remainingAtTier1
        return remainingAtTier1 * TIER_1_RATE_PENCE + milesAtTier2 * TIER_2_RATE_PENCE
    }

    fun formatPence(pence: Int): String {
        val pounds = pence / 100
        val remainder = pence % 100
        return "£$pounds.${remainder.toString().padStart(2, '0')}"
    }
}
