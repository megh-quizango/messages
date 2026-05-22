package com.text.messages.sms.messanger.util

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.LinearInterpolator

object ButtonShimmerAnimator {

    private const val DURATION_MS = 1450L
    private const val OFFSET_MULTIPLIER = 1.6f

    fun start(stripView: View, existingAnimator: ObjectAnimator?): ObjectAnimator? {
        existingAnimator?.cancel()

        val parentView = stripView.parent as? View ?: return null
        val parentWidth = parentView.width
        val stripWidth = stripView.width

        if (parentWidth == 0 || stripWidth == 0) {
            return null
        }

        val startX = -stripWidth * OFFSET_MULTIPLIER
        val endX = parentWidth + stripWidth * OFFSET_MULTIPLIER
        stripView.translationX = startX

        return ObjectAnimator.ofFloat(stripView, View.TRANSLATION_X, startX, endX).apply {
            duration = DURATION_MS
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun stop(stripView: View, animator: ObjectAnimator?) {
        animator?.cancel()
        stripView.translationX = -stripView.width * OFFSET_MULTIPLIER
    }
}
