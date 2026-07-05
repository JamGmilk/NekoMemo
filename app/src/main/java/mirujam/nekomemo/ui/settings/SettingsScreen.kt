package mirujam.nekomemo.ui.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mirujam.nekomemo.BuildConfig
import mirujam.nekomemo.R
import mirujam.nekomemo.data.preferences.ThemeMode
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.domain.validator.DataValidator
import mirujam.nekomemo.navigation.BOTTOM_BAR_HEIGHT
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.component.displayName
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.util.clearWebViewData

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showWebViewClearDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showRenameCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var snackbarTrigger by remember { mutableIntStateOf(0) }

    val bankCount by viewModel.bankCount.collectAsStateWithLifecycle()
    val totalQuestionCount by viewModel.totalQuestionCount.collectAsStateWithLifecycle()
    val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val directAnswer by viewModel.directAnswer.collectAsStateWithLifecycle()
    val autoNextOnCorrect by viewModel.autoNextOnCorrect.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryError by viewModel.categoryError.collectAsStateWithLifecycle()
    val bankCountsByCategory by viewModel.bankCountsByCategory.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    val bottomBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BOTTOM_BAR_HEIGHT

    LaunchedEffect(snackbarTrigger) {
        if (snackbarTrigger > 0) {
            snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.categoryEvent.collect { event ->
            when (event) {
                is CategoryOperationResult.Added -> {
                    showAddCategoryDialog = false
                    snackbarMessage = context.getString(R.string.settings_category_added, event.name)
                    snackbarTrigger++
                }
                is CategoryOperationResult.Renamed -> {
                    showRenameCategoryDialog = false
                    selectedCategory = null
                    snackbarMessage = context.getString(R.string.settings_category_renamed, event.name)
                    snackbarTrigger++
                }
                is CategoryOperationResult.Deleted -> {
                    showDeleteCategoryDialog = false
                    selectedCategory = null
                    snackbarMessage = context.getString(R.string.settings_category_deleted, event.name)
                    snackbarTrigger++
                }
                is CategoryOperationResult.Error -> {
                    showDeleteCategoryDialog = false
                    showAddCategoryDialog = false
                    showRenameCategoryDialog = false
                    selectedCategory = null
                    snackbarMessage = event.message.asString(context)
                    snackbarTrigger++
                }
            }
        }
    }

    LaunchedEffect(categoryError) {
        categoryError?.let { error ->
            snackbarMessage = error.asString(context)
            snackbarTrigger++
            viewModel.clearCategoryError()
        }
    }

    if (showClearDialog) {
        DialogWithIcon(
            onDismiss = { showClearDialog = false },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.settings_clear_db_title),
            confirmText = stringResource(R.string.settings_clear),
            onConfirm = {
                viewModel.clearAllData()
                showClearDialog = false
                snackbarMessage = context.getString(R.string.settings_clear_db_success)
                snackbarTrigger++
            },
            isDestructive = true,
            dismissText = stringResource(R.string.settings_cancel),
            content = {
                Text(stringResource(R.string.settings_clear_db_message))
            }
        )
    }

    if (showWebViewClearDialog) {
        DialogWithIcon(
            onDismiss = { showWebViewClearDialog = false },
            icon = Icons.Outlined.CleaningServices,
            title = stringResource(R.string.settings_clear_webview_title),
            confirmText = stringResource(R.string.settings_clear),
            onConfirm = {
                clearWebViewData(context)
                showWebViewClearDialog = false
                snackbarMessage = context.getString(R.string.settings_clear_webview_success)
                snackbarTrigger++
            },
            isDestructive = true,
            dismissText = stringResource(R.string.settings_cancel),
            content = {
                Text(stringResource(R.string.settings_clear_webview_message))
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name)
            }
        )
    }

    if (showRenameCategoryDialog && selectedCategory != null) {
        RenameCategoryDialog(
            currentName = selectedCategory!!.name,
            onDismiss = {
                showRenameCategoryDialog = false
                selectedCategory = null
            },
            onConfirm = { newName ->
                viewModel.renameCategory(selectedCategory!!.id, newName)
            }
        )
    }

    if (showDeleteCategoryDialog && selectedCategory != null) {
        val hasBanks = (bankCountsByCategory[selectedCategory!!.id] ?: 0) > 0
        val dialogMessage = if (hasBanks) {
            stringResource(R.string.settings_delete_category_confirm, selectedCategory!!.name)
        } else {
            stringResource(R.string.settings_delete_category_confirm_empty, selectedCategory!!.name)
        }
        DialogWithIcon(
            onDismiss = {
                showDeleteCategoryDialog = false
                selectedCategory = null
            },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.settings_delete_category),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteCategory(selectedCategory!!.id)
            },
            isDestructive = true,
            dismissText = stringResource(R.string.common_cancel),
            content = {
                Text(dialogMessage)
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Route.Settings.titleResId)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp + bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppearanceCard(
                currentTheme = currentTheme,
                onThemeChange = { viewModel.setThemeMode(it) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LanguageCard()
            }

            TestSettingsCard(
                directAnswer = directAnswer,
                onDirectAnswerChange = { viewModel.setDirectAnswer(it) },
                autoNextOnCorrect = autoNextOnCorrect,
                onAutoNextOnCorrectChange = { viewModel.setAutoNextOnCorrect(it) }
            )

            StatisticsCard(
                bankCount = bankCount,
                totalQuestionCount = totalQuestionCount
            )

            val onAddClick = remember { { showAddCategoryDialog = true } }
            val onRenameClick = remember {
                { category: Category ->
                    selectedCategory = category
                    showRenameCategoryDialog = true
                }
            }
            val onDeleteClick = remember {
                { category: Category ->
                    selectedCategory = category
                    showDeleteCategoryDialog = true
                }
            }

            CategoryCard(
                categories = categories,
                onAddCategory = onAddClick,
                onRenameCategory = onRenameClick,
                onDeleteCategory = onDeleteClick
            )

            val onClearDbClick = remember { { showClearDialog = true } }
            val onClearWebViewClick = remember { { showWebViewClearDialog = true } }

            DataManagementCard(
                onClearDatabase = onClearDbClick,
                onClearWebViewData = onClearWebViewClick
            )

            val onOpenSourceClick = remember {
                {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://github.com/JamGmilk/NekoMemo".toUri())
                    )
                }
            }

            AboutCard(
                onOpenSourceClick = onOpenSourceClick
            )


        }
    }
}

