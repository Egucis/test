package uk.co.cabcomply.app.data.billing

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.entitlementDataStore by preferencesDataStore(name = "entitlement_store")
