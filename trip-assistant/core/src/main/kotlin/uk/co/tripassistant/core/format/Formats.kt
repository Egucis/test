package uk.co.tripassistant.core.format

import uk.co.tripassistant.core.model.RuleUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number formatting shared by the overlay, history, rule tester and reason text.
 *
 * It lives in :core on purpose — the overlay and the history row must never disagree about what
 * "£1.74/mi" means. V1 is a UK/GBP product (spec sections 1 and 15) so the strings are English
 * and the locale is fixed to UK; see SPEC_COMPLIANCE.md if that ever needs to change.
 */
object Formats {

    private val UK = Locale.UK

    fun money(value: Double): String = String.format(UK, "£%.2f", value)

    /** Whole pounds when the value is exact, otherwise two decimals — for compact targets. */
    fun moneyCompact(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.005) String.format(UK, "£%d", value.roundToInt())
        else money(value)

    fun poundsPerMile(value: Double): String = String.format(UK, "£%.2f/mi", value)

    fun poundsPerHour(value: Double): String = String.format(UK, "£%.0f/h", value)

    /** £/hour with pennies, for the expanded card and history detail where there is room. */
    fun poundsPerHourPrecise(value: Double): String = String.format(UK, "£%.2f/h", value)

    fun miles(value: Double): String = String.format(UK, "%.1f mi", value)

    fun minutes(value: Double): String = String.format(UK, "%d min", value.roundToInt())

    fun rating(value: Double): String = String.format(UK, "★%.2f", value)

    fun percent(value: Double): String = String.format(UK, "%d%%", value.roundToInt())

    /** Formats a rule target in its own unit, for reason text such as "Below £1.50 target". */
    fun target(unit: RuleUnit, value: Double): String = when (unit) {
        RuleUnit.POUNDS_PER_MILE -> String.format(UK, "£%.2f/mi", value)
        RuleUnit.POUNDS_PER_HOUR -> String.format(UK, "£%.0f/h", value)
        RuleUnit.MILES -> miles(value)
        RuleUnit.MINUTES -> minutes(value)
        RuleUnit.RATING -> String.format(UK, "★%.2f", value)
        RuleUnit.POUNDS -> moneyCompact(value)
        RuleUnit.PERCENT -> percent(value)
    }

    /**
     * Target as it reads inside reason text, where the headline has already carried the unit —
     * "Below £1.50 target", "Maximum 4.0 mi" (spec section 23).
     */
    fun targetShort(unit: RuleUnit, value: Double): String = when (unit) {
        RuleUnit.POUNDS_PER_MILE, RuleUnit.POUNDS -> moneyCompact(value)
        RuleUnit.POUNDS_PER_HOUR -> String.format(UK, "£%.0f", value)
        RuleUnit.MILES -> miles(value)
        RuleUnit.MINUTES -> minutes(value)
        RuleUnit.RATING -> String.format(UK, "★%.2f", value)
        RuleUnit.PERCENT -> percent(value)
    }

    /** Formats a measured value in the same unit as its rule, for the left-hand side of a reason. */
    fun actual(unit: RuleUnit, value: Double): String = when (unit) {
        RuleUnit.POUNDS_PER_MILE -> poundsPerMile(value)
        RuleUnit.POUNDS_PER_HOUR -> poundsPerHour(value)
        RuleUnit.MILES -> miles(value)
        RuleUnit.MINUTES -> minutes(value)
        RuleUnit.RATING -> rating(value)
        RuleUnit.POUNDS -> money(value)
        RuleUnit.PERCENT -> percent(value)
    }
}
