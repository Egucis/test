package uk.co.tripassistant.core.model

/** Whether a rule sets a floor or a ceiling — this decides which side the amber band sits on. */
enum class RuleDirection { MINIMUM, MAXIMUM }

/** The unit a rule's target is expressed in, so the UI and reason text can format it. */
enum class RuleUnit { POUNDS_PER_MILE, POUNDS_PER_HOUR, MILES, MINUTES, RATING, POUNDS, PERCENT }

/**
 * The seven rules of spec section 19.
 *
 * [reasonPriority] orders the "why is this not GOOD" text (spec section 23): money first, then
 * unpaid pickup mileage, then the softer qualifiers. Lower sorts first.
 */
enum class RuleId(
    val direction: RuleDirection,
    val unit: RuleUnit,
    val displayName: String,
    /** Prefix used in overlay reason text, e.g. "Pickup 6.2 mi". Blank where the value speaks for itself. */
    val shortName: String,
    /** Name of the underlying measurement, used when it could not be read at all. */
    val metricLabel: String,
    val reasonPriority: Int
) {
    MIN_POUNDS_PER_MILE(RuleDirection.MINIMUM, RuleUnit.POUNDS_PER_MILE, "Minimum £ per mile", "", "£/mile", 0),
    MIN_POUNDS_PER_HOUR(RuleDirection.MINIMUM, RuleUnit.POUNDS_PER_HOUR, "Minimum £ per hour", "", "£/hour", 1),
    MAX_PICKUP_MILES(RuleDirection.MAXIMUM, RuleUnit.MILES, "Maximum pickup distance", "Pickup", "Pickup distance", 2),
    MAX_PICKUP_PERCENT(RuleDirection.MAXIMUM, RuleUnit.PERCENT, "Maximum pickup proportion", "Pickup", "Pickup share", 3),
    MIN_FARE(RuleDirection.MINIMUM, RuleUnit.POUNDS, "Minimum total fare", "Fare", "Fare", 4),
    MAX_PICKUP_MINUTES(RuleDirection.MAXIMUM, RuleUnit.MINUTES, "Maximum pickup time", "Pickup", "Pickup time", 5),
    MIN_RIDER_RATING(RuleDirection.MINIMUM, RuleUnit.RATING, "Minimum rider rating", "Rider", "Rider rating", 6);

    companion object {
        /** Stable order for settings screens. */
        val displayOrder: List<RuleId> = listOf(
            MIN_POUNDS_PER_MILE,
            MIN_POUNDS_PER_HOUR,
            MIN_FARE,
            MAX_PICKUP_MILES,
            MAX_PICKUP_MINUTES,
            MAX_PICKUP_PERCENT,
            MIN_RIDER_RATING
        )
    }
}

/**
 * One rule inside a profile.
 *
 * [enabled] and [importance] are kept separate because the data model in spec section 42 lists
 * both. They combine as [isActive]: a rule only counts when it is switched on *and* its
 * importance is not OFF.
 */
data class RuleSetting(
    val enabled: Boolean,
    val importance: RuleImportance,
    val target: Double
) {
    val isActive: Boolean get() = enabled && importance != RuleImportance.OFF
}

/**
 * A named set of thresholds (spec section 18). Exactly one profile is active at a time.
 *
 * [amberTolerancePercent] is the global amber band width for this profile, expressed as a
 * percentage (10.0 means 10%, the default of spec section 20).
 */
data class RuleProfile(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val rules: Map<RuleId, RuleSetting>,
    val amberTolerancePercent: Double,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun rule(id: RuleId): RuleSetting? = rules[id]

    fun activeRules(): List<Pair<RuleId, RuleSetting>> =
        RuleId.displayOrder.mapNotNull { id -> rules[id]?.takeIf { it.isActive }?.let { id to it } }

    /** Tolerance as a fraction, clamped to something sane so a bad value cannot invert the bands. */
    val toleranceFraction: Double
        get() = (amberTolerancePercent / 100.0).coerceIn(0.0, 0.9)
}
