package uk.co.cabcomply.app.data.pdf

/**
 * Everything needed to render one weekly compliance report. Both the PDF generator and the
 * Officer Mode weekly view are built from this same model so the two never disagree
 * (product spec section 89). The report is a full item-by-item matrix — every checklist item as
 * a row, every day of the week as a column — matching the format a licensing officer expects to
 * see, not just a completion summary.
 */
data class WeeklyReportData(
    val vehicleRegistration: String,
    val vehicleMakeModel: String,
    val driverName: String,
    val licensingAuthorityName: String?,
    val weekStartLabel: String,
    val weekEndLabel: String,
    val dayHeaders: List<WeeklyReportDayHeader>,
    val itemRows: List<WeeklyReportItemRow>,
    val mileageTotalMiles: Int,
    val mileageBusinessMiles: Int,
    val defects: List<DefectSummary>,
    val driverSignatures: List<String>,
    val generatedAtLabel: String
)

data class WeeklyReportDayHeader(
    val dayLetter: String,
    val dateLabel: String,
    val timeLabel: String?,
    val odometerLabel: String?,
    val completed: Boolean
)

enum class ItemDayStatus { OK, DEFECT, NOT_APPLICABLE, NOT_RECORDED }

data class WeeklyReportItemRow(
    val itemName: String,
    val statuses: List<ItemDayStatus>
)

data class DefectSummary(
    val dateLabel: String,
    val checklistItem: String,
    val description: String,
    val statusLabel: String,
    val resolutionNote: String?
)
