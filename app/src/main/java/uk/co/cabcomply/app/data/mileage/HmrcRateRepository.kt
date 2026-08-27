package uk.co.cabcomply.app.data.mileage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.cabcomply.app.util.HmrcMileageRates
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hmrcRateDataStore by preferencesDataStore(name = "hmrc_rate_store")

/**
 * Per-tax-year overrides for HMRC's AMAP rates. Stored separately from
 * [uk.co.cabcomply.app.data.db.entity.MileageEntryEntity] so an override only ever changes how
 * mileage is *valued*, never the raw recorded readings, and a driver can correct the rate the
 * day HMRC changes it rather than waiting for an app update.
 */
@Singleton
class HmrcRateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.hmrcRateDataStore

    fun observeProfile(taxYearStart: Int): Flow<HmrcMileageRates.RateProfile> =
        dataStore.data.map { prefs ->
            val default = HmrcMileageRates.defaultProfile(taxYearStart)
            HmrcMileageRates.RateProfile(
                tier1Pence = prefs[tier1Key(taxYearStart)] ?: default.tier1Pence,
                tier2Pence = prefs[tier2Key(taxYearStart)] ?: default.tier2Pence,
                thresholdMiles = prefs[thresholdKey(taxYearStart)] ?: default.thresholdMiles
            )
        }

    suspend fun getProfile(taxYearStart: Int): HmrcMileageRates.RateProfile = observeProfile(taxYearStart).first()

    suspend fun isOverridden(taxYearStart: Int): Boolean {
        val prefs = dataStore.data.first()
        return prefs[tier1Key(taxYearStart)] != null
    }

    suspend fun setProfile(taxYearStart: Int, profile: HmrcMileageRates.RateProfile) {
        dataStore.edit { prefs ->
            prefs[tier1Key(taxYearStart)] = profile.tier1Pence
            prefs[tier2Key(taxYearStart)] = profile.tier2Pence
            prefs[thresholdKey(taxYearStart)] = profile.thresholdMiles
        }
    }

    suspend fun resetToDefault(taxYearStart: Int) {
        dataStore.edit { prefs ->
            prefs.remove(tier1Key(taxYearStart))
            prefs.remove(tier2Key(taxYearStart))
            prefs.remove(thresholdKey(taxYearStart))
        }
    }

    private fun tier1Key(taxYearStart: Int) = intPreferencesKey("tier1_pence_$taxYearStart")
    private fun tier2Key(taxYearStart: Int) = intPreferencesKey("tier2_pence_$taxYearStart")
    private fun thresholdKey(taxYearStart: Int) = intPreferencesKey("threshold_miles_$taxYearStart")
}
