package com.nils.artifact.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nils.artifact.data.ArtifactBackupArchive
import com.nils.artifact.data.ArtifactDatabase
import com.nils.artifact.data.ArtifactItem
import com.nils.artifact.data.ArtifactRepository
import com.nils.artifact.data.ArtifactSyncClient
import com.nils.artifact.data.ArtifactSyncSettings
import com.nils.artifact.data.ArtifactSyncSettingsStore
import com.nils.artifact.data.ArtifactWithPhotos
import com.nils.artifact.data.CatalogData
import com.nils.artifact.data.CatalogSeed
import com.nils.artifact.data.CollectionSettings
import com.nils.artifact.data.CollectionSettingsStore
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArtifactRepository(
        ArtifactDatabase.get(application).artifactDao(),
    )
    private val settingsStore = CollectionSettingsStore(application)
    private val syncSettingsStore = ArtifactSyncSettingsStore(application)

    private val _catalog = MutableStateFlow(CatalogData.Empty)
    val catalog: StateFlow<CatalogData> = _catalog.asStateFlow()

    private val _collectionSettings = MutableStateFlow(settingsStore.load())
    val collectionSettings: StateFlow<CollectionSettings> = _collectionSettings.asStateFlow()

    private val _syncSettings = MutableStateFlow(syncSettingsStore.load())
    val syncSettings: StateFlow<ArtifactSyncSettings> = _syncSettings.asStateFlow()

    val items: StateFlow<List<ArtifactWithPhotos>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<ArtifactWithPhotos>> = _searchQuery
        .flatMapLatest(repository::searchItems)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _catalog.value = CatalogSeed.load(application)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addItem(
        category: String,
        manufacturerId: String?,
        manufacturer: String,
        modelId: String?,
        itemType: String,
        productionYearStart: Int?,
        productionYearEnd: Int?,
        estimatedValue: Double?,
        serialNumber: String,
        location: String,
        notes: String,
        photoPaths: List<String>,
        onComplete: (ArtifactItem) -> Unit,
    ) {
        viewModelScope.launch {
            val item = repository.addItem(
                category = category,
                manufacturerId = manufacturerId,
                manufacturer = manufacturer,
                modelId = modelId,
                itemType = itemType,
                productionYearStart = productionYearStart,
                productionYearEnd = productionYearEnd,
                estimatedValue = estimatedValue,
                serialNumber = serialNumber,
                location = location,
                notes = notes,
                photoPaths = photoPaths,
            )
            onComplete(item)
        }
    }

    fun deleteItem(item: ArtifactWithPhotos) {
        viewModelScope.launch {
            repository.deleteItem(item.item)
            withContext(Dispatchers.IO) {
                item.photos.forEach { photo ->
                    runCatching { File(photo.filePath).delete() }
                }
            }
        }
    }

    fun updateItem(item: ArtifactItem) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    fun saveCollectionSettings(settings: CollectionSettings) {
        _collectionSettings.value = settings
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.save(settings)
        }
    }

    fun saveSyncSettings(settings: ArtifactSyncSettings) {
        val cleanedSettings = settings.copy(
            serverUrl = settings.serverUrl.trim(),
            accessToken = settings.accessToken.trim(),
        )
        _syncSettings.value = cleanedSettings
        viewModelScope.launch(Dispatchers.IO) {
            syncSettingsStore.save(cleanedSettings)
        }
    }

    fun exportBackup(uri: Uri, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = repository.backupSnapshot()
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        ArtifactBackupArchive.write(
                            outputStream = output,
                            snapshot = snapshot,
                            settings = _collectionSettings.value,
                        )
                    } ?: error("Could not open backup destination.")
                    "Exported ${snapshot.items.size} items and ${snapshot.photos.size} photos."
                }
            }
            onComplete(result)
        }
    }

    fun restoreBackup(uri: Uri, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val resolver = context.contentResolver
                    resolver.openInputStream(uri)?.use { input ->
                        restoreBackupFromStream(input)
                    } ?: error("Could not open backup file.")
                }
            }
            onComplete(result)
        }
    }

    fun pushBackupToServer(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val snapshot = repository.backupSnapshot()
                    val tempFile = File.createTempFile(
                        "artifact-sync-upload-",
                        ".${ArtifactBackupArchive.FILE_EXTENSION}",
                        context.cacheDir,
                    )
                    try {
                        tempFile.outputStream().use { output ->
                            ArtifactBackupArchive.write(
                                outputStream = output,
                                snapshot = snapshot,
                                settings = _collectionSettings.value,
                            )
                        }
                        val serverMessage = ArtifactSyncClient.uploadBackup(
                            settings = _syncSettings.value,
                            backupFile = tempFile,
                        )
                        "Uploaded ${snapshot.items.size} items and ${snapshot.photos.size} photos. $serverMessage"
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                }
            }
            onComplete(result)
        }
    }

    fun pullBackupFromServer(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val tempFile = File.createTempFile(
                        "artifact-sync-download-",
                        ".${ArtifactBackupArchive.FILE_EXTENSION}",
                        context.cacheDir,
                    )
                    try {
                        val serverMessage = ArtifactSyncClient.downloadBackup(
                            settings = _syncSettings.value,
                            targetFile = tempFile,
                        )
                        val restoreMessage = tempFile.inputStream().use { input ->
                            restoreBackupFromStream(input)
                        }
                        "$serverMessage $restoreMessage"
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                }
            }
            onComplete(result)
        }
    }

    fun clearCollection(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val clearResult = repository.clearCollection()
                    clearResult.photoPaths.forEach { path ->
                        runCatching { File(path).delete() }
                    }
                    "Cleared ${clearResult.itemCount} items. Next item UID will be #1."
                }
            }
            onComplete(result)
        }
    }

    private suspend fun restoreBackupFromStream(input: InputStream): String {
        val context = getApplication<Application>()
        val archive = ArtifactBackupArchive.read(context, input)
        val restoredPhotoDirectory = ArtifactBackupArchive.restoredPhotoDirectory(context)
        val restoredPhotoFiles = mutableListOf<File>()

        try {
            val restoredPhotos = archive.photos.map { restorePhoto ->
                val targetFile = File(
                    restoredPhotoDirectory,
                    "${restorePhoto.photo.id}${restorePhoto.extension}",
                )
                restorePhoto.temporaryFile.copyTo(targetFile, overwrite = true)
                restoredPhotoFiles += targetFile
                restorePhoto.photo.copy(filePath = targetFile.absolutePath)
            }

            val previousPhotoPaths = repository.replaceAllFromBackup(
                items = archive.items,
                photos = restoredPhotos,
                uidAllocations = archive.uidAllocations,
            )
            settingsStore.save(archive.settings)
            _collectionSettings.value = archive.settings

            previousPhotoPaths.forEach { path ->
                runCatching { File(path).delete() }
            }
            archive.temporaryDirectory.deleteRecursively()

            return "Restored ${archive.items.size} items and ${restoredPhotos.size} photos."
        } catch (error: Throwable) {
            restoredPhotoFiles.forEach { file -> runCatching { file.delete() } }
            runCatching { restoredPhotoDirectory.deleteRecursively() }
            archive.temporaryDirectory.deleteRecursively()
            throw error
        }
    }
}
