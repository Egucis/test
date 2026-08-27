package uk.co.cabcomply.app.data.seed

import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity

/**
 * Initial predefined UK licensing authorities. Adding another authority later is a matter of
 * appending another row here (or letting an admin-configured source populate this table) —
 * never a UI code change (product spec section 15).
 */
object AuthoritySeedData {
    const val CUSTOM_AUTHORITY_ID = "custom_other"

    val authorities: List<LicensingAuthorityEntity> = listOf(
        LicensingAuthorityEntity(
            id = "north_northamptonshire",
            name = "North Northamptonshire Council",
            region = "East Midlands",
            isCustom = false,
            isActive = true
        ),
        LicensingAuthorityEntity(
            id = "west_northamptonshire",
            name = "West Northamptonshire Council",
            region = "East Midlands",
            isCustom = false,
            isActive = true
        ),
        LicensingAuthorityEntity(
            id = "peterborough",
            name = "Peterborough City Council",
            region = "East of England",
            isCustom = false,
            isActive = true
        ),
        LicensingAuthorityEntity(
            id = "rutland",
            name = "Rutland County Council",
            region = "East Midlands",
            isCustom = false,
            isActive = true
        ),
        LicensingAuthorityEntity(
            id = "wolverhampton",
            name = "Wolverhampton City Council",
            region = "West Midlands",
            isCustom = false,
            isActive = true
        ),
        LicensingAuthorityEntity(
            id = CUSTOM_AUTHORITY_ID,
            name = "Custom / Other",
            region = null,
            isCustom = true,
            isActive = true
        )
    )
}
