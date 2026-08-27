package uk.co.cabcomply.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.co.cabcomply.app.data.db.CabComplyDatabase
import uk.co.cabcomply.app.data.db.dao.AttachmentDao
import uk.co.cabcomply.app.data.db.dao.ChecklistDao
import uk.co.cabcomply.app.data.db.dao.DefectDao
import uk.co.cabcomply.app.data.db.dao.DocumentDao
import uk.co.cabcomply.app.data.db.dao.DriverProfileDao
import uk.co.cabcomply.app.data.db.dao.InspectionDao
import uk.co.cabcomply.app.data.db.dao.InspectionResultDao
import uk.co.cabcomply.app.data.db.dao.LicensingAuthorityDao
import uk.co.cabcomply.app.data.db.dao.MileageDao
import uk.co.cabcomply.app.data.db.dao.VehicleDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CabComplyDatabase =
        Room.databaseBuilder(context, CabComplyDatabase::class.java, CabComplyDatabase.DATABASE_NAME)
            .build()

    @Provides fun provideDriverProfileDao(db: CabComplyDatabase): DriverProfileDao = db.driverProfileDao()
    @Provides fun provideLicensingAuthorityDao(db: CabComplyDatabase): LicensingAuthorityDao = db.licensingAuthorityDao()
    @Provides fun provideVehicleDao(db: CabComplyDatabase): VehicleDao = db.vehicleDao()
    @Provides fun provideChecklistDao(db: CabComplyDatabase): ChecklistDao = db.checklistDao()
    @Provides fun provideInspectionDao(db: CabComplyDatabase): InspectionDao = db.inspectionDao()
    @Provides fun provideInspectionResultDao(db: CabComplyDatabase): InspectionResultDao = db.inspectionResultDao()
    @Provides fun provideDefectDao(db: CabComplyDatabase): DefectDao = db.defectDao()
    @Provides fun provideAttachmentDao(db: CabComplyDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun provideMileageDao(db: CabComplyDatabase): MileageDao = db.mileageDao()
    @Provides fun provideDocumentDao(db: CabComplyDatabase): DocumentDao = db.documentDao()
}
