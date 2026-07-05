package com.nils.artifact.data

import kotlinx.coroutines.flow.Flow

class ArtifactRepository(private val dao: ArtifactDao) {
    fun observeItems(): Flow<List<ArtifactWithPhotos>> = dao.observeItems()

    fun searchItems(query: String): Flow<List<ArtifactWithPhotos>> {
        val cleanQuery = query.trim()
        return if (cleanQuery.isBlank()) {
            dao.observeItems()
        } else {
            dao.searchItems(cleanQuery)
        }
    }

    suspend fun addItem(
        manufacturer: String,
        itemType: String,
        serialNumber: String,
        location: String,
        notes: String,
        photoPaths: List<String>,
    ) {
        val now = System.currentTimeMillis()
        val item = ArtifactItem(
            manufacturer = manufacturer.trim(),
            itemType = itemType.trim(),
            serialNumber = serialNumber.trim(),
            location = location.trim(),
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now,
        )
        val photos = photoPaths.map { path ->
            ArtifactPhoto(
                itemId = item.id,
                filePath = path,
                createdAt = now,
            )
        }
        dao.insertItemWithPhotos(item, photos)
    }

    suspend fun deleteItem(item: ArtifactItem) {
        dao.deleteItem(item)
    }
}
