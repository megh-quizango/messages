package com.text.messages.sms.messanger.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdView
import kotlin.math.roundToInt
import com.text.messages.sms.messanger.R

object AdLoadingShimmerHelper {

    private const val BANNER_SHIMMER_TAG = "banner_ad_loading_shimmer"
    private const val BANNER_HOST_TAG = "banner_ad_host_container"
    private const val NATIVE_SHIMMER_TAG = "native_ad_loading_shimmer"

    fun showBannerLoading(adView: AdView) {
        setBannerHostVisibility(adView, View.VISIBLE)
        val shimmer = ensureBannerShimmer(adView) ?: return
        val bannerHeight = getBannerHeightPx(adView)
        updateViewHeight(adView, bannerHeight)
        updateViewHeight(shimmer, bannerHeight)
        adView.visibility = View.INVISIBLE
        shimmer.visibility = View.VISIBLE
        restartShimmer(shimmer)
    }

    fun showBannerContent(adView: AdView) {
        setBannerHostVisibility(adView, View.VISIBLE)
        val bannerHeight = getBannerHeightPx(adView)
        updateViewHeight(adView, bannerHeight)
        findBannerShimmer(adView)?.let(::stopAndHideShimmer)
        adView.visibility = View.VISIBLE
        adView.requestLayout()
    }

    fun hideBanner(adView: AdView) {
        findBannerShimmer(adView)?.let(::stopAndHideShimmer)
        adView.visibility = View.GONE
        setBannerHostVisibility(adView, View.GONE)
    }

    fun showNativeLoading(container: ViewGroup, adView: View? = null) {
        val shimmer = ensureNativeShimmer(container) ?: return
        container.visibility = View.VISIBLE
        adView?.visibility = View.GONE
        shimmer.visibility = View.VISIBLE
        restartShimmer(shimmer)
    }

    fun showNativeContent(container: ViewGroup, adView: View? = null) {
        findNativeShimmer(container)?.let(::stopAndHideShimmer)
        container.visibility = View.VISIBLE
        adView?.visibility = View.VISIBLE
    }

    fun hideNative(container: ViewGroup, adView: View? = null) {
        findNativeShimmer(container)?.let(::stopAndHideShimmer)
        adView?.visibility = View.GONE
        container.visibility = View.GONE
    }

    private fun ensureBannerShimmer(adView: AdView): ShimmerFrameLayout? {
        val parent = adView.parent as? ViewGroup ?: return null
        val existing = findBannerShimmer(adView)
        if (existing != null) {
            existing.layoutParams = copyLayoutParams(adView.layoutParams)
            return existing
        }

        val shimmer = LayoutInflater.from(adView.context)
            .inflate(R.layout.layout_banner_ad_shimmer, parent, false) as ShimmerFrameLayout
        shimmer.tag = BANNER_SHIMMER_TAG
        shimmer.layoutParams = copyLayoutParams(adView.layoutParams)

        val insertIndex = parent.indexOfChild(adView) + 1
        parent.addView(shimmer, insertIndex)
        return shimmer
    }

    private fun findBannerShimmer(adView: AdView): ShimmerFrameLayout? {
        val parent = adView.parent as? ViewGroup ?: return null
        return parent.children().firstOrNull { it.tag == BANNER_SHIMMER_TAG } as? ShimmerFrameLayout
    }

    private fun setBannerHostVisibility(adView: AdView, visibility: Int) {
        val parent = adView.parent as? ViewGroup ?: return
        if (parent.tag == BANNER_HOST_TAG && parent.visibility != visibility) {
            parent.visibility = visibility
        }
    }

    private fun ensureNativeShimmer(container: ViewGroup): ShimmerFrameLayout? {
        val existing = findNativeShimmer(container)
        if (existing != null) {
            return existing
        }

        val shimmer = LayoutInflater.from(container.context)
            .inflate(R.layout.layout_native_ad_shimmer, container, false) as ShimmerFrameLayout
        shimmer.tag = NATIVE_SHIMMER_TAG

        if (container is FrameLayout) {
            container.addView(
                shimmer,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            container.addView(
                shimmer,
                0,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return shimmer
    }

    private fun findNativeShimmer(container: ViewGroup): ShimmerFrameLayout? {
        return container.children().firstOrNull { it.tag == NATIVE_SHIMMER_TAG } as? ShimmerFrameLayout
    }

    private fun restartShimmer(shimmer: ShimmerFrameLayout) {
        shimmer.stopShimmer()
        shimmer.post {
            if (shimmer.visibility == View.VISIBLE && shimmer.isAttachedToWindow) {
                shimmer.startShimmer()
            }
        }
    }

    private fun stopAndHideShimmer(shimmer: ShimmerFrameLayout) {
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
    }

    private fun updateViewHeight(view: View, heightPx: Int) {
        val params = view.layoutParams ?: return
        if (params.height != heightPx) {
            params.height = heightPx
            view.layoutParams = params
        }
    }

    private fun getBannerHeightPx(adView: AdView): Int {
        val context = adView.context
        return adView.adSize?.getHeightInPixels(context)
            ?: (50f * context.resources.displayMetrics.density).roundToInt()
    }

    private fun copyLayoutParams(layoutParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return when (layoutParams) {
            is ConstraintLayout.LayoutParams -> ConstraintLayout.LayoutParams(layoutParams)
            is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(layoutParams)
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(layoutParams)
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(layoutParams)
            else -> ViewGroup.LayoutParams(layoutParams)
        }
    }

    private fun ViewGroup.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            yield(getChildAt(index))
        }
    }
}
