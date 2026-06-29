package mirujam.nekomemo.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.component.EditBankDialog
import mirujam.nekomemo.ui.component.ExportLauncher
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.component.displayName
import mirujam.nekomemo.ui.theme.AppShapes
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

enum class SortMode(@StringRes val labelResId: Int, val icon: ImageVector) {
    DATE_DESC(R.string.library_sort_newest, Icons.Outlined.ArrowDownward),
    DATE_ASC(R.string.library_sort_oldest, Icons.Outlined.ArrowUpward),
    TITLE_ASC(R.string.library_sort_az, Icons.Outlined.SortByAlpha),
    TITLE_DESC(R.string.library_sort_za, Icons.Outlined.SortByAlpha)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBankClick: (Long) -> Unit,
    onNavigateToFetcher: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val banks by viewModel.banks.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val questionCounts by viewModel.questionCounts.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()
    val showEditBankDialog by viewModel.showEditBankDialog.collectAsState()
    val editingBank by viewModel.editingBank.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filteredBanks by viewModel.filteredBanks.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoryMap = remember(categories) { categories.associate { it.id to it } }
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    var showActionMenuFor by remember { mutableStateOf<QuestionBank?>(null) }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }

    ExportLauncher(
        exportState = exportState,
        onExportError = { viewModel.onExportError(it) },
        onClearExportState = { viewModel.clearExportState() }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readText()
                    }
                } ?: return@let
                viewModel.importBank(json)
            } catch (e: Exception) {
                viewModel.onImportError("Failed to read file: ${e.message}")
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            try {
                snackbarHostState.showSnackbar(it.asString(context))
            } finally {
                viewModel.clearSnackbar()
            }
        }
    }

    if (showDeleteConfirmDialog) {
        DialogWithIcon(
            onDismiss = { viewModel.dismissDeleteConfirmDialog() },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.library_delete_title),
            confirmText = stringResource(R.string.library_delete_confirm),
            onConfirm = { viewModel.confirmDeleteBank() },
            isDestructive = true,
            dismissText = stringResource(R.string.common_cancel),
            content = {
                Text(stringResource(R.string.library_delete_message))
            }
        )
    }

    if (showEditBankDialog) {
        editingBank?.let { bank ->
            EditBankDialog(
                initialTitle = bank.title,
                initialCategoryId = bank.categoryId,
                categories = categories,
                onDismiss = { viewModel.dismissEditBankDialog() },
                onConfirm = { title, categoryId -> viewModel.updateEditedBank(title, categoryId) }
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Route.Library.titleResId),
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                actions = {
                    if (banks.isNotEmpty()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.library_sort_and_filter)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showSortFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = stringResource(R.string.library_sort_and_filter)
                                )
                            }
                        }
                    }
                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.library_add)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { addMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.library_add)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_import_from_file)) },
                                onClick = {
                                    addMenuExpanded = false
                                    importLauncher.launch(
                                        arrayOf("application/json", "*/*")
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_fetch_from_chaoxing)) },
                                onClick = {
                                    addMenuExpanded = false
                                    onNavigateToFetcher()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (banks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.library_no_banks),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.library_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }
            } else if (filteredBanks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.library_no_search_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(filteredBanks, key = { it.id }, contentType = { "bank" }) { bank ->
                        QuestionBankCard(
                            bank = bank,
                            questionCount = questionCounts[bank.id] ?: 0,
                            category = categoryMap[bank.categoryId],
                            onClick = { onBankClick(bank.id) },
                            menuExpanded = showActionMenuFor?.id == bank.id,
                            onMenuToggle = {
                                showActionMenuFor = if (showActionMenuFor?.id == bank.id) null else bank
                            },
                            onExport = {
                                showActionMenuFor = null
                                viewModel.prepareExport(bank)
                            },
                            onEdit = {
                                showActionMenuFor = null
                                viewModel.showEditBankDialog(bank)
                            },
                            onDuplicate = {
                                showActionMenuFor = null
                                viewModel.duplicateBank(bank)
                            },
                            onDelete = {
                                showActionMenuFor = null
                                viewModel.deleteBank(bank)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Sort & Filter Bottom Sheet
    if (showSortFilterSheet) {
        SortFilterBottomSheet(
            sortMode = sortMode,
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onSortModeChange = { viewModel.setSortMode(it) },
            onCategoryChange = { viewModel.setSelectedCategory(it) },
            onDismiss = { showSortFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionBankCard(
    bank: QuestionBank,
    questionCount: Int,
    category: Category?,
    onClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .clickable(onClick = onClick),
        shape = AppShapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bank.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category?.displayName() ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.library_questions_count, questionCount, questionCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Box {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.library_more_options)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = onMenuToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.library_more_options),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { if (menuExpanded) onMenuToggle() }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_export)) },
                        onClick = onExport,
                        leadingIcon = {
                            Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        onClick = onEdit,
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_duplicate)) },
                        onClick = onDuplicate,
                        leadingIcon = {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = onDelete,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SortFilterBottomSheet(
    sortMode: SortMode,
    selectedCategoryId: Long?,
    categories: List<Category>,
    onSortModeChange: (SortMode) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Sort section
            Text(
                text = stringResource(R.string.library_sort),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { onSortModeChange(mode) },
                        label = { Text(stringResource(mode.labelResId)) },
                        leadingIcon = {
                            val iconAlpha by animateFloatAsState(
                                targetValue = if (sortMode == mode) 1f else 0f,
                                animationSpec = spring(),
                                label = "sortIconAlpha"
                            )
                            val slotWidth by animateDpAsState(
                                targetValue = if (sortMode == mode) 18.dp else 0.dp,
                                animationSpec = spring(),
                                label = "sortSlotWidth"
                            )
                            Box(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .height(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (slotWidth > 0.dp) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .alpha(iconAlpha)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Filter section (only if categories exist)
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.library_filter_by_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategoryChange(null) },
                        label = { Text(stringResource(R.string.library_filter_all)) },
                        leadingIcon = {
                            val iconAlpha by animateFloatAsState(
                                targetValue = if (selectedCategoryId == null) 1f else 0f,
                                animationSpec = spring(),
                                label = "allIconAlpha"
                            )
                            val slotWidth by animateDpAsState(
                                targetValue = if (selectedCategoryId == null) 18.dp else 0.dp,
                                animationSpec = spring(),
                                label = "allSlotWidth"
                            )
                            Box(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .height(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (slotWidth > 0.dp) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .alpha(iconAlpha)
                                    )
                                }
                            }
                        }
                    )
                    // Category chips
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onCategoryChange(category.id) },
                            label = { Text(category.displayName()) },
                            leadingIcon = {
                                val iconAlpha by animateFloatAsState(
                                    targetValue = if (selectedCategoryId == category.id) 1f else 0f,
                                    animationSpec = spring(),
                                    label = "catIconAlpha"
                                )
                                val slotWidth by animateDpAsState(
                                    targetValue = if (selectedCategoryId == category.id) 18.dp else 0.dp,
                                    animationSpec = spring(),
                                    label = "catSlotWidth"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(slotWidth)
                                        .height(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (slotWidth > 0.dp) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .alpha(iconAlpha)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
