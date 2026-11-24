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
import com.unity3d.ads.*
import kotlinx.coroutines.delay

object UnityAdsController {
    const val GAME_ID = "800450606"
    const val REWARDED = "BP_Rewarded_Android"
    const val INTERSTITIAL = "BP_Interstitial_Android"
    const val BANNER = "BP_Banner_Android"

    @Volatile var initialized = false
        private set

    @OptIn(UnityAdsExperimental::class)
    fun initialize(activity: Activity) {
        if (UnityAds.isInitialized) {
            initialized = true
            return
        }
        val config = InitializationConfiguration.Builder(GAME_ID)
            .withContext(activity)
            .withTestMode(true)
            .build()
        UnityAds.initialize(config, InitializationListener { error ->
            if (error == null) {
                initialized = true
                Log.d("ArrowEscapeAds", "Unity Ads initialized in TEST mode")
            } else {
                initialized = false
                Log.e("ArrowEscapeAds", "Unity Ads init failed: ${error.message}")
            }
        })
    }

    @OptIn(UnityAdsExperimental::class)
    fun showRewarded(activity: Activity, placement: String, onReward: () -> Unit, onUnavailable: () -> Unit) {
        if (!initialized) {
            onUnavailable()
            return
        }
        val loadConfig = LoadConfiguration.Builder(placement).build()
        RewardedAd.load(loadConfig, RewardedLoadListener { ad, error ->
            if (ad == null) {
                Log.e("ArrowEscapeAds", "Rewarded load failed: ${error?.message}")
                activity.runOnUiThread { onUnavailable() }
                return@RewardedLoadListener
            }
            val showConfig = ShowConfiguration.Builder().build()
            ad.show(showConfig, object : RewardedShowListener {
                override fun onStarted(unityAd: RewardedAd) {}
                override fun onClicked(unityAd: RewardedAd) {}
                override fun onCompleted(unityAd: RewardedAd, state: ShowFinishState) {}
                override fun onFailed(unityAd: RewardedAd, error: UnityAdsError) {
                    Log.e("ArrowEscapeAds", "Rewarded show failed: ${error.message}")
                    activity.runOnUiThread { onUnavailable() }
                }
                override fun onRewarded(unityAd: RewardedAd) {
                    activity.runOnUiThread { onReward() }
                }
            })
        })
    }
}

@OptIn(UnityAdsExperimental::class)
@Composable
fun UnityBottomBanner() {
    val context = LocalContext.current
    var bannerAd by remember { mutableStateOf<BannerAd?>(null) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (!UnityAdsController.initialized && attempts < 80) {
            delay(250)
            attempts++
        }
        if (UnityAdsController.initialized) {
            val config = BannerLoadConfiguration.Builder(
                UnityAdsController.BANNER,
                BannerSize(320, 50)
            ).build()
            BannerAd.load(config, BannerLoadListener { ad, error ->
                if (ad != null) bannerAd = ad
                else Log.e("ArrowEscapeAds", "Banner load failed: ${error?.message}")
            })
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        factory = { FrameLayout(it) },
        update = { frame ->
            val adView = bannerAd?.view
            if (adView != null && adView.parent !== frame) {
                (adView.parent as? ViewGroup)?.removeView(adView)
                frame.removeAllViews()
                frame.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
        }
    )
}
