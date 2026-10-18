package com.example

import android.app.Application
import com.example.ads.AdManager
import com.example.data.local.AppDatabase
import com.example.download.DownloadNotificationHelper
import com.example.download.VideoDownloadManager

class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Room Database
        AppDatabase.getInstance(this)

        // Initialize Notification Channels
        DownloadNotificationHelper(this)

        // Initialize Download Manager
        VideoDownloadManager.getInstance(this)

        // Initialize Monetization Networks (AdMob + StartApp)
        AdManager.initialize(this)
    }
}
