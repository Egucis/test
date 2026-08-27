package uk.co.cabcomply.app.data.pdf

/**
 * Everything needed to render one weekly compliance report. Both the PDF generator and the
 * Officer Mode weekly view are built from this same model so the two never disagree
 * (product spec section 89).
 */
data class WeeklyReportData(
    val vehicleRegistration: String,
    val vehicleMakeModel: String,
    val driverName: String,
    val licensingAuthorityName: String?,
    val weekStartLabel: String,
    val weekEndLabel: String,
    val days: List<DailyCheckSummary>,
    val mileageTotalMiles: Int,
    val mileageBusinessMiles: Int,
    val defects: List<DefectSummary>,
    val generatedAtLabel: String
)

data class DailyCheckSummary(
    val dateLabel: String,
    val dayOfWeekLabel: String,
    val completed: Boolean,
    val completionTimeLabel: String?,
    val odometer: Int?,
    val hasDefect: Boolean,
    val isQuickCheck: Boolean
)

data class DefectSummary(
    val dateLabel: String,
    val checklistItem: String,
    val description: String,
    val statusLabel: String
)
