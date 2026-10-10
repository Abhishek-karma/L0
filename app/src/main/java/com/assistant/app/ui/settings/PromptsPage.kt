package com.assistant.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assistant.app.R
import com.assistant.app.data.PromptTemplate
import com.assistant.app.ui.components.AppIcons
import com.assistant.app.ui.components.PromptTemplateEditor
import com.assistant.app.ui.theme.AppSpacing

@Composable
internal fun PromptsPage(
    templates: List<PromptTemplate>,
    onSave: (String?, String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<PromptTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PromptTemplate?>(null) }

    if (creating) {
        PromptTemplateEditor(
            existing = null,
            onSave = { title, body ->
                onSave(null, title, body)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    editing?.let { template ->
        PromptTemplateEditor(
            existing = template,
            onSave = { title, body ->
                onSave(template.id, title, body)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.templates_delete_title)) },
            text = { Text(stringResource(R.string.templates_delete_message, template.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(template.id)
                        deleting = null
                    },
                ) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.templates_new_action),
            onClick = { creating = true },
        )
    }

    if (templates.isEmpty()) {
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            text = stringResource(R.string.templates_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Spacer(Modifier.height(AppSpacing.lg))
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        templates.forEach { template ->
            SettingsCard {
                SettingsRow(
                    label = template.title,
                    icon = AppIcons.Sparkle,
                    description = template.body,
                    onClick = { editing = template },
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { editing = template }) {
                        Text(stringResource(R.string.templates_edit_action))
                    }
                    TextButton(onClick = { deleting = template }) {
                        Text(
                            text = stringResource(R.string.menu_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}