@Composable
private fun AppearanceCard(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_appearance),
        icon = Icons.Outlined.DarkMode,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = currentTheme == mode
                val icon = remember(mode) {
                    when (mode) {
                        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                    }
                }

                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }

                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                val border = if (!isSelected) {
                    ButtonDefaults.outlinedButtonBorder(enabled = true)
                } else {
                    null
                }

                Button(
                    onClick = { onThemeChange(mode) },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.medium,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    ),
                    border = border
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(mode.labelResId()),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    SettingsCard(
        title = stringResource(R.string.settings_language),
        icon = Icons.Outlined.Translate,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = false,
                    role = Role.Button,
                    onValueChange = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            val fallbackIntent = Intent(Settings.ACTION_LOCALE_SETTINGS)
                            context.startActivity(fallbackIntent)
                        }
                    }
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TestSettingsCard(
    directAnswer: Boolean,
    onDirectAnswerChange: (Boolean) -> Unit,
    autoNextOnCorrect: Boolean,
    onAutoNextOnCorrectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_test_settings),
        icon = Icons.Outlined.Quiz,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = directAnswer,
                        onValueChange = onDirectAnswerChange,
                        role = Role.Switch
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_direct_answer),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_direct_answer_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = directAnswer,
                    onCheckedChange = null
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoNextOnCorrect,
                        onValueChange = onAutoNextOnCorrectChange,
                        role = Role.Switch
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_auto_next_on_correct),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_auto_next_on_correct_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoNextOnCorrect,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatisticsCard(
    bankCount: Int,
    totalQuestionCount: Int,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_statistics),
        icon = Icons.Outlined.QueryStats,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                value = bankCount.toString(),
                label = stringResource(R.string.settings_banks),
                modifier = Modifier.weight(1f)
            )

            StatItem(
                value = totalQuestionCount.toString(),
                label = stringResource(R.string.settings_questions),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    categories: List<Category>,
    onAddCategory: () -> Unit,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_category_management),
        icon = Icons.Outlined.Category,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_add_category_suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onAddCategory,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.settings_add_category))
                }
            }

            if (categories.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tooltipPosition = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above
                    )

                    categories.forEach { category ->
                        val isDefault = category.isDefault

                        val displayName = category.displayName()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TooltipBox(
                                        positionProvider = tooltipPosition,
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.settings_rename_category)) } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(
                                            onClick = { onRenameCategory(category) },
                                            enabled = !isDefault
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = stringResource(R.string.settings_rename_category),
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isDefault) {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    TooltipBox(
                                        positionProvider = tooltipPosition,
                                        tooltip = { PlainTooltip { Text(stringResource(R.string.settings_delete_category)) } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(
                                            onClick = { onDeleteCategory(category) },
                                            enabled = !isDefault
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.settings_delete_category),
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isDefault) {
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataManagementCard(
    onClearDatabase: () -> Unit,
    onClearWebViewData: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_data_management),
        icon = Icons.Outlined.Storage,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataItemRow(
                title = stringResource(R.string.settings_local_db),
                description = stringResource(R.string.settings_local_db_desc),
                buttonText = stringResource(R.string.settings_clear_database),
                icon = Icons.Outlined.DeleteOutline,
                onClick = onClearDatabase,
                isDestructive = true
            )

            DataItemRow(
                title = stringResource(R.string.settings_webview_data),
                description = stringResource(R.string.settings_webview_data_desc),
                buttonText = stringResource(R.string.settings_clear_cache_cookies),
                icon = Icons.Outlined.CleaningServices,
                onClick = onClearWebViewData,
                isDestructive = false
            )
        }
    }
}

