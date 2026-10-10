package com.assistant.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.assistant.app.R
import com.assistant.app.data.PromptTemplate
import com.assistant.app.data.PromptTemplates

/**
 * Creates or edits a reusable prompt. A new prompt starts from [initialBody]
 * with its title derived from it. The body stays under
 * [PromptTemplates.MAX_BODY_CHARS] and the title is required, so an unusable
 * template cannot be stored.
 */
@Composable
fun PromptTemplateEditor(
    existing: PromptTemplate?,
    onSave: (title: String, body: String) -> Unit,
    onDismiss: () -> Unit,
    initialBody: String = "",
) {
    var title by remember(existing, initialBody) {
        mutableStateOf(existing?.title ?: PromptTemplates.titleFromBody(initialBody))
    }
    var body by remember(existing, initialBody) { mutableStateOf(existing?.body ?: initialBody) }
    val canSave = PromptTemplates.isValid(title, body)
    val overLimit = body.length > PromptTemplates.MAX_BODY_CHARS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.templates_new_title else R.string.templates_edit_title,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.templates_title_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.templates_body_field)) },
                    supportingText = {
                        Text(
                            text = if (overLimit) {
                                pluralStringResource(
                                    R.plurals.templates_too_long,
                                    PromptTemplates.MAX_BODY_CHARS,
                                    PromptTemplates.MAX_BODY_CHARS,
                                )
                            } else {
                                stringResource(R.string.templates_placeholder_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overLimit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.history_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}