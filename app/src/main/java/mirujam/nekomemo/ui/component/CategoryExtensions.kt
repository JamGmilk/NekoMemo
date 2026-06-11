package mirujam.nekomemo.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Category

@Composable
fun Category.displayName(): String = if (isDefault) {
    stringResource(R.string.category_general_display)
} else {
    name
}
