package uk.co.tripassistant.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.tripassistant.app.data.db.dao.OfferDao
import uk.co.tripassistant.app.data.db.dao.ProfileDao
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.app.data.db.entity.RuleProfileEntity

@Database(
    entities = [RuleProfileEntity::class, EvaluatedOfferEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TripAssistantDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun offerDao(): OfferDao

    companion object {
        const val NAME = "trip_assistant.db"
    }
}
