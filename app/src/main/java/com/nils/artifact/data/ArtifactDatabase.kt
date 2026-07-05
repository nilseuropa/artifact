package com.nils.artifact.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ArtifactItem::class, ArtifactPhoto::class],
    version = 2,
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
                    .addMigrations(Migration1To2)
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
    }
}
