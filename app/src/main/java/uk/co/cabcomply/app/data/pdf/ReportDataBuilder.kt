package uk.co.cabcomply.app.data.pdf

import uk.co.cabcomply.app.data.db.entity.DefectStatus
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
 * same figures (product spec section 89).
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

        val days = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val inspection = inspections
                .filter { it.inspectionDate == dayStartMillis }
                .maxByOrNull { it.completedAt ?: 0L }
            val defectsForDay = inspection?.let { inspectionRepository.getDefects(it.id) }.orEmpty()
            DailyCheckSummary(
                dateLabel = DateFormatting.formatDate(dayStartMillis),
                dayOfWeekLabel = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                completed = inspection != null,
                completionTimeLabel = inspection?.completedAt?.let { DateFormatting.formatTime(it) },
                odometer = inspection?.odometer,
                hasDefect = defectsForDay.isNotEmpty(),
                isQuickCheck = inspection?.isQuickCheck ?: false
            )
        }

        val defectSummaries = inspections.flatMap { inspection ->
            inspectionRepository.getDefects(inspection.id).map { defect ->
                DefectSummary(
                    dateLabel = DateFormatting.formatDate(inspection.inspectionDate),
                    checklistItem = defect.checklistItemNameSnapshot,
                    description = defect.description,
                    statusLabel = if (defect.status == DefectStatus.RESOLVED) "Resolved" else "Open"
                )
            }
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
            days = days,
            mileageTotalMiles = totalMiles,
            mileageBusinessMiles = businessMiles,
            defects = defectSummaries,
            generatedAtLabel = DateFormatting.formatDateTime(clock.nowMillis())
        )
    }
}