@Composable
private fun AboutCard(
    onOpenSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title = stringResource(R.string.settings_about),
        icon = Icons.Outlined.Info,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "JamGmilk",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenSourceClick,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_open_source),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowOutward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    val isValid = categoryName.isNotBlank() && categoryName.length <= DataValidator.MAX_CATEGORY_LENGTH

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Add,
        title = stringResource(R.string.settings_add_category),
        confirmText = stringResource(R.string.common_save),
        onConfirm = { onConfirm(categoryName.trim().take(DataValidator.MAX_CATEGORY_LENGTH)) },
        confirmEnabled = isValid,
        dismissText = stringResource(R.string.common_cancel),
        content = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text(stringResource(R.string.settings_add_category_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraSmall,
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                isError = categoryName.length > DataValidator.MAX_CATEGORY_LENGTH,
                supportingText = if (categoryName.length > DataValidator.MAX_CATEGORY_LENGTH) {
                    { Text("${categoryName.length}/${DataValidator.MAX_CATEGORY_LENGTH}") }
                } else null
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_add_category_suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun RenameCategoryDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf(currentName) }
    val isValid = categoryName.isNotBlank() && categoryName != currentName && categoryName.length <= DataValidator.MAX_CATEGORY_LENGTH

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.settings_rename_category),
        confirmText = stringResource(R.string.common_save),
        onConfirm = { onConfirm(categoryName.trim().take(DataValidator.MAX_CATEGORY_LENGTH)) },
        confirmEnabled = isValid,
        dismissText = stringResource(R.string.common_cancel),
        content = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text(stringResource(R.string.settings_category_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraSmall,
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                isError = categoryName.length > DataValidator.MAX_CATEGORY_LENGTH,
                supportingText = if (categoryName.length > DataValidator.MAX_CATEGORY_LENGTH) {
                    { Text("${categoryName.length}/${DataValidator.MAX_CATEGORY_LENGTH}") }
                } else null
            )
        }
    )
}

@Composable
private fun ThemeMode.labelResId(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun DataItemRow(
    title: String,
    description: String,
    buttonText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val buttonColors = if (isDestructive) {
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        }

        val border = if (isDestructive) {
            ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            )
        } else {
            null
        }

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.medium,
            colors = buttonColors,
            border = border
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            content()
        }
    }
}
