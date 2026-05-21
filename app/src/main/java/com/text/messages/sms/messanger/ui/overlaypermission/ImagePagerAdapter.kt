package com.text.messages.sms.messanger.ui.overlaypermission

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ItemOverlayImageBinding

data class OverlayPermissionSlide(
    val stepBadge: String,
    val title: String,
    val subtitle: String?,
    val imageRes: Int,
    val showPermissionRow: Boolean
)

class ImagePagerAdapter(
    private val slides: List<OverlayPermissionSlide>
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    private var selectedPosition: Int = 0

    fun setSelectedPosition(position: Int) {
        val previous = selectedPosition
        selectedPosition = position
        if (previous in slides.indices) {
            notifyItemChanged(previous)
        }
        if (position in slides.indices) {
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemOverlayImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(slides[position], position == selectedPosition)
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        holder.stopAnimation()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = slides.size

    class ImageViewHolder(
        private val binding: ItemOverlayImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val handler = Handler(Looper.getMainLooper())
        private var animationRunnable: Runnable? = null
        private var toggleRunnable: Runnable? = null

        fun bind(slide: OverlayPermissionSlide, shouldAnimate: Boolean) {
            stopAnimation()

            binding.textStepBadge.text = slide.stepBadge
            binding.textSlideTitle.text = slide.title
            binding.textSlideSubtitle.text = slide.subtitle.orEmpty()
            binding.textSlideSubtitle.isGone = slide.subtitle.isNullOrEmpty()
            binding.imageSlide.setImageResource(slide.imageRes)

            binding.cardPermissionRow.isVisible = slide.showPermissionRow
            binding.imageHand.isVisible = slide.showPermissionRow
            binding.imageToggle.setImageResource(R.drawable.toff)
            binding.imageHand.translationX = toggleStartTranslation()
            binding.imageHand.translationY = dp(24f)

            if (slide.showPermissionRow && shouldAnimate) {
                startAnimation()
            }
        }

        private fun startAnimation() {
            binding.imageHand.post {
                val startPosition = toggleStartTranslation()
                val endPosition = 0f

                var playToggleGesture: (() -> Unit)? = null
                playToggleGesture = {
                    binding.imageHand.translationX = startPosition
                    binding.imageToggle.setImageResource(R.drawable.toff)

                    toggleRunnable?.let(handler::removeCallbacks)
                    toggleRunnable = Runnable {
                        binding.imageToggle.setImageResource(R.drawable.ton)
                    }
                    handler.postDelayed(toggleRunnable!!, 350L)

                    binding.imageHand.animate()
                        .translationX(endPosition)
                        .setDuration(700L)
                        .withEndAction {
                            scheduleNext(playToggleGesture!!, 900L)
                        }
                        .start()
                }

                scheduleNext(playToggleGesture, 450L)
            }
        }

        private fun scheduleNext(action: () -> Unit, delayMillis: Long) {
            animationRunnable = Runnable { action() }
            handler.postDelayed(animationRunnable!!, delayMillis)
        }

        fun stopAnimation() {
            animationRunnable?.let(handler::removeCallbacks)
            animationRunnable = null
            toggleRunnable?.let(handler::removeCallbacks)
            toggleRunnable = null
            binding.imageHand.animate().cancel()
        }

        private fun toggleStartTranslation(): Float {
            return dp(-36f)
        }

        private fun dp(value: Float): Float {
            return value * binding.root.resources.displayMetrics.density
        }
    }
}
