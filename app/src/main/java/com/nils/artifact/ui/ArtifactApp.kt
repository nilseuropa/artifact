package com.nils.artifact.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.nils.artifact.label.ArtifactLabelRenderer
import com.nils.artifact.data.ArtifactItem
import com.nils.artifact.data.ArtifactWithPhotos
import com.nils.artifact.data.CatalogData
import com.nils.artifact.data.CatalogManufacturer
import com.nils.artifact.data.CatalogModel
import com.nils.artifact.data.CollectionSettings
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class Screen {
    Home,
    Browse,
    Search,
    Manage,
    Add,
    Detail,
    Settings,
}

private data class ModelSuggestion(
    val manufacturer: CatalogManufacturer,
    val model: CatalogModel,
)

private data class TextSuggestion(
    val primary: String,
    val secondary: String?,
    val onClick: () -> Unit,
)

private data class EditTextTarget(
    val title: String,
    val initialValue: String,
    val singleLine: Boolean,
    val onSave: (String) -> Unit,
)

private data class EditYearsTarget(
    val initialStart: Int?,
    val initialEnd: Int?,
    val onSave: (Int?, Int?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactApp(viewModel: ArtifactViewModel) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var returnScreenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var labelPreviewItem by remember { mutableStateOf<ArtifactItem?>(null) }
    val screen = remember(screenName) { Screen.valueOf(screenName) }
    val items by viewModel.items.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val collectionSettings by viewModel.collectionSettings.collectAsState()

    fun openDetail(returnScreen: Screen, artifact: ArtifactWithPhotos) {
        selectedItemId = artifact.item.id
        returnScreenName = returnScreen.name
        screenName = Screen.Detail.name
    }

    fun navigateBack() {
        screenName = if (screen == Screen.Detail) {
            returnScreenName
        } else {
            Screen.Home.name
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        navigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(screen.title()) },
                navigationIcon = {
                    if (screen != Screen.Home) {
                        IconButton(onClick = ::navigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    itemCount = items.size,
                    collectionSettings = collectionSettings,
                    onBrowse = { screenName = Screen.Browse.name },
                    onSearch = { screenName = Screen.Search.name },
                    onManage = { screenName = Screen.Manage.name },
                    onAdd = { screenName = Screen.Add.name },
                    onSettings = { screenName = Screen.Settings.name },
                )

                Screen.Browse -> BrowseScreen(
                    items = items,
                    onOpen = { artifact -> openDetail(Screen.Browse, artifact) },
                )

                Screen.Search -> SearchScreen(
                    viewModel = viewModel,
                    onOpen = { artifact -> openDetail(Screen.Search, artifact) },
                )

                Screen.Manage -> ManageScreen(
                    items = items,
                    onDelete = viewModel::deleteItem,
                    onOpen = { artifact -> openDetail(Screen.Manage, artifact) },
                )

                Screen.Add -> AddItemScreen(
                    viewModel = viewModel,
                    onDone = { screenName = Screen.Browse.name },
                    onPrintLabel = { item ->
                        labelPreviewItem = item
                        selectedItemId = item.id
                        returnScreenName = Screen.Browse.name
                        screenName = Screen.Detail.name
                    },
                )

                Screen.Detail -> DetailScreen(
                    artifact = items.firstOrNull { artifact -> artifact.item.id == selectedItemId },
                    catalog = catalog,
                    onUpdate = viewModel::updateItem,
                    onPrintLabel = { item -> labelPreviewItem = item },
                )

                Screen.Settings -> CollectionSettingsScreen(
                    settings = collectionSettings,
                    onSave = viewModel::saveCollectionSettings,
                )
            }

            labelPreviewItem?.let { item ->
                LabelPreviewDialog(
                    item = item,
                    settings = collectionSettings,
                    onDismiss = { labelPreviewItem = null },
                )
            }
        }
    }
}

private fun Screen.title(): String =
    when (this) {
        Screen.Home -> "Artifact"
        Screen.Browse -> "Browse"
        Screen.Search -> "Search"
        Screen.Manage -> "Manage Collection"
        Screen.Add -> "Add New Item"
        Screen.Detail -> "Item Details"
        Screen.Settings -> "Collection Settings"
    }

@Composable
private fun HomeScreen(
    itemCount: Int,
    collectionSettings: CollectionSettings,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onManage: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = collectionSettings.collectionName.ifBlank { "Retro computing catalog" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        collectionSettings.ownerName.takeIf { it.isNotBlank() }?.let { ownerName ->
            Text(
                text = "Owner: $ownerName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$itemCount item${if (itemCount == 1) "" else "s"} stored locally",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MenuAction(
            label = "Browse",
            supportingText = "Review every cataloged item",
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            onClick = onBrowse,
        )
        MenuAction(
            label = "Search",
            supportingText = "Find by manufacturer, type, serial, or location",
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            onClick = onSearch,
        )
        MenuAction(
            label = "Manage Collection",
            supportingText = "Audit locations and remove entries",
            icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
            onClick = onManage,
        )
        MenuAction(
            label = "Add New Item",
            supportingText = "Capture photos and catalog details",
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = onAdd,
        )
        MenuAction(
            label = "Collection Settings",
            supportingText = "Collection name and owner details",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }
}

@Composable
private fun MenuAction(
    label: String,
    supportingText: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowseScreen(
    items: List<ArtifactWithPhotos>,
    onOpen: (ArtifactWithPhotos) -> Unit,
) {
    CollectionList(
        items = items,
        emptyTitle = "No items yet",
        emptyBody = "Add a new item to start the local catalog.",
        onItemClick = onOpen,
    )
}

@Composable
private fun SearchScreen(
    viewModel: ArtifactViewModel,
    onOpen: (ArtifactWithPhotos) -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::updateSearchQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search collection") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CollectionList(
            items = results,
            emptyTitle = if (query.isBlank()) "Type to search" else "No matches",
            emptyBody = if (query.isBlank()) {
                "Search manufacturer, type, serial number, or location."
            } else {
                "Try a different catalog field."
            },
            contentPadding = PaddingValues(0.dp),
            onItemClick = onOpen,
        )
    }
}

@Composable
private fun ManageScreen(
    items: List<ArtifactWithPhotos>,
    onDelete: (ArtifactWithPhotos) -> Unit,
    onOpen: (ArtifactWithPhotos) -> Unit,
) {
    val locationCount = items
        .map { it.item.location.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBlock(
                label = "Items",
                value = items.size.toString(),
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                label = "Locations",
                value = locationCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        CollectionList(
            items = items,
            emptyTitle = "Nothing to manage",
            emptyBody = "Catalog entries will appear here.",
            contentPadding = PaddingValues(0.dp),
            onItemClick = onOpen,
            trailing = { artifact ->
                IconButton(onClick = { onDelete(artifact) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            },
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollectionSettingsScreen(
    settings: CollectionSettings,
    onSave: (CollectionSettings) -> Unit,
) {
    var collectionName by rememberSaveable(settings.collectionName) {
        mutableStateOf(settings.collectionName)
    }
    var ownerName by rememberSaveable(settings.ownerName) {
        mutableStateOf(settings.ownerName)
    }
    var ownerEmail by rememberSaveable(settings.ownerEmail) {
        mutableStateOf(settings.ownerEmail)
    }
    var ownerPhone by rememberSaveable(settings.ownerPhone) {
        mutableStateOf(settings.ownerPhone)
    }
    var ownerNotes by rememberSaveable(settings.ownerNotes) {
        mutableStateOf(settings.ownerNotes)
    }
    var savedMessageVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = collectionName,
            onValueChange = {
                collectionName = it
                savedMessageVisible = false
            },
            label = { Text("Collection name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ownerName,
            onValueChange = {
                ownerName = it
                savedMessageVisible = false
            },
            label = { Text("Owner name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ownerEmail,
            onValueChange = {
                ownerEmail = it
                savedMessageVisible = false
            },
            label = { Text("Owner email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ownerPhone,
            onValueChange = {
                ownerPhone = it
                savedMessageVisible = false
            },
            label = { Text("Owner phone") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ownerNotes,
            onValueChange = {
                ownerNotes = it
                savedMessageVisible = false
            },
            label = { Text("Owner notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSave(
                    CollectionSettings(
                        collectionName = collectionName,
                        ownerName = ownerName,
                        ownerEmail = ownerEmail,
                        ownerPhone = ownerPhone,
                        ownerNotes = ownerNotes,
                    ),
                )
                savedMessageVisible = true
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Settings")
        }
        if (savedMessageVisible) {
            Text(
                text = "Settings saved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun AddItemScreen(
    viewModel: ArtifactViewModel,
    onDone: () -> Unit,
    onPrintLabel: (ArtifactItem) -> Unit,
) {
    val catalog by viewModel.catalog.collectAsState()
    var manufacturer by rememberSaveable { mutableStateOf("") }
    var itemType by rememberSaveable { mutableStateOf("") }
    var serialNumber by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val photoPaths = remember { mutableStateListOf<String>() }
    val canSave = photoPaths.isNotEmpty() &&
        manufacturer.isNotBlank() &&
        itemType.isNotBlank() &&
        location.isNotBlank()
    val selectedManufacturer = catalog.findManufacturer(manufacturer)
    val selectedModel = catalog.findModel(selectedManufacturer, itemType)
    val manufacturerSuggestions = catalog.manufacturerSuggestions(manufacturer)
        .map { suggestion ->
            TextSuggestion(
                primary = suggestion.name,
                secondary = "${suggestion.models.size} model${if (suggestion.models.size == 1) "" else "s"}",
                onClick = { manufacturer = suggestion.name },
            )
        }
    val modelSuggestions = catalog.modelSuggestions(
        manufacturer = selectedManufacturer,
        query = itemType,
    ).map { suggestion ->
        TextSuggestion(
            primary = suggestion.model.name,
            secondary = listOfNotNull(
                suggestion.manufacturer.name.takeUnless { selectedManufacturer != null },
                suggestion.model.productionYearsLabel(),
            ).joinToString(" / ").ifBlank { null },
            onClick = {
                manufacturer = suggestion.manufacturer.name
                itemType = suggestion.model.name
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CameraPermissionCapture(
            onPhotoCaptured = { path ->
                photoPaths.add(path)
                errorMessage = null
            },
            onError = { errorMessage = it },
        )

        if (photoPaths.isNotEmpty()) {
            PhotoStrip(
                photoPaths = photoPaths,
                onRemove = { path ->
                    photoPaths.remove(path)
                    runCatching { File(path).delete() }
                },
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SuggestionTextField(
            value = manufacturer,
            onValueChange = { value -> manufacturer = value },
            label = "Manufacturer",
            suggestions = manufacturerSuggestions,
            modifier = Modifier.fillMaxWidth(),
        )
        SuggestionTextField(
            value = itemType,
            onValueChange = { value -> itemType = value },
            label = "Type",
            suggestions = modelSuggestions,
            modifier = Modifier.fillMaxWidth(),
        )
        selectedModel?.productionYearsLabel()?.let { years ->
            Text(
                text = "Catalog production years: $years",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = serialNumber,
            onValueChange = { serialNumber = it },
            label = { Text("Serial number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.addItem(
                    manufacturerId = selectedManufacturer?.id,
                    manufacturer = manufacturer,
                    modelId = selectedModel?.id,
                    itemType = itemType,
                    productionYearStart = selectedModel?.productionYearStart,
                    productionYearEnd = selectedModel?.productionYearEnd,
                    serialNumber = serialNumber,
                    location = location,
                    notes = notes,
                    photoPaths = photoPaths.toList(),
                    onComplete = { onDone() },
                )
            },
            enabled = canSave,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Item")
        }
        OutlinedButton(
            onClick = {
                viewModel.addItem(
                    manufacturerId = selectedManufacturer?.id,
                    manufacturer = manufacturer,
                    modelId = selectedModel?.id,
                    itemType = itemType,
                    productionYearStart = selectedModel?.productionYearStart,
                    productionYearEnd = selectedModel?.productionYearEnd,
                    serialNumber = serialNumber,
                    location = location,
                    notes = notes,
                    photoPaths = photoPaths.toList(),
                    onComplete = onPrintLabel,
                )
            },
            enabled = canSave,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Print Label")
        }
    }
}

@Composable
private fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<TextSuggestion>,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> focused = focusState.isFocused },
        )
        if (focused && suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    suggestions.take(8).forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suggestion.onClick()
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = suggestion.primary,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            suggestion.secondary?.let { secondary ->
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    artifact: ArtifactWithPhotos?,
    catalog: CatalogData,
    onUpdate: (ArtifactItem) -> Unit,
    onPrintLabel: (ArtifactItem) -> Unit,
) {
    if (artifact == null) {
        EmptyState(
            title = "Item not found",
            body = "The selected catalog item is no longer available.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val item = artifact.item
    var selectedPhotoPath by rememberSaveable(item.id) {
        mutableStateOf(artifact.photos.firstOrNull()?.filePath)
    }
    var fullscreenPhotoPath by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var editTextTarget by remember { mutableStateOf<EditTextTarget?>(null) }
    var editYearsTarget by remember { mutableStateOf<EditYearsTarget?>(null) }
    val photoPaths = artifact.photos.map { photo -> photo.filePath }

    LaunchedEffect(item.id, photoPaths) {
        if (selectedPhotoPath !in photoPaths) {
            selectedPhotoPath = photoPaths.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "#${item.catalogUid} ${item.manufacturer.ifBlank { "Unknown manufacturer" }}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Button(
            onClick = { onPrintLabel(item) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Print Label")
        }

        PhotoGallery(
            photoPaths = photoPaths,
            selectedPhotoPath = selectedPhotoPath,
            onSelectPhoto = { path -> selectedPhotoPath = path },
            onOpenFullScreen = { path -> fullscreenPhotoPath = path },
        )

        DetailReadOnlyRow(label = "UID", value = "#${item.catalogUid}")
        DetailEditableRow(
            label = "Manufacturer",
            value = item.manufacturer.ifBlank { "Not recorded" },
            onEdit = {
                editTextTarget = EditTextTarget(
                    title = "Manufacturer",
                    initialValue = item.manufacturer,
                    singleLine = true,
                    onSave = { value ->
                        val cleanValue = value.trim()
                        val selectedManufacturer = catalog.findManufacturer(cleanValue)
                        val selectedModel = catalog.findModel(selectedManufacturer, item.itemType)
                        onUpdate(
                            item.copy(
                                manufacturerId = selectedManufacturer?.id,
                                manufacturer = cleanValue,
                                modelId = selectedModel?.id,
                                productionYearStart = selectedModel?.productionYearStart,
                                productionYearEnd = selectedModel?.productionYearEnd,
                            ),
                        )
                    },
                )
            },
        )
        DetailEditableRow(
            label = "Type",
            value = item.itemType.ifBlank { "Not recorded" },
            onEdit = {
                editTextTarget = EditTextTarget(
                    title = "Type",
                    initialValue = item.itemType,
                    singleLine = true,
                    onSave = { value ->
                        val cleanValue = value.trim()
                        val selectedManufacturer = catalog.findManufacturer(item.manufacturer)
                        val selectedModel = catalog.findModel(selectedManufacturer, cleanValue)
                        onUpdate(
                            item.copy(
                                modelId = selectedModel?.id,
                                itemType = cleanValue,
                                productionYearStart = selectedModel?.productionYearStart,
                                productionYearEnd = selectedModel?.productionYearEnd,
                            ),
                        )
                    },
                )
            },
        )
        DetailEditableRow(
            label = "Production years",
            value = item.productionYearsLabel() ?: "Not recorded",
            onEdit = {
                editYearsTarget = EditYearsTarget(
                    initialStart = item.productionYearStart,
                    initialEnd = item.productionYearEnd,
                    onSave = { start, end ->
                        onUpdate(
                            item.copy(
                                productionYearStart = start,
                                productionYearEnd = end,
                            ),
                        )
                    },
                )
            },
        )
        DetailEditableRow(
            label = "Serial number",
            value = item.serialNumber.ifBlank { "Not recorded" },
            onEdit = {
                editTextTarget = EditTextTarget(
                    title = "Serial number",
                    initialValue = item.serialNumber,
                    singleLine = true,
                    onSave = { value -> onUpdate(item.copy(serialNumber = value.trim())) },
                )
            },
        )
        DetailEditableRow(
            label = "Location",
            value = item.location.ifBlank { "Not recorded" },
            onEdit = {
                editTextTarget = EditTextTarget(
                    title = "Location",
                    initialValue = item.location,
                    singleLine = true,
                    onSave = { value -> onUpdate(item.copy(location = value.trim())) },
                )
            },
        )
        DetailEditableRow(
            label = "Notes",
            value = item.notes.ifBlank { "Not recorded" },
            onEdit = {
                editTextTarget = EditTextTarget(
                    title = "Notes",
                    initialValue = item.notes,
                    singleLine = false,
                    onSave = { value -> onUpdate(item.copy(notes = value.trim())) },
                )
            },
        )
        DetailReadOnlyRow(label = "Added", value = formatTimestamp(item.createdAt))
        DetailReadOnlyRow(label = "Updated", value = formatTimestamp(item.updatedAt))
    }

    editTextTarget?.let { target ->
        EditTextDialog(
            target = target,
            onDismiss = { editTextTarget = null },
        )
    }

    editYearsTarget?.let { target ->
        EditYearsDialog(
            target = target,
            onDismiss = { editYearsTarget = null },
        )
    }

    fullscreenPhotoPath?.let { path ->
        FullScreenPhotoViewer(
            photoPath = path,
            onDismiss = { fullscreenPhotoPath = null },
        )
    }
}

@Composable
private fun LabelPreviewDialog(
    item: ArtifactItem,
    settings: CollectionSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val labelBitmap = remember(item, settings) {
        ArtifactLabelRenderer.render(item = item, settings = settings)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label #${item.catalogUid}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        bitmap = labelBitmap.asImageBitmap(),
                        contentDescription = "Generated label preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                ArtifactLabelRenderer.WIDTH.toFloat() /
                                    ArtifactLabelRenderer.HEIGHT.toFloat(),
                            )
                            .padding(8.dp),
                    )
                }
                Text(
                    text = "${ArtifactLabelRenderer.WIDTH}x${ArtifactLabelRenderer.HEIGHT} black/white label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ArtifactLabelRenderer.print(
                        context = context,
                        bitmap = labelBitmap,
                        uid = item.catalogUid,
                    )
                    onDismiss()
                },
            ) {
                Text("Print")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun PhotoGallery(
    photoPaths: List<String>,
    selectedPhotoPath: String?,
    onSelectPhoto: (String) -> Unit,
    onOpenFullScreen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Photos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (selectedPhotoPath == null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "No photos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clickable { onOpenFullScreen(selectedPhotoPath) },
        ) {
            AsyncImage(
                model = File(selectedPhotoPath),
                contentDescription = "Selected item photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(photoPaths, key = { it }) { path ->
                val selected = path == selectedPhotoPath
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                    ),
                    modifier = Modifier
                        .size(76.dp)
                        .clickable { onSelectPhoto(path) },
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Item photo thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenPhotoViewer(
    photoPath: String,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f)),
    ) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = "Full screen item photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close photo")
        }
    }
}

@Composable
private fun DetailReadOnlyRow(
    label: String,
    value: String,
) {
    DetailRowSurface {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DetailLabel(label = label)
            DetailValue(value = value)
        }
    }
}

@Composable
private fun DetailEditableRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
) {
    DetailRowSurface {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DetailLabel(label = label)
            DetailValue(value = value)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit $label")
        }
    }
}

@Composable
private fun DetailRowSurface(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EditTextDialog(
    target: EditTextTarget,
    onDismiss: () -> Unit,
) {
    var value by remember(target) { mutableStateOf(target.initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${target.title}") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(target.title) },
                singleLine = target.singleLine,
                minLines = if (target.singleLine) 1 else 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    target.onSave(value)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditYearsDialog(
    target: EditYearsTarget,
    onDismiss: () -> Unit,
) {
    var start by remember(target) { mutableStateOf(target.initialStart?.toString() ?: "") }
    var end by remember(target) { mutableStateOf(target.initialEnd?.toString() ?: "") }
    val parsedStart = start.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedEnd = end.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val hasInvalidNumber = (start.isNotBlank() && parsedStart == null) ||
        (end.isNotBlank() && parsedEnd == null)
    val hasInvalidOrder = parsedStart != null && parsedEnd != null && parsedStart > parsedEnd
    val canSave = !hasInvalidNumber && !hasInvalidOrder

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Production Years") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start year") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("End year") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasInvalidNumber) {
                    Text(
                        text = "Years must be numbers.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (hasInvalidOrder) {
                    Text(
                        text = "Start year must not be after end year.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    target.onSave(parsedStart, parsedEnd)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CameraPermissionCapture(
    onPhotoCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        CameraCaptureBox(
            onPhotoCaptured = onPhotoCaptured,
            onError = onError,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Camera permission is needed before adding photos.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow Camera")
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureBox(
    onPhotoCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CameraPreview(
                imageCapture = imageCapture,
                onError = onError,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            )
            Button(
                onClick = {
                    captureArtifactPhoto(
                        context = context,
                        imageCapture = imageCapture,
                        onPhotoCaptured = onPhotoCaptured,
                        onError = onError,
                    )
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take Photo")
            }
        }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner, imageCapture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderState.value = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }.onFailure { failure ->
                onError(failure.message ?: "Unable to start camera.")
            }
        }

        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            cameraProviderState.value?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun captureArtifactPhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?: context.filesDir
    val photoDirectory = File(baseDirectory, "artifacts").apply { mkdirs() }
    val photoFile = File(photoDirectory, "artifact-${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPhotoCaptured(photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "Photo capture failed.")
            }
        },
    )
}

@Composable
private fun PhotoStrip(
    photoPaths: List<String>,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${photoPaths.size} photo${if (photoPaths.size == 1) "" else "s"} captured",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(photoPaths, key = { it }) { path ->
                Box {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Captured item photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    IconButton(
                        onClick = { onRemove(path) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                            ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionList(
    items: List<ArtifactWithPhotos>,
    emptyTitle: String,
    emptyBody: String,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onItemClick: (ArtifactWithPhotos) -> Unit = {},
    trailing: @Composable ((ArtifactWithPhotos) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        EmptyState(
            title = emptyTitle,
            body = emptyBody,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.item.id }) { artifact ->
            ArtifactListItem(
                artifact = artifact,
                onClick = { onItemClick(artifact) },
                trailing = trailing,
            )
        }
    }
}

@Composable
private fun ArtifactListItem(
    artifact: ArtifactWithPhotos,
    onClick: () -> Unit,
    trailing: @Composable ((ArtifactWithPhotos) -> Unit)?,
) {
    val item = artifact.item
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoThumbnail(artifact = artifact)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "#${item.catalogUid} ${item.manufacturer.ifBlank { "Unknown manufacturer" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.itemType.ifBlank { "Unknown type" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                InfoLine(label = "Serial", value = item.serialNumber.ifBlank { "Not recorded" })
                InfoLine(label = "Location", value = item.location.ifBlank { "Not recorded" })
                item.productionYearsLabel()?.let { years ->
                    InfoLine(label = "Years", value = years)
                }
                InfoLine(
                    label = "Added",
                    value = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                    ).format(Date(item.createdAt)),
                )
                if (artifact.photos.size > 1) {
                    Text(
                        text = "${artifact.photos.size} photos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            trailing?.invoke(artifact)
        }
    }
}

@Composable
private fun PhotoThumbnail(artifact: ArtifactWithPhotos) {
    val firstPhoto = artifact.photos.firstOrNull()
    if (firstPhoto != null) {
        AsyncImage(
            model = File(firstPhoto.filePath),
            contentDescription = "Item photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun CatalogData.findManufacturer(query: String): CatalogManufacturer? =
    manufacturers.firstOrNull { manufacturer ->
        manufacturer.name.equals(query.trim(), ignoreCase = true)
    }

private fun CatalogData.findModel(
    manufacturer: CatalogManufacturer?,
    query: String,
): CatalogModel? {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return null
    val candidates = manufacturer?.models ?: manufacturers.asSequence().flatMap { it.models.asSequence() }.toList()
    return candidates.firstOrNull { model ->
        model.name.equals(cleanQuery, ignoreCase = true)
    }
}

private fun CatalogData.manufacturerSuggestions(query: String): List<CatalogManufacturer> {
    val cleanQuery = query.trim()
    return manufacturers
        .asSequence()
        .filter { manufacturer ->
            cleanQuery.isBlank() || manufacturer.name.contains(cleanQuery, ignoreCase = true)
        }
        .take(8)
        .toList()
}

private fun CatalogData.modelSuggestions(
    manufacturer: CatalogManufacturer?,
    query: String,
): List<ModelSuggestion> {
    val cleanQuery = query.trim()
    val candidates = if (manufacturer != null) {
        sequence {
            manufacturer.models.forEach { model ->
                yield(ModelSuggestion(manufacturer = manufacturer, model = model))
            }
        }
    } else {
        manufacturers.asSequence().flatMap { currentManufacturer ->
            currentManufacturer.models.asSequence().map { model ->
                ModelSuggestion(manufacturer = currentManufacturer, model = model)
            }
        }
    }

    return candidates
        .filter { suggestion ->
            cleanQuery.isBlank() || suggestion.model.name.contains(cleanQuery, ignoreCase = true)
        }
        .take(8)
        .toList()
}

private fun CatalogModel.productionYearsLabel(): String? =
    productionYearsLabel(productionYearStart, productionYearEnd)

private fun com.nils.artifact.data.ArtifactItem.productionYearsLabel(): String? =
    productionYearsLabel(productionYearStart, productionYearEnd)

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
    ).format(Date(timestamp))

private fun productionYearsLabel(start: Int?, end: Int?): String? =
    when {
        start != null && end != null && start == end -> start.toString()
        start != null && end != null -> "$start-$end"
        start != null -> "$start-"
        end != null -> "-$end"
        else -> null
    }

@Composable
private fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
