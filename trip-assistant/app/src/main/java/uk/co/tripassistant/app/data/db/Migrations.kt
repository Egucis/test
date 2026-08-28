package uk.co.tripassistant.app.data.db

import androidx.room.migration.Migration

/**
 * Explicit migrations only (spec section 41).
 *
 * There is deliberately no `fallbackToDestructiveMigration()` anywhere in this project: a schema
 * change must never be resolved by deleting a driver's history. When the schema changes, bump the
 * version, write the migration, add it here, and add a migration test.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
