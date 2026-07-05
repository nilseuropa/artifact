package com.nils.artifact.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ArtifactItem::class, ArtifactPhoto::class, CatalogUidAllocation::class],
    version = 3,
    exportSchema = true,
)
abstract class ArtifactDatabase : RoomDatabase() {
    abstract fun artifactDao(): ArtifactDao

    companion object {
        @Volatile
        private var instance: ArtifactDatabase? = null

        fun get(context: Context): ArtifactDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArtifactDatabase::class.java,
                    "artifact.db",
                )
                    .addMigrations(Migration1To2, Migration2To3)
                    .build()
                    .also { instance = it }
            }

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN manufacturerId TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN modelId TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN productionYearStart INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN productionYearEnd INTEGER")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_uid_allocations (
                        uid INTEGER NOT NULL,
                        itemId TEXT NOT NULL,
                        allocatedAt INTEGER NOT NULL,
                        PRIMARY KEY(uid)
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE items ADD COLUMN catalogUid INTEGER NOT NULL DEFAULT 0")

                val cursor = db.query("SELECT id, createdAt FROM items ORDER BY createdAt ASC, id ASC")
                cursor.use {
                    var nextUid = 1L
                    while (it.moveToNext()) {
                        val itemId = it.getString(0)
                        val createdAt = it.getLong(1)
                        db.execSQL(
                            "UPDATE items SET catalogUid = ? WHERE id = ?",
                            arrayOf(nextUid, itemId),
                        )
                        db.execSQL(
                            """
                            INSERT INTO catalog_uid_allocations(uid, itemId, allocatedAt)
                            VALUES (?, ?, ?)
                            """.trimIndent(),
                            arrayOf(nextUid, itemId, createdAt),
                        )
                        nextUid += 1
                    }
                }

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_items_catalogUid ON items(catalogUid)",
                )
            }
        }
    }
}
