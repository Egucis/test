package uk.co.cabcomply.app.data.seed

import uk.co.cabcomply.app.data.db.entity.ChecklistEntity
import uk.co.cabcomply.app.data.db.entity.ChecklistItemEntity

/**
 * The default generic daily-check version 1, used until a driver's authority gets its own
 * structured checklist. [DEFAULT_GROUP_ID] is the stable checklist identity that a future v2
 * would share — completed inspections keep pointing at [DEFAULT_CHECKLIST_ID] forever
 * (product spec section 14).
 */
object ChecklistSeedData {
    const val DEFAULT_GROUP_ID = "default_uk_taxi_daily_check"
    const val DEFAULT_CHECKLIST_ID = "default_uk_taxi_daily_check_v1"

    private data class Item(val id: String, val category: String, val name: String, val help: String? = null, val required: Boolean = true)

    private val items = listOf(
        Item("ext_bodywork", "Exterior", "Bodywork condition", "Check for damage that could affect safety or presentation."),
        Item("ext_number_plates", "Exterior", "Number plates", "Clean, undamaged and legible front and rear."),
        Item("ext_doors", "Exterior", "Doors and boot", "Open, close and latch correctly."),
        Item("tyres_nsf", "Tyres and wheels", "Nearside front tyre", "Tread depth, condition, correct pressure."),
        Item("tyres_osf", "Tyres and wheels", "Offside front tyre", "Tread depth, condition, correct pressure."),
        Item("tyres_nsr", "Tyres and wheels", "Nearside rear tyre", "Tread depth, condition, correct pressure."),
        Item("tyres_osr", "Tyres and wheels", "Offside rear tyre", "Tread depth, condition, correct pressure."),
        Item("tyres_spare", "Tyres and wheels", "Spare tyre / repair kit", required = false),
        Item("lights_head", "Lights", "Headlights", "Both dip and main beam working."),
        Item("lights_rear", "Lights", "Rear and brake lights"),
        Item("lights_indicators", "Lights", "Indicators", "All four corners plus hazards."),
        Item("lights_fog", "Lights", "Fog lights", required = false),
        Item("windows_windscreen", "Windows and mirrors", "Windscreen", "No cracks or chips within the driver's view."),
        Item("windows_wipers", "Windows and mirrors", "Wipers and washers"),
        Item("windows_mirrors", "Windows and mirrors", "Mirrors", "All mirrors present, secure and undamaged."),
        Item("interior_seats", "Interior", "Seats and upholstery", "Clean and free of damage."),
        Item("interior_cleanliness", "Interior", "General cleanliness"),
        Item("interior_dashboard_warnings", "Interior", "Dashboard warning lights", "No unexpected warning lights on."),
        Item("seatbelts_all", "Seatbelts", "Seatbelts", "Present, undamaged and functioning in every seating position."),
        Item("safety_fire_extinguisher", "Safety equipment", "Fire extinguisher", "Present, in date and correctly mounted."),
        Item("safety_first_aid", "Safety equipment", "First aid kit", "Present and in date."),
        Item("safety_warning_triangle", "Safety equipment", "Warning triangle / hi-vis", required = false),
        Item("access_ramp", "Accessibility equipment", "Wheelchair ramp / lift", "If fitted: deploys and stows correctly.", required = false),
        Item("access_signage", "Accessibility equipment", "Accessibility signage", required = false),
        Item("controls_horn", "Driver controls", "Horn"),
        Item("controls_handbrake", "Driver controls", "Handbrake"),
        Item("controls_footbrake", "Driver controls", "Footbrake", "No unusual travel, noise or pulling."),
        Item("controls_steering", "Driver controls", "Steering", "No excessive play or noise.")
    )

    fun checklist(): ChecklistEntity = ChecklistEntity(
        id = DEFAULT_CHECKLIST_ID,
        checklistGroupId = DEFAULT_GROUP_ID,
        licensingAuthorityId = null,
        name = "Standard Daily Vehicle Check",
        version = 1,
        effectiveDate = 0L,
        isCustom = false,
        isActive = true
    )

    fun checklistItems(): List<ChecklistItemEntity> = items.mapIndexed { index, item ->
        ChecklistItemEntity(
            id = item.id,
            checklistId = DEFAULT_CHECKLIST_ID,
            category = item.category,
            displayOrder = index,
            name = item.name,
            helpText = item.help,
            isRequired = item.required
        )
    }
}
