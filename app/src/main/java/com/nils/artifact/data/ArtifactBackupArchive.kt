package com.nils.artifact.data

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArtifactRestoreArchive(
    val settings: CollectionSettings,
    val items: List<ArtifactItem>,
    val photos: List<ArtifactRestorePhoto>,
    val uidAllocations: List<CatalogUidAllocation>,
    val temporaryDirectory: File,
)

data class ArtifactRestorePhoto(
    val photo: ArtifactPhoto,
    val temporaryFile: File,
    val extension: String,
)

object ArtifactBackupArchive {
    const val FILE_EXTENSION = "artifactbackup"
    private const val FORMAT_VERSION = 1
    private const val DATABASE_VERSION = 3

    fun write(
        outputStream: OutputStream,
        snapshot: ArtifactBackupSnapshot,
        settings: CollectionSettings,
    ) {
        validateBackupSnapshot(snapshot)
        val photoEntries = snapshot.photos.map { photo ->
            val source = File(photo.filePath)
            require(source.isFile) {
                "Missing photo file for backup: ${source.name}"
            }
            val archivePath = "photos/${photo.id}.${safeExtension(source)}"
            BackupPhotoEntry(photo = photo, source = source, archivePath = archivePath)
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            zip.putJson(
                "manifest.json",
                JSONObject()
                    .put("app", "Artifact")
                    .put("formatVersion", FORMAT_VERSION)
                    .put("databaseVersion", DATABASE_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .put("itemCount", snapshot.items.size)
                    .put("photoCount", snapshot.photos.size)
                    .put("uidAllocationCount", snapshot.uidAllocations.size),
            )
            zip.putJson("collection_settings.json", settings.toJson())
            zip.putJson("items.json", snapshot.items.toJsonArray { item -> item.toJson() })
            zip.putJson(
                "photos.json",
                photoEntries.toJsonArray { entry ->
                    entry.photo.toJson(entry.archivePath, entry.source.name)
                },
            )
            zip.putJson(
                "uid_allocations.json",
                snapshot.uidAllocations.toJsonArray { allocation -> allocation.toJson() },
            )
            photoEntries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.archivePath))
                entry.source.inputStream().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }

