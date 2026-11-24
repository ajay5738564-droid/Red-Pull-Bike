package com.redpullbike.game

import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.delay

object UnityAdsController {
    const val GAME_ID = "800450606"
    const val REWARDED = "BP_Rewarded_Android"
    const val INTERSTITIAL = "BP_Interstitial_Android"
    const val BANNER = "BP_Banner_Android"

    @Volatile var initialized = false
        private set

    fun initialize(activity: Activity) {
        if (initialized) return
        UnityAds.initialize(
            activity.applicationContext,
            GAME_ID,
            true,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    initialized = true
                    Log.d("ArrowEscapeAds", "Unity Ads initialized in TEST mode")
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    initialized = false
                    Log.e("ArrowEscapeAds", "Unity Ads init failed: $error $message")
                }
            }
        )
    }

    fun showRewarded(activity: Activity, placement: String, onReward: () -> Unit, onUnavailable: () -> Unit) {
        if (!initialized) {
            onUnavailable()
            return
        }
        UnityAds.load(placement, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                activity.runOnUiThread {
                    UnityAds.show(activity, placement, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                        override fun onUnityAdsShowStart(placementId: String) {}
                        override fun onUnityAdsShowClick(placementId: String) {}
                        override fun onUnityAdsShowComplete(
                            placementId: String,
                            state: UnityAds.UnityAdsShowCompletionState
                        ) {
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                activity.runOnUiThread { onReward() }
                            }
                        }
                        override fun onUnityAdsShowFailure(
                            placementId: String,
                            error: UnityAds.UnityAdsShowError,
                            message: String
                        ) {
                            Log.e("ArrowEscapeAds", "Rewarded show failed: $error $message")
                            activity.runOnUiThread { onUnavailable() }
                        }
                    })
                }
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError,
                message: String
            ) {
                Log.e("ArrowEscapeAds", "Rewarded load failed: $error $message")
                activity.runOnUiThread { onUnavailable() }
            }
        })
    }
}

@Composable
fun UnityBottomBanner() {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (!UnityAdsController.initialized && attempts < 80) {
            delay(250)
            attempts++
        }
        ready = UnityAdsController.initialized
    }

    if (ready) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            factory = { ctx -> FrameLayout(ctx) },
            update = { frame ->
                if (frame.childCount == 0) {
                    val banner = BannerView(
                        context,
                        UnityAdsController.BANNER,
                        UnityBannerSize(320, 50)
                    )
                    banner.setListener(object : BannerView.IListener {
                        override fun onBannerLoaded(bannerAdView: BannerView) {
                            Log.d("ArrowEscapeAds", "Bottom banner loaded")
                        }
                        override fun onBannerFailedToLoad(bannerAdView: BannerView, errorInfo: BannerErrorInfo) {
                            Log.e("ArrowEscapeAds", "Banner load failed: ${errorInfo.errorMessage}")
                        }
                        override fun onBannerClick(bannerAdView: BannerView) {}
                        override fun onBannerLeftApplication(bannerAdView: BannerView) {}
                    })
                    frame.addView(
                        banner,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                    )
                    banner.load()
                }
            }
        )
    }
}
