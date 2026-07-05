package com.nils.artifact.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nils.artifact.data.ArtifactDatabase
import com.nils.artifact.data.ArtifactRepository
import com.nils.artifact.data.ArtifactWithPhotos
import com.nils.artifact.data.CatalogData
import com.nils.artifact.data.CatalogSeed
import java.io.File
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

    private val _catalog = MutableStateFlow(CatalogData.Empty)
    val catalog: StateFlow<CatalogData> = _catalog.asStateFlow()

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
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.addItem(
                manufacturerId = manufacturerId,
                manufacturer = manufacturer,
                modelId = modelId,
                itemType = itemType,
                productionYearStart = productionYearStart,
                productionYearEnd = productionYearEnd,
                serialNumber = serialNumber,
                location = location,
                notes = notes,
                photoPaths = photoPaths,
            )
            onComplete()
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
}
