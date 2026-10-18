package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.VideoInfo
import com.example.download.VideoDownloadManager
import com.example.ui.components.VideoDownloadOptionsDialog
import kotlinx.coroutines.launch
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onPreviewStream: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { VideoDownloadManager.getInstance(context) }

    var currentUrl by remember { mutableStateOf(initialUrl ?: "https://www.google.com") }
    var addressInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("Browser") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Detected media stream on current page
    var detectedMediaUrl by remember { mutableStateOf<String?>(null) }
    var analyzedVideoInfo by remember { mutableStateOf<VideoInfo?>(null) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val quickSites = remember {
        listOf(
            "Archive.org" to "https://archive.org/details/movies",
            "Wikimedia" to "https://commons.wikimedia.org/wiki/Category:Featured_videos",
            "Pexels" to "https://www.pexels.com/videos/",
            "Pixabay" to "https://pixabay.com/videos/",
            "Google" to "https://www.google.com"
        )
    }

    // Intercept back button to navigate WebView back
    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    fun navigateTo(input: String) {
        val trimmed = input.trim()
        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
        }
        addressInput = targetUrl
        currentUrl = targetUrl
        webViewInstance?.loadUrl(targetUrl)
    }

    fun checkForVideoMedia(url: String) {
        val lower = url.lowercase()
        if (lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") || lower.contains(".mov")) {
            if (!lower.contains(".jpg") && !lower.contains(".png") && !lower.contains(".gif")) {
                detectedMediaUrl = url
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browser_screen")
    ) {
        // Address Bar & Controls
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.testTag("browser_btn_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.testTag("browser_btn_forward")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward"
                        )
                    }

                    // Address input
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        singleLine = true,
                        placeholder = { Text("Search or type URL", maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (addressInput.startsWith("https://")) Icons.Default.Lock else Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (addressInput.startsWith("https://")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateTo(addressInput) }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_address_bar")
                    )

                    IconButton(
                        onClick = {
                            if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                        },
                        modifier = Modifier.testTag("browser_btn_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }

                // Quick Sites Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSites.forEach { (name, url) ->
                        ElevatedAssistChip(
                            onClick = { navigateTo(url) },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("browser_chip_${name}")
                        )
                    }
                }

                // Page Loading Progress Bar
                if (isLoading && loadProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // WebView and Floating Download Prompt
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            // Secure configuration: disable file scheme access
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    addressInput = it
                                    currentUrl = it
                                    checkForVideoMedia(it)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                pageTitle = view?.title ?: "Browser"

                                // Inject JavaScript to detect HTML5 <video> elements on the page
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        var videos = document.getElementsByTagName('video');
                                        if (videos.length > 0) {
                                            for (var i = 0; i < videos.length; i++) {
                                                if (videos[i].src) return videos[i].src;
                                                var sources = videos[i].getElementsByTagName('source');
                                                if (sources.length > 0 && sources[0].src) return sources[0].src;
                                            }
                                        }
                                        return '';
                                    })();
                                    """.trimIndent()
                                ) { result ->
                                    val cleanResult = result?.replace("\"", "")?.trim()
                                    if (!cleanResult.isNullOrBlank() && cleanResult != "null") {
                                        detectedMediaUrl = cleanResult
                                    }
                                }
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                url?.let { checkForVideoMedia(it) }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.url?.toString()?.let { reqUrl ->
                                    checkForVideoMedia(reqUrl)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress >= 100) isLoading = false
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { pageTitle = it }
                            }
                        }

                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating "Video Detected" Action Banner
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedMediaUrl != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browser_video_detected_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Detected!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = detectedMediaUrl ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                detectedMediaUrl?.let { mediaUrl ->
                                    coroutineScope.launch {
                                        val result = downloadManager.analyzeUrl(mediaUrl)
                                        result.onSuccess { info ->
                                            analyzedVideoInfo = info
                                        }.onFailure {
                                            Toast.makeText(context, "Could not load video metadata", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("browser_download_detected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }

    // Video Options Dialog (when detected video is tapped)
    analyzedVideoInfo?.let { info ->
        VideoDownloadOptionsDialog(
            videoInfo = info,
            onDismiss = { analyzedVideoInfo = null },
            onConfirmDownload = { chosenTitle, chosenRes ->
                coroutineScope.launch {
                    downloadManager.startDownload(info, chosenTitle, chosenRes)
                    analyzedVideoInfo = null
                    detectedMediaUrl = null
                    Toast.makeText(context, "Download started: $chosenTitle", Toast.LENGTH_SHORT).show()
                }
            },
            onPreviewStream = { url ->
                analyzedVideoInfo = null
                onPreviewStream(url)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }
}
