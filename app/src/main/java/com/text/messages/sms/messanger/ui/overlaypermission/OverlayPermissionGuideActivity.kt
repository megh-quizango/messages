package com.text.messages.sms.messanger.ui.overlaypermission

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.BottomSheetOverlayGuideBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity

class OverlayPermissionGuideActivity : BaseActivity() {

    private lateinit var binding: BottomSheetOverlayGuideBinding
    private val handler = Handler(Looper.getMainLooper())

    private var isDismissing = false
    private var loopRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BottomSheetOverlayGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupDismissActions()
        playSheetEntrance()
        startToggleAnimationLoop()
    }

    override fun onBackPressed() {
        dismissGuide()
    }

    override fun onDestroy() {
        stopToggleAnimation()
        super.onDestroy()
    }

    private fun applyInsets() {
        val initialBottomPadding = binding.sheetContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.sheetContainer.setPadding(
                binding.sheetContainer.paddingLeft,
                binding.sheetContainer.paddingTop,
                binding.sheetContainer.paddingRight,
                initialBottomPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun setupDismissActions() {
        binding.rootOverlayGuide.setOnClickListener { dismissGuide() }
        binding.sheetContainer.setOnClickListener { dismissGuide() }
        binding.iconShield.setOnClickListener { dismissGuide() }
        binding.textSafeSecure.setOnClickListener { dismissGuide() }
        binding.iconApp.setOnClickListener { dismissGuide() }
        binding.textAppName.setOnClickListener { dismissGuide() }
        binding.textInstruction.setOnClickListener { dismissGuide() }
        binding.toggleAnimationContainer.setOnClickListener { dismissGuide() }
    }

    private fun playSheetEntrance() {
        binding.sheetContainer.post {
            binding.sheetContainer.translationY = dp(64f)
            binding.sheetContainer.alpha = 0f
            binding.sheetContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240L)
                .start()
        }
    }

    private fun startToggleAnimationLoop() {
        binding.toggleAnimationContainer.post {
            val startX = dp(8f)
            val endX = (
                binding.imageToggle.x +
                    binding.imageToggle.width -
                    binding.imageHand.width -
                    dp(2f)
                ).coerceAtLeast(startX)

            lateinit var animateToEnd: () -> Unit
            lateinit var animateToStart: () -> Unit

            animateToEnd = {
                binding.imageToggle.setImageResource(R.drawable.toff)
                binding.imageHand.translationX = startX
                binding.imageHand.animate()
                    .translationX(endX)
                    .setDuration(850L)
                    .withEndAction {
                        binding.imageToggle.setImageResource(R.drawable.ton)
                        scheduleNext(animateToStart, 360L)
                    }
                    .start()
            }

            animateToStart = {
                binding.imageHand.animate()
                    .translationX(startX)
                    .setDuration(850L)
                    .withEndAction {
                        binding.imageToggle.setImageResource(R.drawable.toff)
                        scheduleNext(animateToEnd, 360L)
                    }
                    .start()
            }

            binding.imageHand.translationX = startX
            binding.imageToggle.setImageResource(R.drawable.toff)
            scheduleNext(animateToEnd, 220L)
        }
    }

    private fun scheduleNext(action: () -> Unit, delayMillis: Long) {
        loopRunnable?.let(handler::removeCallbacks)
        loopRunnable = Runnable { action() }
        handler.postDelayed(loopRunnable!!, delayMillis)
    }

    private fun stopToggleAnimation() {
        loopRunnable?.let(handler::removeCallbacks)
        loopRunnable = null
        binding.imageHand.animate().cancel()
        binding.sheetContainer.animate().cancel()
        binding.rootOverlayGuide.animate().cancel()
    }

    private fun dismissGuide() {
        if (isDismissing) {
            return
        }
        isDismissing = true
        stopToggleAnimation()
        binding.rootOverlayGuide.animate()
            .alpha(0f)
            .setDuration(180L)
            .start()
        binding.sheetContainer.animate()
            .translationY(binding.sheetContainer.height.toFloat())
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
