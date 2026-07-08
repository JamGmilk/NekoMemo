package mirujam.nekomemo.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.domain.validator.DataValidator
import mirujam.nekomemo.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankDialog(
    initialTitle: String,
    initialCategoryId: Long,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selectedCategoryId by remember(initialCategoryId, categories) {
        mutableLongStateOf(
            if (categories.any { it.id == initialCategoryId }) initialCategoryId
            else categories.firstOrNull()?.id ?: initialCategoryId
        )
    }
    var expanded by remember { mutableStateOf(false) }

    val isTitleValid = title.isNotBlank() && title.length <= DataValidator.MAX_TITLE_LENGTH
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name
        ?: if (selectedCategoryId == 0L && categories.isNotEmpty()) categories.first().name else ""

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.detail_edit_dialog_title),
        confirmText = stringResource(R.string.common_save),
        onConfirm = {
            val trimmedTitle = title.trim().take(DataValidator.MAX_TITLE_LENGTH)
            onConfirm(trimmedTitle, selectedCategoryId)
        },
        confirmEnabled = isTitleValid && selectedCategoryId > 0,
        dismissText = stringResource(R.string.common_cancel),
        content = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.extract_bank_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.extraSmall,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = title.length > DataValidator.MAX_TITLE_LENGTH,
                    supportingText = if (title.length > DataValidator.MAX_TITLE_LENGTH) {
                        { Text("${title.length}/${DataValidator.MAX_TITLE_LENGTH}") }
                    } else null
                )
                Spacer(Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val displayName = if (selectedCategoryName == Category.DEFAULT_CATEGORY_NAME) {
                        stringResource(R.string.category_general_display)
                    } else selectedCategoryName
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .focusable(interactionSource = interactionSource)
                    ) {
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = displayName,
                            innerTextField = {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            label = { Text(stringResource(R.string.extract_category_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = interactionSource,
                                    shape = AppShapes.extraSmall
                                )
                            }
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            val displayName = if (category.name == Category.DEFAULT_CATEGORY_NAME) {
                                stringResource(R.string.category_general_display)
                            } else category.name
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        }
    )
}
