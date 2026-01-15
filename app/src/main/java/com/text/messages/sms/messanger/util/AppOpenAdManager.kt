package com.text.messages.sms.messanger.util

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.material.button.MaterialButton
import com.text.messages.sms.messanger.R

@SuppressLint("StaticFieldLeak")
object AppOpenAdManager {

    private const val TAG = "NativeVideoAdManager"
    @SuppressLint("StaticFieldLeak")
    private const val NATIVE_VIDEO_AD_UNIT_ID = "ca-app-pub-3940256099942544/1044960115"
    private const val VIDEO_DURATION_MS = 30000L // Estimated video duration
    private const val NON_VIDEO_DURATION_MS = 5000L // Duration for non-video ads

    private var currentNativeAd: NativeAd? = null
    private var nextNativeAd: NativeAd? = null
    private var currentDialog: Dialog? = null
    private var previousDialog: Dialog? = null
    private var isShowingAd = false
    private val handler = Handler(Looper.getMainLooper())
    private var onCloseCallback: (() -> Unit)? = null
    private var progressAnimator: ObjectAnimator? = null
    private var progressBarSegment1: ProgressBar? = null
    private var progressBarSegment2: ProgressBar? = null
    private const val CLOSE_BUTTON_DELAY_MS = 2000L // 2 seconds delay for close button

    /**
     * Shows Native Video Ads based on build type:
     * - DEBUG: Shows TWO video ads back-to-back
     * - RELEASE: Shows ONE video ad only
     *
     * @param activity The activity context to show the ad
     * @param onFinish Callback invoked when all ads are finished or if any ad fails
     */
    fun showDoubleAppOpenIfDebug(activity: Activity, onFinish: () -> Unit) {
        val isDebug = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val adsToShow = if (isDebug) 2 else 1
        Log.d(TAG, "Starting native video ad sequence. Build type: ${if (isDebug) "DEBUG" else "RELEASE"}, ads to show: $adsToShow")

        showAdSequence(activity, adsToShow, 0, onFinish)
    }

    /**
     * Recursively shows ads in sequence
     */
    private fun showAdSequence(
        activity: Activity,
        totalAds: Int,
        currentAdIndex: Int,
        onFinish: () -> Unit
    ) {
        if (currentAdIndex >= totalAds) {
            Log.d(TAG, "Ad sequence completed. Shown $totalAds ad(s)")
            cleanup()
            onFinish()
            return
        }

        val adNumber = currentAdIndex + 1
        val isLastAd = adNumber == totalAds
        Log.d(TAG, "Loading native video ad #$adNumber of $totalAds (isLastAd: $isLastAd)")

        loadAndShowNativeVideoAd(
            activity = activity,
            adNumber = adNumber,
            totalAds = totalAds,
            isLastAd = isLastAd,
            onAdComplete = {
                Log.d(TAG, "Ad #$adNumber completed successfully")
                // Show next ad
                showAdSequence(activity, totalAds, currentAdIndex + 1, onFinish)
            },
            onAdFailed = {
                Log.w(TAG, "Ad #$adNumber failed. Finishing sequence.")
                cleanup()
                onFinish()
            },
            onFinalAdClosed = {
                Log.d(TAG, "Final ad closed by user")
                cleanup()
                onFinish()
            }
        )
    }

    /**
     * Loads and shows a Native Video Ad in full-screen dialog
     */
    private fun loadAndShowNativeVideoAd(
        activity: Activity,
        adNumber: Int,
        totalAds: Int,
        isLastAd: Boolean,
        onAdComplete: () -> Unit,
        onAdFailed: () -> Unit,
        onFinalAdClosed: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing/destroyed. Cannot show ad #$adNumber")
            onAdFailed()
            return
        }

        // Check if we have a preloaded ad ready
        val preloadedAd = nextNativeAd
        if (preloadedAd != null && adNumber > 1) {
            Log.d(TAG, "Using preloaded ad for ad #$adNumber")
            nextNativeAd = null
            currentNativeAd = preloadedAd
            showNativeAdDialog(activity, preloadedAd, adNumber, totalAds, isLastAd, onAdComplete, onAdFailed, onFinalAdClosed)
            return
        }

        // Load new ad
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(false)
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
            .build()

