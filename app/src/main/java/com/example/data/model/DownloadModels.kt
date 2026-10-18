package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val fileName: String,
    val localFilePath: String,
    val thumbnailPath: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val resolution: String = "HD",
    val mimeType: String = "video/mp4",
    val speedBytesPerSec: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else if (status == DownloadStatus.COMPLETED) {
            100
        } else {
            0
        }
}

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoInfo(
    val url: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val resolution: String,
    val isDirectVideo: Boolean
)
