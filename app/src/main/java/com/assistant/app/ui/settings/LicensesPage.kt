package com.assistant.app.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.assistant.app.R
import com.assistant.app.ui.theme.AppCodeFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LICENSES_ASSET = "licenses.txt"

@Composable
internal fun LicensesPage() {
    val context = LocalContext.current
    val assets = context.assets
    val entries by produceState<Pair<Boolean, List<LibraryLicense>>>(initialValue = false to emptyList()) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val raw = assets.open(LICENSES_ASSET).bufferedReader().use { it.readText() }
                true to LibraryLicenses.parse(raw)
            }.getOrDefault(true to emptyList())
        }
        value = loaded
    }
    Text(
        text = stringResource(R.string.licenses_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!entries.first) {
        Text(
            text = stringResource(R.string.licenses_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val libraries = entries.second
    if (libraries.isEmpty()) {
        Text(
            text = stringResource(R.string.licenses_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Text(
        text = pluralStringResource(
                R.plurals.licenses_count,
                libraries.size,
                libraries.size,
            ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    libraries.forEach { entry ->
        SettingsSectionHeader(text = entry.name)
        if (entry.license.isNotEmpty()) {
            Text(
                text = entry.license,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppCodeFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
