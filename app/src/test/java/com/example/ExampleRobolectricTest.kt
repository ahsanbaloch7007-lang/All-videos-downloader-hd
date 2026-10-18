package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ads.AdManager
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.ui.components.formatFileSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoDownloaderRobolectricTest {

    @Test
    fun testAppNameStringResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("All Video Downloader", appName)
    }

    @Test
    fun testDownloadItemProgressCalculation() {
        val pendingItem = DownloadItem(
            url = "https://example.com/video.mp4",
            title = "Test Video",
            fileName = "video.mp4",
            localFilePath = "/path/video.mp4",
            totalBytes = 1000L,
            downloadedBytes = 250L,
            status = DownloadStatus.DOWNLOADING
        )
        assertEquals(25, pendingItem.progressPercent)

        val completedItem = pendingItem.copy(
            downloadedBytes = 1000L,
            status = DownloadStatus.COMPLETED
        )
        assertEquals(100, completedItem.progressPercent)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", formatFileSize(500))
        assertEquals("10 KB", formatFileSize(10 * 1024))
        assertEquals("5.0 MB", formatFileSize(5 * 1024 * 1024))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testAdManagerConfiguration() {
        assertEquals("ca-app-pub-5956721462213427~1020362142", AdManager.ADMOB_APP_ID)
        assertEquals("ca-app-pub-5956721462213427/4805537794", AdManager.ADMOB_PROD_INTERSTITIAL_ID)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdManager.ADMOB_TEST_INTERSTITIAL_ID)
        assertEquals("209711096", AdManager.STARTAPP_APP_ID)
    }

    @Test
    fun testRoomDatabaseInsertAndQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val dao = db.downloadDao()

        val item = DownloadItem(
            url = "https://example.com/sample.mp4",
            title = "Sample Film",
            fileName = "sample.mp4",
            localFilePath = "/storage/sample.mp4",
            totalBytes = 2048L,
            downloadedBytes = 0L,
            status = DownloadStatus.PENDING
        )

        val id = dao.insertDownload(item)
        assertTrue(id > 0)

        val retrieved = dao.getDownloadById(id)
        assertNotNull(retrieved)
        assertEquals("Sample Film", retrieved?.title)
        assertEquals(DownloadStatus.PENDING, retrieved?.status)

        dao.updateStatus(id, DownloadStatus.COMPLETED)
        val updated = dao.getDownloadById(id)
        assertEquals(DownloadStatus.COMPLETED, updated?.status)

        dao.deleteById(id)
        val afterDelete = dao.getDownloadById(id)
        assertEquals(null, afterDelete)
    }
}
