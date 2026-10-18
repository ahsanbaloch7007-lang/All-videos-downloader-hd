package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdStatus(
    val admobInitialized: Boolean = false,
    val admobLoaded: Boolean = false,
    val admobLastError: String? = null,
    val startAppInitialized: Boolean = false,
    val startAppLoaded: Boolean = false,
    val startAppLastError: String? = null,
    val isTestMode: Boolean = false,
    val totalImpressions: Int = 0
)

object AdManager {
    private const val TAG = "AdManager"

    // AdMob IDs
    const val ADMOB_APP_ID = "ca-app-pub-5956721462213427~1020362142"
    const val ADMOB_PROD_INTERSTITIAL_ID = "ca-app-pub-5956721462213427/4805537794"
    const val ADMOB_TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    // StartApp IDs
    const val STARTAPP_APP_ID = "209711096"

    // Minimum cooldown time between interstitial ads (45 seconds) to prevent spamming
    private const val AD_COOLDOWN_MS = 45_000L
    private var lastAdShowTimeMs = 0L

    private var admobInterstitial: InterstitialAd? = null
    private var isAdmobLoading = false

    private var startAppInterstitial: StartAppAd? = null
    private var isStartAppLoading = false

    // Real production earning ads enabled
    var useTestAds: Boolean = false

    private val _adStatus = MutableStateFlow(AdStatus(isTestMode = useTestAds))
    val adStatus: StateFlow<AdStatus> = _adStatus.asStateFlow()

    fun initialize(context: Context) {
        val appContext = context.applicationContext

        // 1. Initialize AdMob asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(appContext) { initStatus ->
                    Log.d(TAG, "AdMob MobileAds initialized: ${initStatus.adapterStatusMap.keys}")
                    _adStatus.value = _adStatus.value.copy(admobInitialized = true)
                    loadAdmobInterstitial(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AdMob: ${e.message}", e)
                _adStatus.value = _adStatus.value.copy(admobLastError = e.localizedMessage)
            }
        }

