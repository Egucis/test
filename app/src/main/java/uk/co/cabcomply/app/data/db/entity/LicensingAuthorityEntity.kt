package uk.co.cabcomply.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A UK taxi/private-hire licensing authority. Predefined authorities are seeded on first run;
 * users can additionally create a Custom / Other authority. Never render a council logo here —
 * authority identity is text-only by design (see product spec section 15).
 */
@Entity(tableName = "licensing_authorities")
data class LicensingAuthorityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String?,
    val isCustom: Boolean,
    val isActive: Boolean
)
