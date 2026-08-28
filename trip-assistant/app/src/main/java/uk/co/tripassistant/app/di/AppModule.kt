package uk.co.tripassistant.app.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.co.tripassistant.app.data.billing.BillingDataSource
import uk.co.tripassistant.app.data.billing.PlayBillingDataSource
import uk.co.tripassistant.app.data.db.Migrations
import uk.co.tripassistant.app.data.db.TripAssistantDatabase
import uk.co.tripassistant.app.data.db.dao.OfferDao
import uk.co.tripassistant.app.data.db.dao.ProfileDao
import uk.co.tripassistant.core.parser.ParserRegistry
import uk.co.tripassistant.core.pipeline.OfferAnalyzer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TripAssistantDatabase =
        Room.databaseBuilder(context, TripAssistantDatabase::class.java, TripAssistantDatabase.NAME)
            // No fallbackToDestructiveMigration, ever: a schema change must never cost a driver
            // their history (spec section 41).
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun profileDao(database: TripAssistantDatabase): ProfileDao = database.profileDao()

    @Provides
    fun offerDao(database: TripAssistantDatabase): OfferDao = database.offerDao()

    @Provides
    @Singleton
    fun parserRegistry(): ParserRegistry = ParserRegistry()

    @Provides
    @Singleton
    fun offerAnalyzer(registry: ParserRegistry): OfferAnalyzer = OfferAnalyzer(registry)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    /** The only place the Play implementation is named outside its own file. */
    @Binds
    @Singleton
    abstract fun billingDataSource(source: PlayBillingDataSource): BillingDataSource
}
