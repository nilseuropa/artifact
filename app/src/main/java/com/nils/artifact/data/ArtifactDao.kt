package com.nils.artifact.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    @Transaction
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeItems(): Flow<List<ArtifactWithPhotos>>

    @Transaction
    @Query(
        """
        SELECT * FROM items
        WHERE manufacturer LIKE '%' || :query || '%'
            OR itemType LIKE '%' || :query || '%'
            OR serialNumber LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """,
    )
    fun searchItems(query: String): Flow<List<ArtifactWithPhotos>>

    @Insert
    suspend fun insertItem(item: ArtifactItem)

    @Insert
    suspend fun insertPhotos(photos: List<ArtifactPhoto>)

    @Delete
    suspend fun deleteItem(item: ArtifactItem)

    @Transaction
    suspend fun insertItemWithPhotos(item: ArtifactItem, photos: List<ArtifactPhoto>) {
        insertItem(item)
        insertPhotos(photos)
    }
}
