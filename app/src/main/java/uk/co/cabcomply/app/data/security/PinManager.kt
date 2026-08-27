package uk.co.cabcomply.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the driver's optional PIN as a salted PBKDF2 hash only — never as plain text — inside
 * an EncryptedSharedPreferences file backed by the Android Keystore (product spec section 45).
 * "Protect app with PIN" and "protect records/sensitive actions" are independent flags so a
 * driver who wants one without the other can have exactly that (product spec section 44).
 */
@Singleton
class PinManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cabcomply_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    var appLockEnabled: Boolean
        get() = isPinSet() && prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var recordProtectionEnabled: Boolean
        get() = isPinSet() && prefs.getBoolean(KEY_RECORD_PROTECTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_PROTECTION_ENABLED, value).apply()

    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4 to 8 digits." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedSalt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val candidate = hash(pin, salt)
        return Base64.encodeToString(candidate, Base64.NO_WRAP) == storedHash
    }

    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        setPin(newPin)
        return true
    }

    /** Deliberate reset path for a forgotten PIN: turns protection off rather than exposing data any other way. */
    fun disablePinProtection() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .putBoolean(KEY_RECORD_PROTECTION_ENABLED, false)
            .apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_RECORD_PROTECTION_ENABLED = "record_protection_enabled"
    }
}
