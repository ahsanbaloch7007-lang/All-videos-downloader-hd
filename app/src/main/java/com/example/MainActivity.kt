package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ads.AdManager
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.ui.player.VideoPlayerDialog
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AllVideoDownloaderTheme

enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    BROWSER("Browser", Icons.Filled.Language, Icons.Outlined.Language, "nav_tab_browser"),
    DOWNLOADS("Downloads", Icons.Filled.Download, Icons.Outlined.Download, "nav_tab_downloads"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            AllVideoDownloaderTheme(darkTheme = isDarkTheme) {
                MainAppContainer(
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = { isDarkTheme = it },
                    activity = this
                )
            }
        }
    }
}

@Composable
fun MainAppContainer(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    activity: ComponentActivity
) {
    val db = remember { AppDatabase.getInstance(activity) }
    val allDownloads by db.downloadDao().getAllDownloads().collectAsState(initial = emptyList())
    val activeDownloadsCount = allDownloads.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
    }

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var playingVideo by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentTab != tab) {
                                currentTab = tab
                                // Natural transition ad check (throttled by AdManager)
                                AdManager.showInterstitialIfReady(activity)
                            }
                        },
                        icon = {
                            if (tab == AppTab.DOWNLOADS && activeDownloadsCount > 0) {
                                BadgedBox(badge = {
                                    Badge { Text(activeDownloadsCount.toString()) }
                                }) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    onNavigateToDownloads = { currentTab = AppTab.DOWNLOADS },
                    onNavigateToBrowser = { url ->
                        browserUrl = url
                        currentTab = AppTab.BROWSER
                    },
                    onPlayVideo = { item ->
                        playingVideo = Pair(item.localFilePath, item.title)
                    },
                    onPreviewStream = { streamUrl ->
                        playingVideo = Pair(streamUrl, "Streaming Preview")
                    }
                )

                AppTab.BROWSER -> BrowserScreen(
                    initialUrl = browserUrl,
                    onPreviewStream = { streamUrl ->
                        playingVideo = Pair(streamUrl, "Browser Stream")
                    }
                )

                AppTab.DOWNLOADS -> DownloadsScreen(
                    onPlayVideo = { item ->
                        playingVideo = Pair(item.localFilePath, item.title)
                    },
                    onNavigateToHome = { currentTab = AppTab.HOME }
                )

                AppTab.SETTINGS -> SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme
                )
            }

            // Video Player Dialog (fullscreen overlay with Media3 ExoPlayer)
            playingVideo?.let { (uriOrPath, title) ->
                VideoPlayerDialog(
                    videoUriOrPath = uriOrPath,
                    title = title,
                    onDismiss = {
                        playingVideo = null
                        // Natural transition point after closing a video
                        AdManager.showInterstitialIfReady(activity)
                    }
                )
            }
        }
    }
}