        val adLoader = AdLoader.Builder(activity, NATIVE_VIDEO_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native ad #$adNumber loaded successfully")

                if (activity.isFinishing || activity.isDestroyed) {
                    Log.w(TAG, "Activity became invalid after ad load. Destroying ad.")
                    nativeAd.destroy()
                    onAdFailed()
                    return@forNativeAd
                }

                currentNativeAd = nativeAd
                showNativeAdDialog(activity, nativeAd, adNumber, totalAds, isLastAd, onAdComplete, onAdFailed, onFinalAdClosed)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Native ad #$adNumber failed to load: ${loadAdError.message}")
                    onAdFailed()
                }
            })
            .withNativeAdOptions(adOptions)
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Shows native ad in a full-screen dialog
     */
    private fun showNativeAdDialog(
        activity: Activity,
        nativeAd: NativeAd,
        adNumber: Int,
        totalAds: Int,
        isLastAd: Boolean,
        onAdComplete: () -> Unit,
        onAdFailed: () -> Unit,
        onFinalAdClosed: () -> Unit
    ) {
        try {
            isShowingAd = true

            // Cancel any existing progress animation
            progressAnimator?.cancel()
            progressAnimator = null

            // Create new dialog
            val dialog = Dialog(activity, R.style.FullScreenAdDialogTheme)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            val view = LayoutInflater.from(activity).inflate(
                R.layout.layout_fullscreen_native_video_ad, null
            )

            val nativeAdView = view.findViewById<NativeAdView>(R.id.nativeAdView)

            // Get segmented progress bar references
            val segment1 = view.findViewById<ProgressBar>(R.id.progressBarSegment1)
            val segment2 = view.findViewById<ProgressBar>(R.id.progressBarSegment2)
            val progressGap = view.findViewById<View>(R.id.progressBarGap)

            // Store references for animation
            progressBarSegment1 = segment1
            progressBarSegment2 = segment2

            // Setup progress bar visibility based on total ads
            if (totalAds == 1) {
                // Single ad - hide second segment and gap
                segment2.visibility = View.GONE
                progressGap.visibility = View.GONE
            } else {
                // Multiple ads - show both segments
                segment2.visibility = View.VISIBLE
                progressGap.visibility = View.VISIBLE

                // If this is the second ad, keep first segment filled
                if (adNumber == 2) {
                    segment1.progress = 100
                }
            }

            // Setup close button - hidden initially, shown after delay for last ad
            val closeButton = view.findViewById<ImageView>(R.id.adCloseButton)
            closeButton.visibility = View.GONE
            onCloseCallback = onFinalAdClosed
            closeButton.setOnClickListener {
                Log.d(TAG, "Close button clicked on final ad")
                handler.removeCallbacksAndMessages(null)
                progressAnimator?.cancel()
                dismissAllDialogs()
                onCloseCallback?.invoke()
                onCloseCallback = null
            }

            // Show close button after delay only for the last ad
            if (isLastAd) {
                handler.postDelayed({
                    if (isShowingAd && currentDialog?.isShowing == true) {
                        closeButton.visibility = View.VISIBLE
                        Log.d(TAG, "Close button now visible after ${CLOSE_BUTTON_DELAY_MS}ms delay")
                    }
                }, CLOSE_BUTTON_DELAY_MS)
            }

            populateNativeAdView(nativeAd, nativeAdView, adNumber, totalAds)

            dialog.setContentView(view)
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Handle system bars properly
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setDecorFitsSystemWindows(false)
                    insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
                }
            }

            // Apply window insets to the overlays
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val topOverlay = v.findViewById<LinearLayout>(R.id.topOverlay)
                val bottomOverlay = v.findViewById<LinearLayout>(R.id.bottomOverlay)
                val progressContainer = v.findViewById<LinearLayout>(R.id.progressBarContainer)

                // Adjust progress bar container for status bar
                progressContainer?.let {
                    val params = it.layoutParams as? ViewGroup.MarginLayoutParams
                    params?.topMargin = systemBars.top
                    it.layoutParams = params
                }

                topOverlay?.setPadding(
                    16.dpToPx(activity),
                    systemBars.top + 8.dpToPx(activity),
                    16.dpToPx(activity),
                    24.dpToPx(activity)
                )
                bottomOverlay?.setPadding(
                    16.dpToPx(activity),
                    24.dpToPx(activity),
                    16.dpToPx(activity),
                    systemBars.bottom + 16.dpToPx(activity)
                )
                insets
            }

            // Show new dialog first, then dismiss previous (no gap)
            dialog.show()

            // Now dismiss the previous dialog after new one is visible
            handler.postDelayed({
                previousDialog?.dismiss()
                previousDialog = null
            }, 50)

            // Update dialog references
            previousDialog = currentDialog
            currentDialog = dialog

            Log.d(TAG, "Showing native video ad #$adNumber")

            // Start preloading next ad while current one is showing (if not last)
            if (!isLastAd) {
                preloadNextAd(activity, adNumber + 1)
            }

            // Setup video completion detection and progress bar
            val hasVideo = nativeAd.mediaContent?.hasVideoContent() == true
            val duration = if (hasVideo) VIDEO_DURATION_MS else NON_VIDEO_DURATION_MS

            // Determine which progress bar segment to animate
            val currentSegment = if (adNumber == 1) segment1 else segment2

            // Start progress bar animation on the current segment
            startProgressAnimation(currentSegment, duration)

            if (!isLastAd) {
                setupVideoCompletionListener(nativeAd, adNumber, currentSegment, onAdComplete)
            } else {
                setupVideoLogging(nativeAd, adNumber, currentSegment)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing native ad dialog: ${e.message}", e)
            isShowingAd = false
            onAdFailed()
        }
    }

    /**
     * Starts the progress bar animation
     */
    private fun startProgressAnimation(progressBar: ProgressBar, durationMs: Long) {
        progressAnimator?.cancel()
        progressBar.progress = 0

        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            start()
        }

        Log.d(TAG, "Started progress animation for ${durationMs}ms")
    }

    /**
     * Completes the progress bar animation immediately
     */
    private fun completeProgressAnimation(progressBar: ProgressBar?) {
        progressAnimator?.cancel()
        progressBar?.progress = 100
    }

    /**
     * Preloads the next ad for seamless transition
     */
    private fun preloadNextAd(activity: Activity, nextAdNumber: Int) {
        Log.d(TAG, "Preloading next ad #$nextAdNumber")

        val videoOptions = VideoOptions.Builder()
            .setStartMuted(false)
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
            .build()

        val adLoader = AdLoader.Builder(activity, NATIVE_VIDEO_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Next ad #$nextAdNumber preloaded successfully")
                nextNativeAd = nativeAd
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Failed to preload next ad: ${loadAdError.message}")
                    nextNativeAd = null
                }
            })
            .withNativeAdOptions(adOptions)
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Sets up video completion listener to auto-advance to next ad
     */
    private fun setupVideoCompletionListener(
        nativeAd: NativeAd,
        adNumber: Int,
        progressBar: ProgressBar,
        onAdComplete: () -> Unit
    ) {
        val mediaContent = nativeAd.mediaContent
        val hasVideo = mediaContent?.hasVideoContent() == true

        Log.d(TAG, "Ad #$adNumber has video content: $hasVideo")

        if (hasVideo) {
            val videoController = mediaContent?.videoController
            videoController?.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                override fun onVideoEnd() {
                    Log.d(TAG, "Video #$adNumber ended - auto-advancing")
                    handler.post {
                        completeProgressAnimation(progressBar)
                        onAdComplete()
                    }
                }

                override fun onVideoStart() {
                    Log.d(TAG, "Video #$adNumber started playing")
                }

                override fun onVideoPlay() {
                    Log.d(TAG, "Video #$adNumber is playing")
                }

                override fun onVideoPause() {
                    Log.d(TAG, "Video #$adNumber paused")
                }

                override fun onVideoMute(isMuted: Boolean) {
                    Log.d(TAG, "Video #$adNumber mute state: $isMuted")
                }
            }

            // Fallback timeout in case video callback doesn't fire
            handler.postDelayed({
                if (isShowingAd && currentDialog?.isShowing == true) {
                    Log.d(TAG, "Video #$adNumber timeout reached, auto-advancing")
                    completeProgressAnimation(progressBar)
                    onAdComplete()
                }
            }, VIDEO_DURATION_MS)

        } else {
            // No video content - auto-advance after duration
            Log.d(TAG, "Ad #$adNumber has no video, showing for ${NON_VIDEO_DURATION_MS}ms")
            handler.postDelayed({
                if (isShowingAd && currentDialog?.isShowing == true) {
                    completeProgressAnimation(progressBar)
                    onAdComplete()
                }
            }, NON_VIDEO_DURATION_MS)
        }
    }

    /**
     * Sets up video logging for the last ad (no auto-advance)
     */
    private fun setupVideoLogging(nativeAd: NativeAd, adNumber: Int, progressBar: ProgressBar) {
        val mediaContent = nativeAd.mediaContent
        val hasVideo = mediaContent?.hasVideoContent() == true

        Log.d(TAG, "Last ad #$adNumber has video content: $hasVideo (requires manual close)")

        if (hasVideo) {
            val videoController = mediaContent?.videoController
            videoController?.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                override fun onVideoEnd() {
                    Log.d(TAG, "Video #$adNumber ended - waiting for user to close")
                    handler.post {
                        completeProgressAnimation(progressBar)
                    }
                }

                override fun onVideoStart() {
                    Log.d(TAG, "Video #$adNumber started playing")
                }

                override fun onVideoPlay() {
                    Log.d(TAG, "Video #$adNumber is playing")
                }

                override fun onVideoPause() {
                    Log.d(TAG, "Video #$adNumber paused")
                }

                override fun onVideoMute(isMuted: Boolean) {
                    Log.d(TAG, "Video #$adNumber mute state: $isMuted")
                }
            }
        }
    }

    /**
     * Populates the native ad view with ad content
     */
    private fun populateNativeAdView(
        nativeAd: NativeAd,
        adView: NativeAdView,
        adNumber: Int,
        totalAds: Int
    ) {
        // Media View (Video)
        val mediaView = adView.findViewById<MediaView>(R.id.adMediaView)
        adView.mediaView = mediaView
        mediaView.mediaContent = nativeAd.mediaContent

        // Ad Counter
        val adCounter = adView.findViewById<TextView>(R.id.adCounter)
        adCounter.text = "$adNumber/$totalAds"

        // Headline
        val headlineView = adView.findViewById<TextView>(R.id.adHeadline)
        adView.headlineView = headlineView
        headlineView.text = nativeAd.headline ?: "Sponsored"

        // Body
        val bodyView = adView.findViewById<TextView>(R.id.adBody)
        adView.bodyView = bodyView
        bodyView.text = nativeAd.body ?: ""
        bodyView.visibility = if (nativeAd.body != null) View.VISIBLE else View.GONE

        // App Icon
        val iconView = adView.findViewById<ImageView>(R.id.adAppIcon)
        adView.iconView = iconView
        if (nativeAd.icon != null) {
            iconView.setImageDrawable(nativeAd.icon?.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        // Call to Action
        val ctaButton = adView.findViewById<MaterialButton>(R.id.adCallToAction)
        adView.callToActionView = ctaButton
        ctaButton.text = nativeAd.callToAction ?: "Learn More"

        // Register the native ad view
        adView.setNativeAd(nativeAd)
    }

    /**
     * Dismisses all dialogs
     */
    private fun dismissAllDialogs() {
        handler.removeCallbacksAndMessages(null)
        progressAnimator?.cancel()
        progressAnimator = null
        progressBarSegment1 = null
        progressBarSegment2 = null
        isShowingAd = false
        previousDialog?.dismiss()
        previousDialog = null
        currentDialog?.dismiss()
        currentDialog = null
    }

    /**
     * Cleans up all resources
     */
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        progressAnimator?.cancel()
        progressAnimator = null
        progressBarSegment1 = null
        progressBarSegment2 = null
        isShowingAd = false
        onCloseCallback = null
        previousDialog?.dismiss()
        previousDialog = null
        currentDialog?.dismiss()
        currentDialog = null
        currentNativeAd?.destroy()
        currentNativeAd = null
        nextNativeAd?.destroy()
        nextNativeAd = null
    }

    /**
     * Resets the manager state
     */
    fun reset() {
        cleanup()
    }

    /**
     * Extension function to convert dp to pixels
     */
    private fun Int.dpToPx(activity: Activity): Int {
        return (this * activity.resources.displayMetrics.density).toInt()
    }
}
