package uk.co.cabcomply.app.data.pdf

import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.data.repository.AuthorityRepository
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.repository.MileageRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a [WeeklyReportData] snapshot for one vehicle and one Monday-to-Sunday week. Used by
 * both the weekly PDF generator and the Officer Mode weekly view so the two always show the
 * same figures (product spec section 89). Pivots each day's checklist results into one
 * item-by-item matrix, the same shape a licensing officer expects to see on paper.
 */
@Singleton
class ReportDataBuilder @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val driverRepository: DriverRepository,
    private val authorityRepository: AuthorityRepository,
    private val inspectionRepository: InspectionRepository,
    private val mileageRepository: MileageRepository,
    private val clock: AppClock
) {
    suspend fun buildWeeklyReport(vehicleId: String, weekStart: LocalDate): WeeklyReportData {
        val vehicle = vehicleRepository.getById(vehicleId) ?: error("Vehicle not found.")
        val driver = driverRepository.getProfile()
        val authority = vehicle.licensingAuthorityId?.let { authorityRepository.getById(it) }

        val zone = clock.zoneId()
        val weekEnd = weekStart.plusDays(6)
        val fromMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = weekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val inspections = inspectionRepository.getHistorySnapshot(vehicleId, fromMillis, toMillis)
        val mileageEntries = mileageRepository.getFilteredSnapshot(vehicleId, fromMillis, toMillis)

        // The inspection actually used for each of the 7 days (null if no check was completed).
        val inspectionByDay: List<InspectionEntity?> = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            inspections.filter { it.inspectionDate == dayStartMillis }.maxByOrNull { it.completedAt ?: 0L }
        }
        val resultsByDay: List<List<InspectionResultEntity>> = inspectionByDay.map { inspection ->
            inspection?.let { inspectionRepository.getResults(it.id) }.orEmpty()
        }

        val dayHeaders = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val inspection = inspectionByDay[offset]
            WeeklyReportDayHeader(
                dayLetter = date.dayOfWeek.name.take(1),
                dateLabel = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                timeLabel = inspection?.completedAt?.let { DateFormatting.formatTime(it) },
                odometerLabel = inspection?.let { "${it.odometer}mi" },
                completed = inspection != null
            )
        }

        // Union of every checklist item seen across the week, in the order first encountered,
        // so a checklist that changed mid-week (a new version) still produces one readable table.
        val itemOrder = LinkedHashMap<String, String>() // itemName snapshot, keyed by checklistItemId
        resultsByDay.forEach { dayResults ->
            dayResults.sortedBy { it.displayOrderSnapshot }.forEach { result ->
                itemOrder.putIfAbsent(result.checklistItemId, result.itemNameSnapshot)
            }
        }

        val itemRows = itemOrder.map { (itemId, itemName) ->
            val statuses = resultsByDay.mapIndexed { dayIndex, dayResults ->
                if (inspectionByDay[dayIndex] == null) {
                    ItemDayStatus.NOT_RECORDED
                } else {
                    when (dayResults.firstOrNull { it.checklistItemId == itemId }?.status) {
                        InspectionResultStatus.DEFECT -> ItemDayStatus.DEFECT
                        InspectionResultStatus.NOT_APPLICABLE -> ItemDayStatus.NOT_APPLICABLE
                        InspectionResultStatus.OK -> ItemDayStatus.OK
                        null -> ItemDayStatus.NOT_RECORDED
                    }
                }
            }
            WeeklyReportItemRow(itemName = itemName, statuses = statuses)
        }

        val defectSummaries = inspections.sortedBy { it.inspectionDate }.flatMap { inspection ->
            inspectionRepository.getDefects(inspection.id).map { defect ->
                DefectSummary(
                    dateLabel = DateFormatting.formatDate(inspection.inspectionDate),
                    checklistItem = defect.checklistItemNameSnapshot,
                    description = defect.description,
                    statusLabel = if (defect.status == DefectStatus.RESOLVED) "Resolved" else "Open",
                    resolutionNote = defect.resolutionNote
                )
            }
        }

        val driverSignatures = inspectionByDay.mapIndexedNotNull { offset, inspection ->
            if (inspection?.completedAt == null) return@mapIndexedNotNull null
            val date = weekStart.plusDays(offset.toLong())
            "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} " +
                "${DateFormatting.formatTime(inspection.completedAt)} ${inspection.driverNameSnapshot}"
        }

        val completedMileage = mileageEntries.filter { it.endMileage != null }
        val totalMiles = completedMileage.sumOf { it.endMileage!! - it.startMileage }
        val businessMiles = completedMileage
            .filter { it.purpose == MileagePurpose.BUSINESS }
            .sumOf { it.endMileage!! - it.startMileage }

        return WeeklyReportData(
            vehicleRegistration = vehicle.registration,
            vehicleMakeModel = "${vehicle.make} ${vehicle.model}".trim(),
            driverName = driver?.name ?: "Not set",
            licensingAuthorityName = authority?.name,
            weekStartLabel = DateFormatting.formatDate(fromMillis),
            weekEndLabel = DateFormatting.formatDate(toMillis),
            dayHeaders = dayHeaders,
            itemRows = itemRows,
            mileageTotalMiles = totalMiles,
            mileageBusinessMiles = businessMiles,
            defects = defectSummaries,
            driverSignatures = driverSignatures,
            generatedAtLabel = DateFormatting.formatDateTime(clock.nowMillis())
        )
    }
}
