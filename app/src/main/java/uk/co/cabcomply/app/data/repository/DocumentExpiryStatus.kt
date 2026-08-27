package uk.co.cabcomply.app.data.repository

enum class DocumentExpiryStatus { NO_EXPIRY, VALID, EXPIRING_SOON, EXPIRED }

private const val EXPIRING_SOON_WINDOW_DAYS = 30
private const val MILLIS_PER_DAY = 86_400_000L

fun expiryStatusFor(expiryDate: Long?, nowMillis: Long): DocumentExpiryStatus {
    if (expiryDate == null) return DocumentExpiryStatus.NO_EXPIRY
    val daysRemaining = (expiryDate - nowMillis) / MILLIS_PER_DAY
    return when {
        daysRemaining < 0 -> DocumentExpiryStatus.EXPIRED
        daysRemaining <= EXPIRING_SOON_WINDOW_DAYS -> DocumentExpiryStatus.EXPIRING_SOON
        else -> DocumentExpiryStatus.VALID
    }
}
