package com.text.messages.sms.messanger.ui.conversation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.OtpHelper
import com.squareup.picasso.Picasso
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DateHeader(val date: Long) : MessageListItem()
}

class MessageAdapter(
    private val onMessageLongClick: ((Message) -> Unit)? = null,
    private val onMessageClick: ((Message) -> Unit)? = null,
    private val onFailedMessageClick: ((Message) -> Unit)? = null,
    private val onSelectionChanged: (() -> Unit)? = null
) : ListAdapter<MessageListItem, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val selectedMessages = mutableSetOf<Long>()
    private val starredMessages = mutableSetOf<Long>()
    private var isSelectionMode = false

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_DATE_HEADER = 2
    }
    
    fun setSelectedMessages(selected: Set<Long>) {
        selectedMessages.clear()
        selectedMessages.addAll(selected)
        notifyDataSetChanged()
    }
    
    fun setStarredMessages(starred: Set<Long>) {
        starredMessages.clear()
        starredMessages.addAll(starred)
        notifyDataSetChanged()
    }
    
    fun toggleSelection(messageId: Long) {
        if (selectedMessages.contains(messageId)) {
            selectedMessages.remove(messageId)
        } else {
            selectedMessages.add(messageId)
        }
        // Notify only the changed item
        val position = currentList.indexOfFirst { 
            it is MessageListItem.MessageItem && it.message.id == messageId 
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
        // Notify selection changed
        onSelectionChanged?.invoke()
    }
    
    fun getSelectedMessages(): Set<Long> = selectedMessages.toSet()
    
    fun clearSelection() {
        selectedMessages.clear()
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedMessages.clear()
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                MessageViewHolder(view, this)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                MessageViewHolder(view, this)
            }
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MessageListItem.MessageItem -> {
                (holder as MessageViewHolder).bind(item.message, isSelectionMode)
            }
            is MessageListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.date)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageListItem.MessageItem -> {
                if (item.message.type == MessageType.SENT) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
            is MessageListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
        }
    }

    class MessageViewHolder(
        itemView: View,
        private val adapter: MessageAdapter
    ) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView? = itemView.findViewById(R.id.textMessage)
        private val textTime: TextView? = itemView.findViewById(R.id.textTime)
        private val textTimeForAttachment: TextView? = itemView.findViewById(R.id.textTimeForAttachment)
        private val textTimeForContactCard: TextView? = itemView.findViewById(R.id.textTimeForContactCard)
        private val layoutTimeForAttachment: LinearLayout? = itemView.findViewById(R.id.layoutTimeForAttachment)
        private val layoutTimeForContactCard: LinearLayout? = itemView.findViewById(R.id.layoutTimeForContactCard)
        private val imageCheck: ImageView? = itemView.findViewById(R.id.imageCheck)
        private val imageStarBadge: ImageView? = itemView.findViewById(R.id.imageStarBadge)
        private val imageSmsFailed: ImageView? = itemView.findViewById(R.id.imageSmsFailed)
        private val cardMessage: MaterialCardView? = itemView.findViewById(R.id.cardMessage)
        private val context = itemView.context
        
        // Attachment views
        private val imageAttachment: ImageView? = itemView.findViewById(R.id.imageAttachment)
        private val imageAttachmentFailed: ImageView? = itemView.findViewById(R.id.imageAttachmentFailed)
        private val layoutContactCard: LinearLayout? = itemView.findViewById(R.id.layoutContactCard)
        private val imageContactCardFailed: ImageView? = itemView.findViewById(R.id.imageContactCardFailed)
        private val textContactName: TextView? = itemView.findViewById(R.id.textContactName)
        private val textContactNumber: TextView? = itemView.findViewById(R.id.textContactNumber)
        private val imageContactIcon: ImageView? = itemView.findViewById(R.id.imageContactIcon)
        
        // OTP box views - find from included layout
        private val layoutOtpBox: View? get() = itemView.findViewById(R.id.layoutOtpBox)
        private val textOtpValue: TextView? get() = itemView.findViewById(R.id.textOtpValue)
        private val buttonCopyOtp: TextView? get() = itemView.findViewById(R.id.buttonCopyOtp)
        private val imageDeleteOtp: ImageView? get() = itemView.findViewById(R.id.imageDeleteOtp)

        fun bind(message: Message, isSelectionMode: Boolean) {
            // Early return if essential views are null (shouldn't happen, but safe during rotation)
            if (textMessage == null || textTime == null || cardMessage == null) {
                return
            }
            
            textMessage.text = message.body
            val timeText = formatTime(message.date)
            textTime.text = timeText
            
            // Handle attachments (images and contact cards)
            val hasVisibleAttachment = handleAttachments(message)
            
            // Show/hide failed status indicator
            // Consider a message failed if:
            // 1. Status is explicitly FAILED, OR
            // 2. Status is PENDING and message is older than 2 minutes (likely failed to send)
            val currentTime = System.currentTimeMillis()
            val messageAge = currentTime - message.date
            val isPendingTooLong = message.status == MessageStatus.PENDING && 
                                   message.type == MessageType.SENT && 
                                   messageAge > 120000 // 2 minutes in milliseconds
            val isFailed = (message.status == MessageStatus.FAILED && message.type == MessageType.SENT) || 
                          isPendingTooLong
            
            // Show error icon only on the specific attachment that's visible
            if (isFailed && hasVisibleAttachment) {
                // Show error icon only on the attachment that's actually visible
                if (imageAttachment?.visibility == View.VISIBLE) {
                    imageAttachmentFailed?.visibility = View.VISIBLE
                    imageContactCardFailed?.visibility = View.GONE
                } else if (layoutContactCard?.visibility == View.VISIBLE) {
                    imageAttachmentFailed?.visibility = View.GONE
                    imageContactCardFailed?.visibility = View.VISIBLE
                } else {
                    imageAttachmentFailed?.visibility = View.GONE
                    imageContactCardFailed?.visibility = View.GONE
                }
                // Hide SMS error badge when attachment error is shown
                imageSmsFailed?.visibility = View.GONE
            } else {
                // Hide error icons on attachments
                imageAttachmentFailed?.visibility = View.GONE
                imageContactCardFailed?.visibility = View.GONE
                
                // Show SMS error badge for failed text-only messages
                if (isFailed && !hasVisibleAttachment) {
                    imageSmsFailed?.visibility = View.VISIBLE
                } else {
                    imageSmsFailed?.visibility = View.GONE
                }
            }
            
            // Handle time display - show highlighted time for attachments, regular time for text-only
            if (hasVisibleAttachment) {
                // Hide regular time, show highlighted time for attachments
                textTime?.visibility = View.GONE
                
                // Show time for image attachment
                if (imageAttachment?.visibility == View.VISIBLE) {
                    layoutTimeForAttachment?.visibility = View.VISIBLE
                    textTimeForAttachment?.text = timeText
                } else {
                    layoutTimeForAttachment?.visibility = View.GONE
                }
                
                // Show time for contact card
                if (layoutContactCard?.visibility == View.VISIBLE) {
                    layoutTimeForContactCard?.visibility = View.VISIBLE
                    textTimeForContactCard?.text = timeText
                } else {
                    layoutTimeForContactCard?.visibility = View.GONE
                }
            } else {
                // Show regular time for text-only messages (no background)
                textTime?.visibility = View.VISIBLE
                textTime?.background = null
                layoutTimeForAttachment?.visibility = View.GONE
                layoutTimeForContactCard?.visibility = View.GONE
            }
            
            // Handle OTP display
            val otp = message.otp ?: OtpHelper.extractOTP(message.body)
            if (otp != null && OtpHelper.isOTPMessage(message.body)) {
                // Show OTP box
                layoutOtpBox?.visibility = View.VISIBLE
                textOtpValue?.text = otp
                
                // Set up copy button
                buttonCopyOtp?.setOnClickListener {
                    copyOTPToClipboard(otp)
                }
                
                // Set up delete button (hides OTP box)
                imageDeleteOtp?.setOnClickListener {
                    layoutOtpBox?.visibility = View.GONE
                }
            } else {
                // Hide OTP box if no OTP
                layoutOtpBox?.visibility = View.GONE
            }
            
            // Apply font size and font family
            val fontSize = AppPreferences.getFontSize(context)
            textMessage?.textSize = fontSize
            
            val fontFamilyIndex = AppPreferences.getFontFamily(context)
            val fontFamilies = listOf(
                Typeface.DEFAULT,
                Typeface.SANS_SERIF,
                Typeface.SERIF,
                Typeface.MONOSPACE,
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC),
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC),
                Typeface.create(Typeface.SERIF, Typeface.BOLD),
                Typeface.create(Typeface.SERIF, Typeface.ITALIC),
                Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
                Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC),
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )
            textMessage?.typeface = fontFamilies.getOrElse(fontFamilyIndex) { Typeface.DEFAULT }
            
            // Set max width to 80% of screen width on the message text
            val displayMetrics = context.resources.displayMetrics
            val maxWidth = (displayMetrics.widthPixels * 0.8f).toInt()
            textMessage?.maxWidth = maxWidth
            
            // Apply bubble color only to sent messages (not theme color)
            // Make bubble transparent if it has a visible attachment
            if (message.type == MessageType.SENT) {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.RECTANGLE
                val cornerRadius = 16f * context.resources.displayMetrics.density
                drawable.cornerRadius = cornerRadius
                
                if (hasVisibleAttachment) {
                    // Transparent background for messages with attachments
                    drawable.setColor(Color.TRANSPARENT)
                } else {
                    // Normal bubble color for text-only messages
                    val bubbleColor = AppPreferences.getBubbleColor(context)
                    drawable.setColor(Color.parseColor(bubbleColor))
                }
                cardMessage?.background = drawable
            } else {
                // For received messages, also make transparent if has visible attachment
                if (hasVisibleAttachment) {
                    val drawable = GradientDrawable()
                    drawable.shape = GradientDrawable.RECTANGLE
                    val cornerRadius = 16f * context.resources.displayMetrics.density
                    drawable.cornerRadius = cornerRadius
                    drawable.setColor(Color.TRANSPARENT)
                    cardMessage?.background = drawable
                }
            }
            
            // Show/hide checkmark based on selection
            val isSelected = adapter.selectedMessages.contains(message.id)
            if (isSelectionMode) {
                imageCheck?.visibility = View.VISIBLE
                imageCheck?.setImageResource(if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected)
            } else {
                imageCheck?.visibility = View.GONE
            }
            
            // Show/hide star badge based on starred status
            val isStarred = adapter.starredMessages.contains(message.id)
            imageStarBadge?.visibility = if (isStarred) View.VISIBLE else View.GONE
            
            // Handle clicks
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    // In selection mode, toggle selection
                    adapter.toggleSelection(message.id)
                    bind(message, isSelectionMode) // Refresh view
                } else {
                    // If message is failed and has attachment, show preview dialog
                    if (isFailed && (message.attachmentPath != null || message.mimeType != null)) {
                        adapter.onFailedMessageClick?.invoke(message)
                    } else {
                        adapter.onMessageClick?.invoke(message)
                    }
                }
            }
            
            itemView.setOnLongClickListener {
                adapter.onMessageLongClick?.invoke(message)
                true
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return timeFormat.format(calendar.time)
        }
        
        private fun copyOTPToClipboard(otp: String) {
            try {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "OTP copied: $otp", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error copying OTP", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun handleAttachments(message: Message): Boolean {
            // Hide all attachment views first
            imageAttachment?.visibility = View.GONE
            layoutContactCard?.visibility = View.GONE
            
            // Check if message has attachment
            val attachmentPath = message.attachmentPath
            val mimeType = message.mimeType
            var hasAttachment = false
            
            if (!attachmentPath.isNullOrEmpty()) {
                hasAttachment = true
                try {
                    val uri = Uri.parse(attachmentPath)
                    
                    // Check if it's an image
                    if (mimeType?.startsWith("image/") == true || 
                        mimeType == "image/*" ||
                        attachmentPath.contains(".jpg", ignoreCase = true) ||
                        attachmentPath.contains(".jpeg", ignoreCase = true) ||
                        attachmentPath.contains(".png", ignoreCase = true) ||
                        attachmentPath.contains(".gif", ignoreCase = true)) {
                        // Display image
                        imageAttachment?.visibility = View.VISIBLE
                        try {
                            // Try loading with Picasso first
                            Picasso.get()
                                .load(uri)
                                .placeholder(R.drawable.ic_gallery)
                                .error(R.drawable.ic_gallery)
                                .resize(400, 400)
                                .centerCrop()
                                .into(imageAttachment)
                        } catch (e: Exception) {
                            Log.e("MessageAdapter", "Error loading image with Picasso: ${e.message}")
                            // Fallback: try to load from file or content URI
                            try {
                                if (uri.scheme == "content" || uri.scheme == "file") {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    if (inputStream != null) {
                                        val bitmap = BitmapFactory.decodeStream(inputStream)
                                        inputStream.close()
                                        if (bitmap != null) {
                                            imageAttachment?.setImageBitmap(bitmap)
                                        } else {
                                            imageAttachment?.visibility = View.GONE
                                        }
                                    } else {
                                        // Try file path
                                        val file = File(uri.path ?: "")
                                        if (file.exists()) {
                                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                            imageAttachment?.setImageBitmap(bitmap)
                                        } else {
                                            imageAttachment?.visibility = View.GONE
                                        }
                                    }
                                } else {
                                    // Try file path
                                    val file = File(uri.path ?: "")
                                    if (file.exists()) {
                                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                        imageAttachment?.setImageBitmap(bitmap)
                                    } else {
                                        imageAttachment?.visibility = View.GONE
                                    }
                                }
                            } catch (ex: Exception) {
                                Log.e("MessageAdapter", "Error loading image fallback: ${ex.message}")
                                // If all fails, hide the image view
                                imageAttachment?.visibility = View.GONE
                            }
                        }
                    } 
                    // Check if it's a contact card
                    else if (mimeType == "text/x-vCard" || 
                             attachmentPath.contains(".vcf", ignoreCase = true)) {
                        // Display contact card
                        layoutContactCard?.visibility = View.VISIBLE
                        // Try to parse contact info from message body or attachment
                        parseContactCard(message)
                    }
                } catch (e: Exception) {
                    Log.e("MessageAdapter", "Error handling attachment: ${e.message}")
                    // Error loading attachment, but still consider it has attachment for transparent bubble
                    // Hide views but keep hasAttachment = true
                    imageAttachment?.visibility = View.GONE
                    layoutContactCard?.visibility = View.GONE
                }
            }
            
            return hasAttachment
        }
        
        private fun parseContactCard(message: Message) {
            var contactName = ""
            var contactNumber = ""
            
            // First, try to extract from message body (vCard format)
            val body = message.body
            if (body.contains("BEGIN:VCARD")) {
                val nameMatch = Regex("FN:([^\\r\\n]+)").find(body)
                val telMatch = Regex("TEL:([^\\r\\n]+)").find(body)
                
                contactName = nameMatch?.groupValues?.get(1) ?: ""
                contactNumber = telMatch?.groupValues?.get(1) ?: ""
            }
            
            // If not found in body, try reading from attachment file
            if (contactName.isEmpty() && contactNumber.isEmpty()) {
                val attachmentPath = message.attachmentPath
                if (!attachmentPath.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse(attachmentPath)
                        val vCardContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.bufferedReader().use { it.readText() }
                        }
                        
                        if (vCardContent != null && vCardContent.contains("BEGIN:VCARD")) {
                            val nameMatch = Regex("FN:([^\\r\\n]+)").find(vCardContent)
                            val telMatch = Regex("TEL:([^\\r\\n]+)").find(vCardContent)
                            
                            contactName = nameMatch?.groupValues?.get(1) ?: ""
                            contactNumber = telMatch?.groupValues?.get(1) ?: ""
                        }
                    } catch (e: Exception) {
                        Log.e("MessageAdapter", "Error reading vCard file: ${e.message}")
                    }
                }
            }
            
            // Display contact info
            if (contactName.isNotEmpty() || contactNumber.isNotEmpty()) {
                textContactName?.text = contactName.ifEmpty { "Contact" }
                textContactNumber?.text = contactNumber
            } else {
                // Fallback: show generic contact card
                textContactName?.text = "Contact card"
                textContactNumber?.text = ""
            }
        }
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate: TextView = itemView.findViewById(R.id.textDate)

        fun bind(date: Long) {
            textDate.text = formatDate(date)
        }

        private fun formatDate(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }

            return when {
                isSameDay(messageDate, today) -> "Today"
                isSameDay(messageDate, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
                messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                    val dateFormat = SimpleDateFormat("MMMM dd", Locale.getDefault())
                    dateFormat.format(messageDate.time)
                }
                else -> {
                    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                    dateFormat.format(messageDate.time)
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageListItem>() {
        override fun areItemsTheSame(oldItem: MessageListItem, newItem: MessageListItem): Boolean {
            return when {
                oldItem is MessageListItem.MessageItem && newItem is MessageListItem.MessageItem ->
                    oldItem.message.id == newItem.message.id
                oldItem is MessageListItem.DateHeader && newItem is MessageListItem.DateHeader ->
                    oldItem.date == newItem.date
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MessageListItem, newItem: MessageListItem): Boolean {
            return when {
                oldItem is MessageListItem.MessageItem && newItem is MessageListItem.MessageItem -> {
                    oldItem.message.id == newItem.message.id &&
                    oldItem.message.body == newItem.message.body &&
                    oldItem.message.date == newItem.message.date &&
                    oldItem.message.type == newItem.message.type &&
                    oldItem.message.status == newItem.message.status
                }
                oldItem is MessageListItem.DateHeader && newItem is MessageListItem.DateHeader ->
                    oldItem.date == newItem.date
                else -> false
            }
        }
    }
}
