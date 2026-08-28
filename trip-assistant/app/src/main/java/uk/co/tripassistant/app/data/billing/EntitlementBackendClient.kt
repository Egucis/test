package uk.co.tripassistant.app.data.billing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.tripassistant.app.BuildConfig
import uk.co.tripassistant.core.entitlement.EntitlementStatus
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** What the entitlement service says about a purchase. */
data class BackendEntitlement(
    val status: EntitlementStatus,
    val expiryTimeMillis: Long?,
    val autoRenewing: Boolean,
    val productId: String?
)

/**
 * The small backend of spec section 5.
 *
 * Google Play alone tells the app that a purchase exists; only the Play Developer API — which
 * needs a service account, so it has to live on a server — can say whether that purchase is in a
 * grace period, on hold, or expired. This client is the app's half of that conversation.
 *
 * The endpoint is a build configuration value and is empty in source control. With no endpoint
 * configured the app falls back to Play-only verification and says so on the subscription screen,
 * rather than pretending to a level of verification it is not doing.
 *
 * A purchase token is sent over HTTPS to the configured endpoint and nowhere else. It is never
 * logged and never included in an error message (spec section 52).
 */
@Singleton
class EntitlementBackendClient @Inject constructor() {

    private val baseUrl: String = BuildConfig.ENTITLEMENT_BACKEND_URL.trim().trimEnd('/')

    val isConfigured: Boolean get() = baseUrl.isNotEmpty()

    suspend fun verify(
        purchase: PurchaseRecord,
        installId: String,
        packageName: String
    ): Result<BackendEntitlement> {
        if (!isConfigured) {
            return Result.failure(IllegalStateException("No entitlement service configured"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("purchaseToken", purchase.purchaseToken)
                    put("productId", purchase.productId)
                    put("packageName", packageName)
                    put("installId", installId)
                }.toString()

                val connection = (URL("$baseUrl/v1/entitlement").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    if (connection.responseCode !in 200..299) {
                        // No token, no response body in the message: this string may reach a log.
                        error("Entitlement service returned ${connection.responseCode}")
                    }
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parse(JSONObject(response))
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun parse(json: JSONObject): BackendEntitlement {
        val status = runCatching { EntitlementStatus.valueOf(json.getString("status")) }
            .getOrDefault(EntitlementStatus.NONE)
        return BackendEntitlement(
            status = status,
            expiryTimeMillis = json.optLong("expiryTimeMillis", 0L).takeIf { it > 0L },
            autoRenewing = json.optBoolean("autoRenewing", false),
            productId = json.optString("productId").takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
