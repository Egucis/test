package uk.co.cabcomply.app.data.db.entity

/** Result of a single checklist item within one inspection. */
enum class InspectionResultStatus { OK, DEFECT, NOT_APPLICABLE }

/** Simple two-state defect lifecycle: evidence is never deleted, only marked resolved. */
enum class DefectStatus { OPEN, RESOLVED }

/** Used to group mileage for HMRC business-mileage reporting. */
enum class MileagePurpose { BUSINESS, PRIVATE, MIXED }

enum class DocumentOwnerType { DRIVER, VEHICLE }

enum class DocumentType {
    MOT,
    INSURANCE,
    VEHICLE_LICENCE,
    PRIVATE_HIRE_OPERATOR_LICENCE,
    DRIVER_BADGE,
    DRIVER_LICENCE,
    FIRE_EXTINGUISHER,
    FIRST_AID_KIT,
    OTHER
}

enum class AttachmentOwnerType { DEFECT, DEFECT_RESOLUTION, DOCUMENT, INSPECTION }
