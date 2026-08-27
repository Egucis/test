package uk.co.cabcomply.app.data.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudBackupDataStore by preferencesDataStore(name = "cloud_backup_store")

private val KEY_ENABLED = booleanPreferencesKey("enabled")
private val KEY_TREE_URI = stringPreferencesKey("tree_uri")
private val KEY_LAST_SUCCESS = longPreferencesKey("last_success_at")
private val KEY_LAST_ERROR = stringPreferencesKey("last_error")

data class CloudBackupSettings(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val lastSuccessAtMillis: Long? = null,
    val lastError: String? = null
)

/**
 * Where the driver's opt-in choice of an automatic, folder-based daily backup lives. Kept
 * separate from [CabComplyDatabase][uk.co.cabcomply.app.data.db.CabComplyDatabase] so this
 * setting - and the folder permission grant it references - is never itself included inside a
 * CabComply backup file.
 */
@Singleton
class CloudBackupPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = context.cloudBackupDataStore

    val settings: Flow<CloudBackupSettings> = dataStore.data.map { prefs ->
        CloudBackupSettings(
            enabled = prefs[KEY_ENABLED] ?: false,
            treeUri = prefs[KEY_TREE_URI],
            lastSuccessAtMillis = prefs[KEY_LAST_SUCCESS],
            lastError = prefs[KEY_LAST_ERROR]
        )
    }

    suspend fun current(): CloudBackupSettings = settings.first()

    suspend fun setFolder(enabled: Boolean, treeUriString: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
            prefs[KEY_TREE_URI] = treeUriString
            prefs.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    suspend fun recordSuccess(atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUCCESS] = atMillis
            prefs.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST_ERROR] = message }
    }
}
