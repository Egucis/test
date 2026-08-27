package uk.co.cabcomply.app.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")
private val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")

/** A single master switch for expiry reminders (product spec section 34). Per-document opt-out lives on the document itself. */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val remindersEnabled: Flow<Boolean> = context.notificationDataStore.data.map { it[KEY_REMINDERS_ENABLED] ?: false }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[KEY_REMINDERS_ENABLED] = enabled }
    }
}
