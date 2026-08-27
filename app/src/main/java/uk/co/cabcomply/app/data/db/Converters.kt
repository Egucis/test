package uk.co.cabcomply.app.data.db

import androidx.room.TypeConverter
import uk.co.cabcomply.app.data.db.entity.AttachmentOwnerType
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.db.entity.DocumentOwnerType
import uk.co.cabcomply.app.data.db.entity.DocumentType
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.data.db.entity.MileagePurpose

class Converters {
    @TypeConverter
    fun toInspectionResultStatus(value: String) = enumValueOf<InspectionResultStatus>(value)
    @TypeConverter
    fun fromInspectionResultStatus(value: InspectionResultStatus) = value.name

    @TypeConverter
    fun toDefectStatus(value: String) = enumValueOf<DefectStatus>(value)
    @TypeConverter
    fun fromDefectStatus(value: DefectStatus) = value.name

    @TypeConverter
    fun toMileagePurpose(value: String) = enumValueOf<MileagePurpose>(value)
    @TypeConverter
    fun fromMileagePurpose(value: MileagePurpose) = value.name

    @TypeConverter
    fun toDocumentOwnerType(value: String) = enumValueOf<DocumentOwnerType>(value)
    @TypeConverter
    fun fromDocumentOwnerType(value: DocumentOwnerType) = value.name

    @TypeConverter
    fun toDocumentType(value: String) = enumValueOf<DocumentType>(value)
    @TypeConverter
    fun fromDocumentType(value: DocumentType) = value.name

    @TypeConverter
    fun toAttachmentOwnerType(value: String) = enumValueOf<AttachmentOwnerType>(value)
    @TypeConverter
    fun fromAttachmentOwnerType(value: AttachmentOwnerType) = value.name
}