        // 2. Initialize StartApp SDK
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize StartApp with returnAds=false to prevent unwanted automatic ads
                StartAppSDK.init(appContext, STARTAPP_APP_ID, false)
                StartAppAd.disableSplash()
                Log.d(TAG, "StartApp SDK initialized with App ID: $STARTAPP_APP_ID")
                _adStatus.value = _adStatus.value.copy(startAppInitialized = true)
                loadStartAppInterstitial(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize StartApp: ${e.message}", e)
                _adStatus.value = _adStatus.value.copy(startAppLastError = e.localizedMessage)
            }
        }
    }

    /**
     * Loads AdMob interstitial asynchronously
     */
    fun loadAdmobInterstitial(context: Context) {
        if (admobInterstitial != null || isAdmobLoading) return
        isAdmobLoading = true

        val adUnitId = if (useTestAds) ADMOB_TEST_INTERSTITIAL_ID else ADMOB_PROD_INTERSTITIAL_ID
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "AdMob Interstitial loaded successfully")
                    admobInterstitial = interstitialAd
                    isAdmobLoading = false
                    _adStatus.value = _adStatus.value.copy(
                        admobLoaded = true,
                        admobLastError = null
                    )

                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "AdMob Interstitial dismissed")
                            admobInterstitial = null
                            _adStatus.value = _adStatus.value.copy(admobLoaded = false)
                            // Reload for next natural transition
                            loadAdmobInterstitial(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "AdMob failed to show: ${adError.message}")
                            admobInterstitial = null
                            _adStatus.value = _adStatus.value.copy(
                                admobLoaded = false,
                                admobLastError = adError.message
                            )
                            loadAdmobInterstitial(context)
                        }

                        override fun onAdShowedFullScreenContent() {
                            lastAdShowTimeMs = System.currentTimeMillis()
                            _adStatus.value = _adStatus.value.copy(
                                totalImpressions = _adStatus.value.totalImpressions + 1
                            )
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "AdMob failed to load: ${loadAdError.message}")
                    admobInterstitial = null
                    isAdmobLoading = false
                    _adStatus.value = _adStatus.value.copy(
                        admobLoaded = false,
                        admobLastError = loadAdError.message
                    )
                }
            }
        )
    }

    /**
     * Loads StartApp interstitial asynchronously
     */
    fun loadStartAppInterstitial(context: Context) {
        if (startAppInterstitial != null || isStartAppLoading) return
        isStartAppLoading = true

        try {
            val ad = StartAppAd(context.applicationContext)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(adItem: Ad) {
                    Log.d(TAG, "StartApp Interstitial loaded successfully")
                    startAppInterstitial = ad
                    isStartAppLoading = false
                    _adStatus.value = _adStatus.value.copy(
                        startAppLoaded = true,
                        startAppLastError = null
                    )
                }

                override fun onFailedToReceiveAd(adItem: Ad?) {
                    Log.w(TAG, "StartApp failed to load ad: ${adItem?.errorMessage}")
                    startAppInterstitial = null
                    isStartAppLoading = false
                    _adStatus.value = _adStatus.value.copy(
                        startAppLoaded = false,
                        startAppLastError = adItem?.errorMessage ?: "Failed to receive StartApp ad"
                    )
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "StartApp exception while loading ad: ${e.message}")
            isStartAppLoading = false
            _adStatus.value = _adStatus.value.copy(startAppLastError = e.localizedMessage)
        }
    }

    /**
     * Shows an interstitial ad at a natural transition point if ready and not throttled.
     * Guaranteed to never crash or block the caller if ads are not ready.
     * Returns true if an ad was displayed, false otherwise.
     */
    fun showInterstitialIfReady(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAdShowTimeMs < AD_COOLDOWN_MS) {
            Log.d(TAG, "Ad skipped due to cooldown throttle")
            onAdClosed()
            return false
        }

        // 1. Try AdMob first
        val adMob = admobInterstitial
        if (adMob != null) {
            adMob.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    admobInterstitial = null
                    _adStatus.value = _adStatus.value.copy(admobLoaded = false)
                    loadAdmobInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    admobInterstitial = null
                    _adStatus.value = _adStatus.value.copy(
                        admobLoaded = false,
                        admobLastError = adError.message
                    )
                    loadAdmobInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    lastAdShowTimeMs = System.currentTimeMillis()
                    _adStatus.value = _adStatus.value.copy(
                        totalImpressions = _adStatus.value.totalImpressions + 1
                    )
                }
            }
            adMob.show(activity)
            return true
        }

        // 2. Fallback to StartApp if AdMob wasn't ready
        val startApp = startAppInterstitial
        if (startApp != null) {
            val shown = startApp.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    startAppInterstitial = null
                    _adStatus.value = _adStatus.value.copy(startAppLoaded = false)
                    loadStartAppInterstitial(activity)
                    onAdClosed()
                }

                override fun adDisplayed(ad: Ad?) {
                    lastAdShowTimeMs = System.currentTimeMillis()
                    _adStatus.value = _adStatus.value.copy(
                        totalImpressions = _adStatus.value.totalImpressions + 1
                    )
                }

                override fun adClicked(ad: Ad?) {}
                override fun adNotDisplayed(ad: Ad?) {
                    startAppInterstitial = null
                    _adStatus.value = _adStatus.value.copy(startAppLoaded = false)
                    loadStartAppInterstitial(activity)
                    onAdClosed()
                }
            })

            if (shown) {
                return true
            }
        }

        // Neither ad was ready - proceed directly without delaying user
        onAdClosed()
        return false
    }

    fun setTestMode(enabled: Boolean, context: Context) {
        useTestAds = enabled
        _adStatus.value = _adStatus.value.copy(isTestMode = enabled)
        admobInterstitial = null
        loadAdmobInterstitial(context)
    }
}
