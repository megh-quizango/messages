package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.text.messages.sms.messanger.R
import de.hdodenhof.circleimageview.CircleImageView

object AvatarHelper {

    private val referenceAvatarColors = intArrayOf(
        Color.parseColor("#FFFF5B55"),
        Color.parseColor("#FFFFC600"),
        Color.parseColor("#FFFF891D"),
        Color.parseColor("#FF00D0EA"),
        Color.parseColor("#FF7C4DFF"),
        Color.parseColor("#FF0075F0"),
        Color.parseColor("#FFBC56FF"),
        Color.parseColor("#FF26BC6D"),
        Color.parseColor("#FFFF54BB")
    )

    fun getColorForIdentifier(identifier: String, context: Context? = null): Int {
        val key = identifier.ifBlank { context?.packageName ?: "" }
        return referenceAvatarColors[(key.hashCode() and Int.MAX_VALUE) % referenceAvatarColors.size]
    }

    fun getFirstLetter(name: String?, address: String): String {
        return referenceInitialCandidate(name ?: address)?.toString() ?: "#"
    }

    fun loadAvatar(
        imageView: CircleImageView,
        textView: TextView?,
        photoUri: String?,
        contactName: String?,
        address: String,
        context: Context
    ) {
        Picasso.get().cancelRequest(imageView)

        val color = getColorForIdentifier(address.ifBlank { contactName.orEmpty() }, context)
        val initial = referenceInitialCandidate(contactName?.takeIf { it.isNotBlank() } ?: address)
        val shouldShowInitial = initial != null && !initial.isDigit() && !isContactListContext(context)

        resetViews(imageView, textView, color)

        if (!photoUri.isNullOrBlank()) {
            imageView.apply {
                visibility = View.VISIBLE
                imageTintList = null
                clearColorFilter()
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(0, 0, 0, 0)
            }
            textView?.visibility = View.GONE
            Picasso.get()
                .load(Uri.parse(photoUri))
                .error(R.drawable.ic_reference_person)
                .into(imageView)
            return
        }

        if (shouldShowInitial && textView != null) {
            imageView.visibility = View.GONE
            textView.apply {
                visibility = View.VISIBLE
                text = initial.toString().uppercase()
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = Typeface.DEFAULT
                gravity = Gravity.CENTER
                includeFontPadding = false
                isAllCaps = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
        } else {
            showPersonIcon(imageView, textView, color)
        }
    }

    private fun resetViews(
        imageView: CircleImageView,
        textView: TextView?,
        backgroundColor: Int
    ) {
        imageView.apply {
            visibility = View.VISIBLE
            setImageDrawable(null)
            setCircleBackgroundColor(backgroundColor)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            elevation = 0f
            translationZ = 0f
        }

        textView?.apply {
            visibility = View.GONE
            text = ""
            background = null
            alpha = 1f
            elevation = 0f
            translationZ = 0f
        }
    }

    private fun showPersonIcon(
        imageView: CircleImageView,
        textView: TextView?,
        backgroundColor: Int
    ) {
        imageView.apply {
            visibility = View.VISIBLE
            setCircleBackgroundColor(backgroundColor)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_reference_person)
        }
        textView?.visibility = View.GONE
    }

    private fun referenceInitialCandidate(value: String?): Char? {
        val cleaned = value
            ?.substringBefore(',')
            ?.split(' ')
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
            ?.firstOrNull()

        return cleaned?.uppercaseChar()
    }

    private fun isContactListContext(context: Context): Boolean {
        return context.javaClass.name.endsWith(".ui.contacts.ContactsActivity") ||
            context.javaClass.name.endsWith(".ui.contacts.ContactsFragment")
    }
}
