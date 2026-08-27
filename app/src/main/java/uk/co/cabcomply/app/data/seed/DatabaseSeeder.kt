package uk.co.cabcomply.app.data.seed

import uk.co.cabcomply.app.data.db.dao.ChecklistDao
import uk.co.cabcomply.app.data.db.dao.LicensingAuthorityDao
import javax.inject.Inject
import javax.inject.Singleton

/** Populates predefined authorities and the default checklist on first run only; never overwrites existing rows. */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val licensingAuthorityDao: LicensingAuthorityDao,
    private val checklistDao: ChecklistDao
) {
    suspend fun seedIfNeeded() {
        if (licensingAuthorityDao.count() == 0) {
            licensingAuthorityDao.insertAll(AuthoritySeedData.authorities)
        }
        if (checklistDao.count() == 0) {
            checklistDao.insertChecklists(listOf(ChecklistSeedData.checklist()))
            checklistDao.insertItems(ChecklistSeedData.checklistItems())
        }
    }
}
