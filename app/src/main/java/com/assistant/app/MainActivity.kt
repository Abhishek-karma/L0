package com.assistant.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.assistant.app.data.SharedContent
import com.assistant.app.data.SharedIntent
import com.assistant.app.data.settings.AppTheme
import com.assistant.app.ui.AssistantNavHost
import com.assistant.app.ui.theme.ChatTheme

class MainActivity : ComponentActivity() {

    private var pendingShare by mutableStateOf<SharedContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {

        setTheme(R.style.Theme_L0)
        super.onCreate(savedInstanceState)

        // A recreation replays the same intent, so only the first delivery may stage it.
        if (savedInstanceState == null) pendingShare = SharedIntent.read(intent)

        enableEdgeToEdge()
        val container = (application as AssistantApp).container
        container.updateManager.checkOnLaunch()

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {  }

                LaunchedEffect(Unit) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            val appearance by container.appearance.collectAsState()
            val darkTheme = when (appearance) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            val view = LocalView.current
            SideEffect { applySystemBarIconAppearance(view, darkTheme) }
            val textSize by container.textSize.collectAsState()
            ChatTheme(darkTheme = darkTheme) {

                val density = LocalDensity.current
                val chatFactory = remember(container) { container.chatViewModelFactory() }
                val settingsFactory = remember(container) { container.settingsViewModelFactory() }
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * textSize.scale),
                ) {
                    AssistantNavHost(
                        chatViewModelFactory = chatFactory,
                        settingsViewModelFactory = settingsFactory,
                        appPreferences = container.appPreferences,
                        pendingShare = pendingShare,
                        onShareConsumed = { pendingShare = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = SharedIntent.read(intent)
    }
}

internal fun applySystemBarIconAppearance(view: View, darkTheme: Boolean) {
    val window = (view.context as Activity).window
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
