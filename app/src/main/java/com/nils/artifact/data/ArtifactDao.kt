package com.nils.artifact.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
    suspend fun insertItems(items: List<ArtifactItem>)

    @Insert
    suspend fun insertUidAllocation(allocation: CatalogUidAllocation)

    @Insert
    suspend fun insertUidAllocations(allocations: List<CatalogUidAllocation>)

    @Insert
    suspend fun insertPhotos(photos: List<ArtifactPhoto>)

    @Update
    suspend fun updateItem(item: ArtifactItem)

    @Query("SELECT COALESCE(MAX(uid), 0) + 1 FROM catalog_uid_allocations")
    suspend fun nextCatalogUid(): Long

    @Delete
    suspend fun deleteItem(item: ArtifactItem)

    @Query("SELECT * FROM items ORDER BY createdAt ASC, id ASC")
    suspend fun getAllItems(): List<ArtifactItem>

    @Query("SELECT * FROM photos ORDER BY createdAt ASC, id ASC")
    suspend fun getAllPhotos(): List<ArtifactPhoto>

    @Query("SELECT * FROM catalog_uid_allocations ORDER BY uid ASC")
    suspend fun getAllUidAllocations(): List<CatalogUidAllocation>

    @Query("DELETE FROM photos")
    suspend fun clearPhotos()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM catalog_uid_allocations")
    suspend fun clearUidAllocations()

    @Transaction
    suspend fun insertItemWithPhotos(item: ArtifactItem, photos: List<ArtifactPhoto>): ArtifactItem {
        val uid = nextCatalogUid()
        val itemWithUid = item.copy(catalogUid = uid)
        insertUidAllocation(
            CatalogUidAllocation(
                uid = uid,
                itemId = item.id,
                allocatedAt = item.createdAt,
            ),
        )
        insertItem(itemWithUid)
        insertPhotos(photos)
        return itemWithUid
    }

    @Transaction
    suspend fun replaceAllForRestore(
        items: List<ArtifactItem>,
        photos: List<ArtifactPhoto>,
        uidAllocations: List<CatalogUidAllocation>,
    ) {
        clearPhotos()
        clearItems()
        clearUidAllocations()
        insertUidAllocations(uidAllocations)
        insertItems(items)
        insertPhotos(photos)
    }
}
