package uk.co.tripassistant.app.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.tripassistant.core.entitlement.EntitlementSnapshot
import uk.co.tripassistant.core.entitlement.EntitlementStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "entitlement")

/**
 * The cached entitlement snapshot (spec section 42).
 *
 * Cached so a tunnel does not end a shift, and never treated as proof: [EntitlementPolicy] bounds
 * how long an unrefreshed cache is honoured. The install id is a random value generated on this
 * device — it identifies the installation to the entitlement service, nothing else, and replaces
 * asking the driver for an account (spec section 6).
 */
@Singleton
class EntitlementStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val STATUS = stringPreferencesKey("status")
        val PRODUCT_ID = stringPreferencesKey("product_id")
        val EXPIRY = longPreferencesKey("expiry_time")
        val LAST_VERIFIED = longPreferencesKey("last_verified")
        val TRIAL_STARTED = longPreferencesKey("trial_started")
        val AUTO_RENEWING = booleanPreferencesKey("auto_renewing")
        val INSTALL_ID = stringPreferencesKey("install_id")
    }

    val snapshot: Flow<EntitlementSnapshot> = context.entitlementDataStore.data.map { prefs ->
        EntitlementSnapshot(
            status = prefs[Keys.STATUS]
                ?.let { runCatching { EntitlementStatus.valueOf(it) }.getOrNull() }
                ?: EntitlementStatus.NONE,
            productId = prefs[Keys.PRODUCT_ID],
            expiryTimeMillis = prefs[Keys.EXPIRY]?.takeIf { it > 0L },
            lastVerifiedAtMillis = prefs[Keys.LAST_VERIFIED]?.takeIf { it > 0L },
            trialStartedAtMillis = prefs[Keys.TRIAL_STARTED]?.takeIf { it > 0L },
            autoRenewing = prefs[Keys.AUTO_RENEWING] ?: false
        )
    }

    suspend fun current(): EntitlementSnapshot = snapshot.first()

    suspend fun save(snapshot: EntitlementSnapshot) {
        context.entitlementDataStore.edit { prefs ->
            prefs[Keys.STATUS] = snapshot.status.name
            snapshot.productId?.let { prefs[Keys.PRODUCT_ID] = it } ?: prefs.remove(Keys.PRODUCT_ID)
            prefs[Keys.EXPIRY] = snapshot.expiryTimeMillis ?: 0L
            prefs[Keys.LAST_VERIFIED] = snapshot.lastVerifiedAtMillis ?: 0L
            prefs[Keys.TRIAL_STARTED] = snapshot.trialStartedAtMillis ?: 0L
            prefs[Keys.AUTO_RENEWING] = snapshot.autoRenewing
        }
    }

    /** Starts the install trial the first time the app is opened, and never restarts it. */
    suspend fun startTrialIfNeeded(now: Long): EntitlementSnapshot {
        val existing = current()
        if (existing.trialStartedAtMillis != null) return existing
        val started = existing.copy(trialStartedAtMillis = now)
        save(started)
        return started
    }

    /** A random, device-local identifier. Not derived from any hardware or account id. */
    suspend fun installId(): String {
        context.entitlementDataStore.data.first()[Keys.INSTALL_ID]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.entitlementDataStore.edit { it[Keys.INSTALL_ID] = generated }
        return generated
    }
}
