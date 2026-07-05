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
        manufacturerId: String?,
        manufacturer: String,
        modelId: String?,
        itemType: String,
        productionYearStart: Int?,
        productionYearEnd: Int?,
        serialNumber: String,
        location: String,
        notes: String,
        photoPaths: List<String>,
    ) {
        val now = System.currentTimeMillis()
        val item = ArtifactItem(
            manufacturerId = manufacturerId,
            manufacturer = manufacturer.trim(),
            modelId = modelId,
            itemType = itemType.trim(),
            productionYearStart = productionYearStart,
            productionYearEnd = productionYearEnd,
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

    suspend fun updateItem(item: ArtifactItem) {
        dao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))
    }
}
