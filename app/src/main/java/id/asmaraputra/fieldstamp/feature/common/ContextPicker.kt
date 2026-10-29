package id.asmaraputra.fieldstamp.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Project

/**
 * "Label  value  ⇕" row that opens a picker. The label sits in a fixed left column; at large font scales
 * it moves above the value so neither truncates.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    icon: ImageVector = Icons.Outlined.UnfoldMore,
) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val labelText = @Composable {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val valueText = @Composable { m: Modifier ->
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (placeholder) FontWeight.Normal else FontWeight.Medium),
            color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = m,
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stacked) {
            Column(Modifier.weight(1f)) { labelText(); valueText(Modifier) }
        } else {
            Box(Modifier.width(LABEL_COLUMN)) { labelText() }
            valueText(Modifier.weight(1f))
        }
        Spacer(Modifier.width(Spacing.sm))
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val LABEL_COLUMN = 88.dp

data class PickerOption(val id: Long?, val label: String, val supporting: String? = null)

/** Single-choice bottom sheet with an optional "create new" row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    createLabel: String? = null,
    onCreate: ((String) -> Unit)? = null,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(options, key = { it.id ?: -1L }) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option.id == selectedId, role = Role.RadioButton, onClick = { onSelect(option.id) })
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Spacing.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.id == selectedId, onClick = null)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            option.supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (createLabel != null && onCreate != null) {
                    item {
                        FsDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { creating = true }.heightIn(min = 52.dp).padding(horizontal = Spacing.gutter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.md))
                            Text(createLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
    if (creating && onCreate != null && createLabel != null) {
        NameDialog(title = createLabel, initial = "", onConfirm = { creating = false; onCreate(it) }, onDismiss = { creating = false })
    }
}

/** Name (+ optional description) entry dialog. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialDescription: String? = null,
    onConfirmWithDescription: ((String, String) -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var description by rememberSaveable { mutableStateOf(initialDescription.orEmpty()) }
    val valid = name.isNotBlank() && name.length <= 120
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onConfirmWithDescription != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(500) },
                        label = { Text(stringResource(R.string.field_description_optional)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (onConfirmWithDescription != null) onConfirmWithDescription(name, description) else onConfirm(name)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Project + activity selection rows with their sheets. Used on Home, capture review and report setup. */
@Composable
fun ProjectActivityFields(
    projects: List<Project>,
    activities: List<FieldActivity>,
    projectId: Long?,
    activityId: Long?,
    onProjectChange: (Long?) -> Unit,
    onActivityChange: (Long?) -> Unit,
    onCreateProject: ((String) -> Unit)?,
    onCreateActivity: ((String) -> Unit)?,
    noneProjectLabel: String = stringResource(R.string.context_no_project),
    noneActivityLabel: String = stringResource(R.string.context_no_activity),
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val project = projects.firstOrNull { it.id == projectId }
    val projectActivities = activities.filter { it.projectId == projectId }
    val activity = projectActivities.firstOrNull { it.id == activityId }

    PickerField(
        label = stringResource(R.string.field_project),
        value = project?.name ?: noneProjectLabel,
        placeholder = project == null,
        onClick = { sheet = "project" },
    )
    FsDivider(inset = true)
    PickerField(
        label = stringResource(R.string.field_activity),
        value = activity?.name ?: noneActivityLabel,
        placeholder = activity == null,
        onClick = { sheet = "activity" },
    )

    when (sheet) {
        "project" -> OptionSheet(
            title = stringResource(R.string.field_project),
            options = listOf(PickerOption(null, noneProjectLabel)) + projects.filter { !it.isArchived || it.id == projectId }.map { PickerOption(it.id, it.name, it.description.ifBlank { null }) },
            selectedId = projectId,
            onSelect = { onProjectChange(it); sheet = null },
            onDismiss = { sheet = null },
            createLabel = onCreateProject?.let { stringResource(R.string.project_create) },
            onCreate = onCreateProject?.let { create -> { name: String -> create(name); sheet = null } },
        )
        "activity" -> OptionSheet(
            title = stringResource(R.string.field_activity),
            options = listOf(PickerOption(null, noneActivityLabel)) + projectActivities.map { PickerOption(it.id, it.name, it.description.ifBlank { null }) },
            selectedId = activityId,
            onSelect = { onActivityChange(it); sheet = null },
            onDismiss = { sheet = null },
            createLabel = onCreateActivity?.let { stringResource(R.string.activity_create) },
            onCreate = onCreateActivity?.let { create -> { name: String -> create(name); sheet = null } },
        )
    }
}
