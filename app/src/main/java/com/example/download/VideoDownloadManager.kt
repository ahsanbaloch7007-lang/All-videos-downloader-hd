package com.example.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VideoDownloadManager(private val context: Context) {
    companion object {
        private const val TAG = "VideoDownloadMgr"

        @Volatile
        private var INSTANCE: VideoDownloadManager? = null

        fun getInstance(context: Context): VideoDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VideoDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val downloadDao = db.downloadDao()
    private val notificationHelper = DownloadNotificationHelper(context)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // Event flow for UI alerts (e.g. download finished, error occurred)
    private val _downloadEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val downloadEvents: SharedFlow<String> = _downloadEvents.asSharedFlow()

    private fun getDownloadsDirectory(): File {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val videoSaverDir = File(moviesDir, "VideoSaver")
        if (!videoSaverDir.exists()) {
            videoSaverDir.mkdirs()
        }
        return videoSaverDir
    }

    /**
     * Inspects a URL to get metadata (filename, mimeType, size, resolution)
     */
    suspend fun analyzeUrl(rawUrl: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid HTTP or HTTPS URL."))
        }

        try {
            // First try HEAD request to inspect headers without downloading body
            val headRequest = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .head()
                .build()

            var response = try {
                okHttpClient.newCall(headRequest).execute()
            } catch (_: Exception) {
                null
            }

            // If HEAD is not supported (some servers return 405 or 403), fallback to GET with Range 0-0
            if (response == null || !response.isSuccessful) {
                response?.close()
                val getRangeRequest = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    .header("Range", "bytes=0-1024")
                    .build()
                response = okHttpClient.newCall(getRangeRequest).execute()
            }

            if (!response.isSuccessful && response.code != 206) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("Server returned HTTP $code. The video link may be expired or restricted."))
            }

            val contentType = response.header("Content-Type") ?: "video/mp4"
            val contentLengthHeader = response.header("Content-Length")
            val contentRangeHeader = response.header("Content-Range")
            val contentDisposition = response.header("Content-Disposition")

            var totalBytes = contentLengthHeader?.toLongOrNull() ?: 0L
            if (totalBytes == 0L && contentRangeHeader != null) {
                // Content-Range format: bytes 0-1024/totalBytes
                val parts = contentRangeHeader.split("/")
                if (parts.size == 2) {
                    totalBytes = parts[1].trim().toLongOrNull() ?: 0L
                }
            }

            val extractedName = extractFileName(cleanUrl, contentDisposition, contentType)
            val title = extractedName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .capitalizeWords()

            val isVideo = contentType.contains("video", ignoreCase = true) ||
                    cleanUrl.contains(".mp4", ignoreCase = true) ||
                    cleanUrl.contains(".mkv", ignoreCase = true) ||
                    cleanUrl.contains(".webm", ignoreCase = true) ||
                    cleanUrl.contains(".mov", ignoreCase = true)

            // Detect resolution heuristic from filename/URL
            val resolution = when {
                cleanUrl.contains("2160p", ignoreCase = true) || cleanUrl.contains("4k", ignoreCase = true) -> "4K (2160p)"
                cleanUrl.contains("1080p", ignoreCase = true) || cleanUrl.contains("fhd", ignoreCase = true) -> "1080p Full HD"
                cleanUrl.contains("720p", ignoreCase = true) || cleanUrl.contains("hd", ignoreCase = true) -> "720p HD"
                cleanUrl.contains("480p", ignoreCase = true) -> "480p SD"
                cleanUrl.contains("360p", ignoreCase = true) -> "360p"
                else -> "Original HD"
            }

            response.close()

            Result.success(
                VideoInfo(
                    url = cleanUrl,
                    title = if (title.isBlank()) "Video Download" else title,
                    fileName = extractedName,
                    mimeType = contentType,
                    totalBytes = totalBytes,
                    resolution = resolution,
                    isDirectVideo = isVideo
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze URL: ${e.message}", e)
            Result.failure(Exception("Unable to analyze URL: ${e.localizedMessage ?: "Network error"}"))
        }
    }

    private fun extractFileName(url: String, disposition: String?, contentType: String): String {
        // 1. Try Content-Disposition
        if (disposition != null && disposition.contains("filename=")) {
            val fnPart = disposition.substringAfter("filename=").replace("\"", "").trim()
            if (fnPart.isNotBlank()) {
                return fnPart.split(";")[0].trim()
            }
        }

        // 2. Try URL path segment
        try {
            val decoded = URLDecoder.decode(url, "UTF-8")
            val cleanPath = decoded.substringBefore("?").substringBefore("#")
            val lastSegment = cleanPath.substringAfterLast("/")
            if (lastSegment.contains(".")) {
                return lastSegment
            }
        } catch (_: Exception) {}

        // 3. Fallback timestamp
        val ext = when {
            contentType.contains("mp4") -> ".mp4"
            contentType.contains("webm") -> ".webm"
            contentType.contains("mkv") -> ".mkv"
            contentType.contains("quicktime") -> ".mov"
            else -> ".mp4"
        }
        return "Video_${System.currentTimeMillis()}$ext"
    }

    /**
     * Enqueues and starts a video download
     */
    suspend fun startDownload(
        videoInfo: VideoInfo,
        customTitle: String? = null,
        chosenResolution: String = videoInfo.resolution
    ): Long = withContext(Dispatchers.IO) {
        val sanitizedFileName = videoInfo.fileName
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(getDownloadsDirectory(), sanitizedFileName)

        // Ensure unique filename if already exists
        var finalFile = destFile
        var counter = 1
        while (finalFile.exists()) {
            val nameWithoutExt = sanitizedFileName.substringBeforeLast(".")
            val ext = sanitizedFileName.substringAfterLast(".", "mp4")
            finalFile = File(getDownloadsDirectory(), "${nameWithoutExt}_$counter.$ext")
            counter++
        }

        val item = DownloadItem(
            url = videoInfo.url,
            title = customTitle ?: videoInfo.title,
            fileName = finalFile.name,
            localFilePath = finalFile.absolutePath,
            totalBytes = videoInfo.totalBytes,
            downloadedBytes = 0,
            status = DownloadStatus.PENDING,
            resolution = chosenResolution,
            mimeType = videoInfo.mimeType
        )

        val id = downloadDao.insertDownload(item)
        val newItem = item.copy(id = id)

        launchDownloadJob(newItem)
        id
    }

    fun resumeDownload(id: Long) {
        downloadScope.launch {
            val item = downloadDao.getDownloadById(id) ?: return@launch
            if (item.status == DownloadStatus.DOWNLOADING) return@launch
            launchDownloadJob(item)
        }
    }

    fun pauseDownload(id: Long) {
        val job = activeJobs[id]
        if (job != null) {
            job.cancel()
            activeJobs.remove(id)
        }
        downloadScope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED)
            notificationHelper.cancelNotification(id)
        }
    }

    fun cancelDownload(id: Long) {
        val job = activeJobs[id]
        if (job != null) {
            job.cancel()
            activeJobs.remove(id)
        }
        downloadScope.launch {
            val item = downloadDao.getDownloadById(id)
            if (item != null) {
                val file = File(item.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                downloadDao.updateStatus(id, DownloadStatus.CANCELLED)
            }
            notificationHelper.cancelNotification(id)
        }
    }

    fun retryDownload(id: Long) {
        downloadScope.launch {
            val item = downloadDao.getDownloadById(id) ?: return@launch
            val file = File(item.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            val resetItem = item.copy(
                downloadedBytes = 0,
                status = DownloadStatus.PENDING,
                errorMessage = null
            )
            downloadDao.updateDownload(resetItem)
            launchDownloadJob(resetItem)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        val job = activeJobs[item.id]
        if (job != null) {
            job.cancel()
            activeJobs.remove(item.id)
        }
        downloadScope.launch {
            val file = File(item.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            downloadDao.deleteDownload(item)
            notificationHelper.cancelNotification(item.id)
        }
    }

    private fun launchDownloadJob(item: DownloadItem) {
        // Cancel any existing job for this id
        activeJobs[item.id]?.cancel()

        val job = downloadScope.launch {
            executeDownload(item)
        }
        activeJobs[item.id] = job
    }

    private suspend fun executeDownload(item: DownloadItem) {
        val id = item.id
        val file = File(item.localFilePath)
        var downloadedBytes = if (file.exists()) file.length() else 0L

        downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)

        try {
            val requestBuilder = Request.Builder()
                .url(item.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")

            // Support resume via HTTP Range if already partially downloaded
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                // If 416 (Range Not Satisfiable), delete partial file and retry from 0
                if (response.code == 416) {
                    response.close()
                    if (file.exists()) file.delete()
                    val retryRequest = Request.Builder()
                        .url(item.url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                        .build()
                    val retryResponse = okHttpClient.newCall(retryRequest).execute()
                    if (!retryResponse.isSuccessful) {
                        val code = retryResponse.code
                        retryResponse.close()
                        throw Exception("HTTP Error $code while downloading")
                    }
                    downloadFileStream(retryResponse, file, id, item.title, 0L, item.totalBytes)
                    return
                } else {
                    val code = response.code
                    response.close()
                    throw Exception("HTTP Error $code while downloading video")
                }
            }

            val body = response.body ?: throw Exception("Empty response body from server")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) {
                if (response.code == 206) downloadedBytes + contentLength else contentLength
            } else {
                item.totalBytes
            }

            downloadFileStream(response, file, id, item.title, downloadedBytes, totalBytes)

        } catch (e: CancellationException) {
            Log.d(TAG, "Download #$id was paused/cancelled.")
        } catch (e: Exception) {
            Log.e(TAG, "Download #$id failed: ${e.message}", e)
            downloadDao.updateDownload(
                item.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Download failed"
                )
            )
            notificationHelper.cancelNotification(id)
            _downloadEvents.emit("Download failed: ${e.localizedMessage ?: "Network error"}")
        } finally {
            activeJobs.remove(id)
        }
    }

    private suspend fun downloadFileStream(
        response: okhttp3.Response,
        file: File,
        id: Long,
        title: String,
        startDownloadedBytes: Long,
        totalBytes: Long
    ) {
        var currentDownloaded = startDownloadedBytes
        val output = RandomAccessFile(file, "rw")
        output.seek(startDownloadedBytes)

        val input: InputStream = response.body?.byteStream() ?: throw Exception("Null stream")
        val buffer = ByteArray(32 * 1024)
        var bytesRead: Int

        var lastDbUpdateMs = System.currentTimeMillis()
        var bytesSinceLastSpeedCalc = 0L
        var lastSpeedCalcMs = System.currentTimeMillis()
        var currentSpeedBytesPerSec = 0L

        try {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                bytesSinceLastSpeedCalc += bytesRead

                val now = System.currentTimeMillis()
                val speedDuration = now - lastSpeedCalcMs
                if (speedDuration >= 1000) {
                    currentSpeedBytesPerSec = (bytesSinceLastSpeedCalc * 1000) / speedDuration
                    bytesSinceLastSpeedCalc = 0L
                    lastSpeedCalcMs = now
                }

                // Update DB & notification every 500ms
                if (now - lastDbUpdateMs >= 500) {
                    downloadDao.updateProgress(
                        id = id,
                        downloaded = currentDownloaded,
                        total = totalBytes,
                        speed = currentSpeedBytesPerSec,
                        status = DownloadStatus.DOWNLOADING
                    )

                    val percent = if (totalBytes > 0) ((currentDownloaded * 100) / totalBytes).toInt() else 0
                    val speedText = formatSpeed(currentSpeedBytesPerSec)
                    notificationHelper.showProgressNotification(id, title, percent, speedText)

                    lastDbUpdateMs = now
                }
            }

            // Finished!
            output.close()
            input.close()
            response.close()

            val finalTotal = if (totalBytes <= 0) currentDownloaded else totalBytes
            downloadDao.updateProgress(
                id = id,
                downloaded = currentDownloaded,
                total = finalTotal,
                speed = 0,
                status = DownloadStatus.COMPLETED
            )
            val updated = downloadDao.getDownloadById(id)
            if (updated != null) {
                downloadDao.updateDownload(updated.copy(completedAt = System.currentTimeMillis()))
            }

            notificationHelper.showCompletedNotification(id, title)
            _downloadEvents.emit("Completed download: $title")

        } finally {
            try { output.close() } catch (_: Exception) {}
            try { input.close() } catch (_: Exception) {}
            try { response.close() } catch (_: Exception) {}
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            bytesPerSec > 0 -> "$bytesPerSec B/s"
            else -> "0 KB/s"
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
