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
        WHERE CAST(catalogUid AS TEXT) LIKE '%' || :query || '%'
            OR manufacturer LIKE '%' || :query || '%'
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
    suspend fun insertUidAllocation(allocation: CatalogUidAllocation)

    @Insert
    suspend fun insertPhotos(photos: List<ArtifactPhoto>)

    @Query("SELECT COALESCE(MAX(uid), 0) + 1 FROM catalog_uid_allocations")
    suspend fun nextCatalogUid(): Long

    @Delete
    suspend fun deleteItem(item: ArtifactItem)

    @Transaction
    suspend fun insertItemWithPhotos(item: ArtifactItem, photos: List<ArtifactPhoto>) {
        val uid = nextCatalogUid()
        insertUidAllocation(
            CatalogUidAllocation(
                uid = uid,
                itemId = item.id,
                allocatedAt = item.createdAt,
            ),
        )
        insertItem(item.copy(catalogUid = uid))
        insertPhotos(photos)
    }
}