    fun read(
        context: Context,
        inputStream: InputStream,
    ): ArtifactRestoreArchive {
        val temporaryDirectory = File(
            context.cacheDir,
            "artifact-restore-${System.currentTimeMillis()}",
        ).apply { mkdirs() }

        try {
            val jsonEntries = mutableMapOf<String, String>()
            val photoFiles = mutableMapOf<String, File>()

            ZipInputStream(inputStream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name
                    requireValidEntryName(entryName)
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    when {
                        entryName in REQUIRED_JSON_ENTRIES -> {
                            jsonEntries[entryName] = zip.readBytes().toString(Charsets.UTF_8)
                        }

                        entryName.startsWith("photos/") -> {
                            require(entryName !in photoFiles) {
                                "Duplicate photo entry in backup: $entryName"
                            }
                            val temporaryFile = File(temporaryDirectory, File(entryName).name)
                            temporaryFile.outputStream().use { output ->
                                zip.copyTo(output)
                            }
                            photoFiles[entryName] = temporaryFile
                        }
                    }
                    zip.closeEntry()
                }
            }

            REQUIRED_JSON_ENTRIES.forEach { entryName ->
                require(jsonEntries.containsKey(entryName)) {
                    "Backup is missing $entryName."
                }
            }

            val manifest = JSONObject(jsonEntries.getValue("manifest.json"))
            require(manifest.optString("app") == "Artifact") {
                "Backup is not an Artifact backup."
            }
            require(manifest.optInt("formatVersion") == FORMAT_VERSION) {
                "Unsupported backup format version."
            }

            val settings = JSONObject(jsonEntries.getValue("collection_settings.json"))
                .toCollectionSettings()
            val items = JSONArray(jsonEntries.getValue("items.json"))
                .mapObjects { json -> json.toArtifactItem() }
            val uidAllocations = JSONArray(jsonEntries.getValue("uid_allocations.json"))
                .mapObjects { json -> json.toCatalogUidAllocation() }
            val photos = JSONArray(jsonEntries.getValue("photos.json"))
                .mapObjects { json ->
                    val archivePath = json.getString("archivePath")
                    val temporaryFile = photoFiles[archivePath]?.takeIf { file -> file.isFile }
                    require(temporaryFile != null) {
                        "Backup is missing photo file $archivePath."
                    }
                    ArtifactRestorePhoto(
                        photo = json.toArtifactPhoto(filePath = temporaryFile.absolutePath),
                        temporaryFile = temporaryFile,
                        extension = ".${safeExtension(File(archivePath))}",
                    )
                }

            require(manifest.optInt("itemCount") == items.size) {
                "Backup item count does not match its manifest."
            }
            require(manifest.optInt("photoCount") == photos.size) {
                "Backup photo count does not match its manifest."
            }
            require(manifest.optInt("uidAllocationCount") == uidAllocations.size) {
                "Backup UID count does not match its manifest."
            }
            validateRestoreData(
                items = items,
                photos = photos.map { photo -> photo.photo },
                uidAllocations = uidAllocations,
            )

            return ArtifactRestoreArchive(
                settings = settings,
                items = items,
                photos = photos,
                uidAllocations = uidAllocations,
                temporaryDirectory = temporaryDirectory,
            )
        } catch (error: Throwable) {
            temporaryDirectory.deleteRecursively()
            throw error
        }
    }

    fun restoredPhotoDirectory(context: Context): File {
        val baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        return File(
            File(baseDirectory, "artifacts").apply { mkdirs() },
            "restore-${System.currentTimeMillis()}",
        ).apply { mkdirs() }
    }

    private fun validateBackupSnapshot(snapshot: ArtifactBackupSnapshot) {
        validateRestoreData(
            items = snapshot.items,
            photos = snapshot.photos,
            uidAllocations = snapshot.uidAllocations,
        )
    }

    private fun validateRestoreData(
        items: List<ArtifactItem>,
        photos: List<ArtifactPhoto>,
        uidAllocations: List<CatalogUidAllocation>,
    ) {
        val itemIds = mutableSetOf<String>()
        val itemUids = mutableSetOf<Long>()
        items.forEach { item ->
            require(item.id.isNotBlank()) {
                "Backup contains an item with a blank id."
            }
            require(itemIds.add(item.id)) {
                "Backup contains duplicate item id ${item.id}."
            }
            require(item.catalogUid > 0) {
                "Backup contains an invalid UID."
            }
            require(itemUids.add(item.catalogUid)) {
                "Backup contains duplicate item UID #${item.catalogUid}."
            }
            require(
                item.productionYearStart == null ||
                    item.productionYearEnd == null ||
                    item.productionYearStart <= item.productionYearEnd,
            ) {
                "Backup contains invalid production years for UID #${item.catalogUid}."
            }
        }

        val allocatedUids = mutableSetOf<Long>()
        uidAllocations.forEach { allocation ->
            require(allocation.uid > 0) {
                "Backup contains an invalid allocated UID."
            }
            require(allocatedUids.add(allocation.uid)) {
                "Backup contains duplicate allocated UID #${allocation.uid}."
            }
        }
        itemUids.forEach { uid ->
            require(uid in allocatedUids) {
                "Backup is missing UID allocation for item #$uid."
            }
        }

        val photoIds = mutableSetOf<String>()
        photos.forEach { photo ->
            require(photo.id.isNotBlank()) {
                "Backup contains a photo with a blank id."
            }
            require(photoIds.add(photo.id)) {
                "Backup contains duplicate photo id ${photo.id}."
            }
            require(photo.itemId in itemIds) {
                "Backup contains a photo for an unknown item."
            }
        }
    }

    private fun ZipOutputStream.putJson(entryName: String, json: JSONObject) {
        putNextEntry(ZipEntry(entryName))
        write(json.toString(2).toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putJson(entryName: String, json: JSONArray) {
        putNextEntry(ZipEntry(entryName))
        write(json.toString(2).toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ArtifactItem.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("catalogUid", catalogUid)
            .putNullable("manufacturerId", manufacturerId)
            .put("manufacturer", manufacturer)
            .putNullable("modelId", modelId)
            .put("itemType", itemType)
            .putNullable("productionYearStart", productionYearStart)
            .putNullable("productionYearEnd", productionYearEnd)
            .put("serialNumber", serialNumber)
            .put("location", location)
            .put("notes", notes)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)

    private fun JSONObject.toArtifactItem(): ArtifactItem =
        ArtifactItem(
            id = getString("id"),
            catalogUid = getLong("catalogUid"),
            manufacturerId = optNullableString("manufacturerId"),
            manufacturer = getString("manufacturer"),
            modelId = optNullableString("modelId"),
            itemType = getString("itemType"),
            productionYearStart = optNullableInt("productionYearStart"),
            productionYearEnd = optNullableInt("productionYearEnd"),
            serialNumber = getString("serialNumber"),
            location = getString("location"),
            notes = getString("notes"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
        )

    private fun ArtifactPhoto.toJson(archivePath: String, originalFileName: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("itemId", itemId)
            .put("archivePath", archivePath)
            .put("originalFileName", originalFileName)
            .put("createdAt", createdAt)

    private fun JSONObject.toArtifactPhoto(filePath: String): ArtifactPhoto =
        ArtifactPhoto(
            id = getString("id"),
            itemId = getString("itemId"),
            filePath = filePath,
            createdAt = getLong("createdAt"),
        )

    private fun CatalogUidAllocation.toJson(): JSONObject =
        JSONObject()
            .put("uid", uid)
            .put("itemId", itemId)
            .put("allocatedAt", allocatedAt)

    private fun JSONObject.toCatalogUidAllocation(): CatalogUidAllocation =
        CatalogUidAllocation(
            uid = getLong("uid"),
            itemId = getString("itemId"),
            allocatedAt = getLong("allocatedAt"),
        )

    private fun CollectionSettings.toJson(): JSONObject =
        JSONObject()
            .put("collectionName", collectionName)
            .put("ownerName", ownerName)
            .put("ownerEmail", ownerEmail)
            .put("ownerPhone", ownerPhone)
            .put("ownerNotes", ownerNotes)

    private fun JSONObject.toCollectionSettings(): CollectionSettings =
        CollectionSettings(
            collectionName = optString("collectionName", ""),
            ownerName = optString("ownerName", ""),
            ownerEmail = optString("ownerEmail", ""),
            ownerPhone = optString("ownerPhone", ""),
            ownerNotes = optString("ownerNotes", ""),
        )

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putNullable(key: String, value: Int?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
        JSONArray().also { array ->
            forEach { value -> array.put(mapper(value)) }
        }

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        buildList {
            for (index in 0 until length()) {
                add(mapper(getJSONObject(index)))
            }
        }

    private fun safeExtension(file: File): String {
        val extension = file.extension.lowercase(Locale.US)
        return extension.takeIf { value ->
            value.length in 1..8 && value.all { char -> char.isLetterOrDigit() }
        } ?: "jpg"
    }

    private fun requireValidEntryName(entryName: String) {
        require(entryName.isNotBlank()) {
            "Backup contains a blank entry name."
        }
        require(!entryName.startsWith("/") && "\\" !in entryName && ".." !in entryName.split("/")) {
            "Backup contains an unsafe entry name."
        }
    }

    private data class BackupPhotoEntry(
        val photo: ArtifactPhoto,
        val source: File,
        val archivePath: String,
    )

    private val REQUIRED_JSON_ENTRIES = setOf(
        "manifest.json",
        "collection_settings.json",
        "items.json",
        "photos.json",
        "uid_allocations.json",
    )
}
