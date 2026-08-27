package uk.co.cabcomply.app.ui.navigation

/** Every navigable route in one place — screens never hardcode a route string elsewhere. */
object Destinations {
    // Onboarding
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_DRIVER = "onboarding/driver"
    const val ONBOARDING_VEHICLE = "onboarding/vehicle"
    const val ONBOARDING_SECURITY = "onboarding/security"
    const val ONBOARDING_FINISH = "onboarding/finish"

    // Main
    const val HOME = "home"
    const val DAILY_CHECK = "daily_check?vehicleId={vehicleId}&quick={quick}"
    fun dailyCheck(vehicleId: String, quick: Boolean = false) = "daily_check?vehicleId=$vehicleId&quick=$quick"

    const val HISTORY = "history"
    const val INSPECTION_DETAIL = "inspection/{inspectionId}"
    fun inspectionDetail(id: String) = "inspection/$id"

    const val MILEAGE = "mileage"
    const val MILEAGE_EDIT = "mileage_edit?entryId={entryId}"
    fun mileageEdit(entryId: String? = null) = "mileage_edit?entryId=${entryId ?: ""}"

    const val DEFECTS = "defects"
    const val DEFECT_DETAIL = "defect/{defectId}"
    fun defectDetail(id: String) = "defect/$id"

    const val DOCUMENTS = "documents"
    const val DOCUMENT_EDIT = "document_edit?documentId={documentId}&ownerType={ownerType}&ownerId={ownerId}"
    fun documentEdit(documentId: String? = null, ownerType: String, ownerId: String) =
        "document_edit?documentId=${documentId ?: ""}&ownerType=$ownerType&ownerId=$ownerId"

    const val VEHICLES = "vehicles"
    const val VEHICLE_EDIT = "vehicle_edit?vehicleId={vehicleId}"
    fun vehicleEdit(vehicleId: String? = null) = "vehicle_edit?vehicleId=${vehicleId ?: ""}"

    const val WEEKLY_REPORT = "weekly_report/{vehicleId}"
    fun weeklyReport(vehicleId: String) = "weekly_report/$vehicleId"

    const val OFFICER_MODE = "officer"

    const val SETTINGS = "settings"
    const val SETTINGS_DRIVER = "settings/driver"
    const val SETTINGS_DAILY_CHECKS = "settings/daily_checks"
    const val SETTINGS_DOCUMENTS_REMINDERS = "settings/documents_reminders"
    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_SUBSCRIPTION = "settings/subscription"
    const val SETTINGS_ABOUT = "settings/about"
    const val PIN_SETUP = "pin_setup"
    const val PIN_CHANGE = "pin_change"
}
