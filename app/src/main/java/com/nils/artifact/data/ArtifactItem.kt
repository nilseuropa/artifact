package com.nils.artifact.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity(
    tableName = "items",
    indices = [Index(value = ["catalogUid"], unique = true)],
)
data class ArtifactItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val catalogUid: Long = 0,
    val manufacturerId: String?,
    val manufacturer: String,
    val modelId: String?,
    val itemType: String,
    val productionYearStart: Int?,
    val productionYearEnd: Int?,
    val serialNumber: String,
    val location: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "catalog_uid_allocations")
data class CatalogUidAllocation(
    @PrimaryKey val uid: Long,
    val itemId: String,
    val allocatedAt: Long,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ArtifactItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ArtifactPhoto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val filePath: String,
    val createdAt: Long,
)

data class ArtifactWithPhotos(
    @Embedded val item: ArtifactItem,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val photos: List<ArtifactPhoto>,
)
