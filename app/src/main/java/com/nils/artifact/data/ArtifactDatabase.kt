package com.nils.artifact.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ArtifactItem::class, ArtifactPhoto::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
