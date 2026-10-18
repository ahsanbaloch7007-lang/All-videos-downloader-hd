# All Video Downloader – HD Video Saver

A native Android video downloader and offline media player application built with Kotlin, Jetpack Compose, Material Design 3, Media3 ExoPlayer, Room Database, and dual AdMob & StartApp monetization.

---

## Key Features

1. **Fast Direct Video Downloader**
   - Direct HTTP/HTTPS video stream downloader with real-time download progress, percentage, and speed tracking (KB/s and MB/s).
   - Multi-download concurrency handling.
   - HTTP Range header support for resuming partial downloads.
   - Pause, resume, retry, and cancellation controls.
   - Foreground notification channel alerting users of download progress and completion.
   - Download history preserved across app launches using SQLite (Room).

2. **Built-in Safe Browser & Media Sniffer**
   - Integrated WebView with URL address bar and search engine routing.
   - Built-in media detector that sniffs network resources and HTML5 `<video>` tags for downloadable `.mp4`, `.webm`, `.mkv`, and `.mov` media streams.
   - Floating 1-tap download prompt when media is detected on any webpage.
   - Navigation controls (Back, Forward, Refresh) and Quick Sites shortcuts (Archive.org, Wikimedia Commons, Pexels, Pixabay).
   - Secure configuration: disables file scheme access and cross-site scripting risks.

3. **Built-in HD Video Player**
   - Fullscreen video playback using Media3 ExoPlayer.
   - Supports local downloaded media files as well as direct streaming URLs.
   - Controls: Seek bar slider, Play/Pause, Rewind 10s, Forward 10s, duration formatters.

4. **Dual Monetization (AdMob & StartApp)**
   - **Centralized `AdManager`**:
     - **Google AdMob**: Configured with App ID `ca-app-pub-5956721462213427~1020362142` and Interstitial Unit ID `ca-app-pub-5956721462213427/4805537794`.
     - **StartApp (Start.io)**: Configured with App ID `209711096`.
     - Natural transition timing: ads are displayed only during natural breakpoints (e.g. closing video player or navigating tabs) with a mandatory 45-second cooldown throttle.
     - Never interrupts active downloads, video playback, or initial app launch.
     - Test mode toggle allowing testing with official Google test units during development.

5. **Design & Theming**
   - High-contrast Blue and White theme with professional Dark Mode.
   - Edge-to-edge support with Material 3 components.
   - Custom adaptive app icon with generated launcher artwork.
   - Clear privacy policy and terms of service compliant with Google Play guidelines.

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml          # Permissions, Application, AdMob ID, FileProvider
├── java/com/example/
│   ├── MainActivity.kt          # 4-tab Navigation, permission flows, video overlay
│   ├── VideoDownloaderApp.kt    # Application class initializing Room, Ads, & Services
│   ├── ads/
│   │   └── AdManager.kt         # Centralized AdMob & StartApp manager with cooldown
│   ├── data/
│   │   ├── local/               # Room Database (AppDatabase, DownloadDao, Converters)
│   │   └── model/               # Data classes (DownloadItem, Bookmark, VideoInfo)
│   ├── download/
│   │   ├── VideoDownloadManager.kt      # Download engine with pause/resume & speed
│   │   └── DownloadNotificationHelper.kt# Progress & completion notification manager
│   ├── ui/
│   │   ├── components/          # DownloadItemCard, VideoDownloadOptionsDialog
│   │   ├── player/              # VideoPlayerDialog (Media3 ExoPlayer)
│   │   ├── screens/             # HomeScreen, BrowserScreen, DownloadsScreen, SettingsScreen
│   │   └── theme/               # Color.kt, Theme.kt, Type.kt (Blue/White palette)
└── res/
    ├── drawable/                # img_app_icon.jpg, img_hero_downloader.jpg, img_empty_downloads.jpg
    ├── mipmap-*/                # Generated launcher icons across all densities
    └── values/                  # strings.xml, colors.xml
```

---

## Build & Run Instructions

### 1. Build Debug APK
To build the debug APK using Gradle:
```bash
gradle :app:assembleDebug
```
The resulting APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release APK / App Bundle (AAB)
To generate the signed release APK or Android App Bundle for Google Play:
```bash
# Release APK
gradle :app:assembleRelease

# Android App Bundle (AAB)
gradle :app:bundleRelease
```
The outputs are located at:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### 3. Run Automated Tests
```bash
gradle :app:testDebugUnitTest
```

---

## Google Play Publishing Checklist
1. **Package Name**: Update `applicationId` in `app/build.gradle.kts` if publishing under a custom developer domain (e.g., `com.yourcompany.alldownloader`).
2. **AdMob Account**: Link the app in your Google AdMob console and replace the test ads toggle with production ads.
3. **StartApp Account**: Ensure App ID `209711096` is registered in your StartApp portal.
4. **Data Safety**:
   - Location: None
   - Personal Info: None
   - App activity: In-app actions processed locally; advertising network diagnostics collected by AdMob and StartApp in compliance with Google Play Developer Program policies.
