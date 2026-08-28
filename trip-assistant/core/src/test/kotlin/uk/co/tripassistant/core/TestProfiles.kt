package uk.co.tripassistant.core

import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleImportance
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.RuleSetting

/** Small builders so each test states only the rules it is actually about. */
object TestProfiles {

    fun of(
        vararg rules: Pair<RuleId, RuleSetting>,
        tolerancePercent: Double = 10.0,
        name: String = "Test"
    ): RuleProfile = RuleProfile(
        id = 1L,
        name = name,
        isActive = true,
        rules = rules.toMap(),
        amberTolerancePercent = tolerancePercent,
        createdAt = 0L,
        updatedAt = 0L
    )

    fun soft(target: Double) = RuleSetting(enabled = true, importance = RuleImportance.SOFT, target = target)
    fun hard(target: Double) = RuleSetting(enabled = true, importance = RuleImportance.HARD, target = target)
    fun off(target: Double) = RuleSetting(enabled = false, importance = RuleImportance.OFF, target = target)

    /** The profile from the worked example in spec section 22. */
    fun specSection22(): RuleProfile = of(
        RuleId.MIN_POUNDS_PER_MILE to soft(1.50),
        RuleId.MIN_POUNDS_PER_HOUR to soft(25.0),
        RuleId.MAX_PICKUP_MILES to soft(4.0),
        RuleId.MIN_RIDER_RATING to hard(4.75)
    )
}
