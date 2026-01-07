package com.text.messages.sms.messanger.ui.main

import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.util.AvatarHelper
import com.text.messages.sms.messanger.util.OtpHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.widget.Toast
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit,
    private val onConversationLongClick: ((Conversation) -> Unit)? = null
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    constructor(onConversationClick: (Conversation) -> Unit) : this(
        onConversationClick = onConversationClick,
        onConversationLongClick = null
    )
    
    companion object {
        private const val TAG = "ConversationAdapter"
        private val SELECTED_BG_COLOR: Int = Color.parseColor("#E6F0FF")
    }

    private var selectedThreadId: Long? = null

    fun setSelectedThreadId(threadId: Long?) {
        val previous = selectedThreadId
        if (previous == threadId) return

        selectedThreadId = threadId

        // Try to update only the affected rows
        previous?.let { prevId ->
            val prevPos = currentList.indexOfFirst { it.threadId == prevId }
            if (prevPos >= 0) notifyItemChanged(prevPos)
        }
        threadId?.let { newId ->
            val newPos = currentList.indexOfFirst { it.threadId == newId }
            if (newPos >= 0) notifyItemChanged(newPos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onViewRecycled(holder: ConversationViewHolder) {
        super.onViewRecycled(holder)
        // Cancel any pending image loads when view is recycled
        holder.cancelPendingLoads()
    }
    
    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // No payload, bind normally
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update - only update the views that changed
            val conversation = getItem(position)
            val payload = payloads[0] as? Set<*>
            if (payload != null) {
                holder.bindPartial(conversation, payload)
            } else {
                holder.bind(conversation)
            }
        }
    }
    
    fun getConversationAt(position: Int): Conversation {
        return getItem(position)
    }

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageContact: CircleImageView = itemView.findViewById(R.id.imageContact)
        private val textAvatarLetter: TextView = itemView.findViewById(R.id.textAvatarLetter)
        private val textContactName: TextView = itemView.findViewById(R.id.textContactName)
        private val textSnippet: TextView = itemView.findViewById(R.id.textSnippet)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val textUnreadDot: View = itemView.findViewById(R.id.textUnreadDot)
        private val buttonStartChat: MaterialButton = itemView.findViewById(R.id.buttonStartChat)
        private val buttonCopyOtpList: TextView = itemView.findViewById(R.id.buttonCopyOtpList)

        fun bind(conversation: Conversation) {
            // Handle "Add Conversation" special item
            if (conversation.threadId == -1L) {
                textContactName.text = conversation.contactName ?: "Add Conversation"
                textSnippet.text = conversation.snippet ?: "Tap to add conversations to this filter"
                textTime.text = ""
                textUnreadDot.visibility = View.GONE
                buttonStartChat.visibility = View.GONE
                imageContact.visibility = View.GONE
                textAvatarLetter.visibility = View.VISIBLE
                textAvatarLetter.text = "+"
                textAvatarLetter.setBackgroundColor(itemView.context.getColor(R.color.blue_primary))
                textAvatarLetter.setTextColor(itemView.context.getColor(R.color.white))
                itemView.setOnClickListener {
                    onConversationClick(conversation)
                }
                itemView.setOnLongClickListener(null)
                return
            }

            applySelectionBackground(conversation.threadId == selectedThreadId)
            
            Log.d(TAG, "bind: Binding conversation - threadId=${conversation.threadId}, contactName='${conversation.contactName}', address='${conversation.address}', photoUri='${conversation.photoUri}'")
            
            // Reset avatar state first to prevent showing stale data
            resetAvatarState()
            
            textContactName.text = conversation.contactName ?: conversation.address
            textSnippet.text = conversation.snippet
            textTime.text = formatTime(conversation.date)
            
            // Show unread dot if there are unread messages
            if (conversation.unreadCount > 0) {
                textUnreadDot.visibility = View.VISIBLE
            } else {
                textUnreadDot.visibility = View.GONE
            }
            
            // Hide "Start Chat" button - users can click on the conversation item itself
            buttonStartChat.visibility = View.GONE
            
            // Check if snippet contains OTP and show copy button
            val snippet = conversation.snippet ?: ""
            val hasOTP = OtpHelper.isOTPMessage(snippet)
            val otp = if (hasOTP) OtpHelper.extractOTP(snippet) else null
            
            if (hasOTP && otp != null) {
                buttonCopyOtpList.visibility = View.VISIBLE
                buttonCopyOtpList.setOnClickListener { view ->
                    view.isClickable = true
                    copyOTPToClipboard(otp)
                }
            } else {
                buttonCopyOtpList.visibility = View.GONE
            }
            
            // Log TextView state before loading avatar
            Log.d(TAG, "bind: Before loadAvatar - textAvatarLetter visibility=${textAvatarLetter.visibility}, text='${textAvatarLetter.text}', width=${textAvatarLetter.width}, height=${textAvatarLetter.height}")
            Log.d(TAG, "bind: Before loadAvatar - imageContact width=${imageContact.width}, height=${imageContact.height}")
            
            // Load avatar with contact image, first letter, or icon
            AvatarHelper.loadAvatar(
                imageContact,
                textAvatarLetter,
                conversation.photoUri,
                conversation.contactName,
                conversation.address,
                itemView.context
            )
            
            // Log TextView state after loading avatar
            Log.d(TAG, "bind: After loadAvatar - textAvatarLetter visibility=${textAvatarLetter.visibility}, text='${textAvatarLetter.text}', width=${textAvatarLetter.width}, height=${textAvatarLetter.height}")
            
            itemView.setOnClickListener {
                onConversationClick(conversation)
            }

            itemView.setOnLongClickListener {
                if (onConversationLongClick != null) {
                    onConversationLongClick.invoke(conversation)
                    true
                } else {
                    false
                }
            }
        }

        private fun applySelectionBackground(isSelected: Boolean) {
            if (isSelected) {
                itemView.setBackgroundColor(SELECTED_BG_COLOR)
                return
            }

            // Restore selectable background (ripple)
            val typedValue = TypedValue()
            val resolved = itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            if (resolved && typedValue.resourceId != 0) {
                itemView.setBackgroundResource(typedValue.resourceId)
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        
        private fun resetAvatarState() {
            // Cancel any pending Picasso requests
            com.squareup.picasso.Picasso.get().cancelRequest(imageContact)
            
            // Reset image view to a clean state
            imageContact.setImageDrawable(null)
            imageContact.visibility = View.VISIBLE
            // Reset background color will be set by AvatarHelper
            
            // Reset text view to a clean state
            textAvatarLetter.text = ""
            textAvatarLetter.visibility = View.GONE
            textAvatarLetter.background = null
            textAvatarLetter.alpha = 1.0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                textAvatarLetter.elevation = 0f
                textAvatarLetter.translationZ = 0f
            }
        }
        
        fun cancelPendingLoads() {
            // Cancel any pending Picasso requests
            com.squareup.picasso.Picasso.get().cancelRequest(imageContact)
        }
        
        fun bindPartial(conversation: Conversation, payload: Set<*>) {
            // Only update the views that changed based on payload
            payload.forEach { change ->
                when (change) {
                    "snippet" -> {
                        textSnippet.text = conversation.snippet
                        // Update OTP button visibility when snippet changes
                        val snippet = conversation.snippet ?: ""
                        val hasOTP = OtpHelper.isOTPMessage(snippet)
                        val otp = if (hasOTP) OtpHelper.extractOTP(snippet) else null
                        
                        if (hasOTP && otp != null) {
                            buttonCopyOtpList.visibility = View.VISIBLE
                            buttonCopyOtpList.setOnClickListener { view ->
                                view.isClickable = true
                                copyOTPToClipboard(otp)
                            }
                        } else {
                            buttonCopyOtpList.visibility = View.GONE
                        }
                    }
                    "date" -> textTime.text = formatTime(conversation.date)
                    "unreadCount" -> {
                        if (conversation.unreadCount > 0) {
                            textUnreadDot.visibility = View.VISIBLE
                        } else {
                            textUnreadDot.visibility = View.GONE
                        }
                    }
                    "contactName" -> {
                        textContactName.text = conversation.contactName ?: conversation.address
                        // Reload avatar when contact name changes
                        AvatarHelper.loadAvatar(
                            imageContact,
                            textAvatarLetter,
                            conversation.photoUri,
                            conversation.contactName,
                            conversation.address,
                            itemView.context
                        )
                    }
                }
            }
            // Ensure button is always hidden during partial updates
            buttonStartChat.visibility = View.GONE
        }
        
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} Min"
                diff < 86400000 -> {
                    val hours = diff / 3600000
                    if (hours < 24) "${hours}h" else "Yesterday"
                }
                else -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    days[dayOfWeek - 1]
                }
            }
        }
        
        private fun copyOTPToClipboard(otp: String) {
            try {
                val clipboard = itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "OTP copied: $otp", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(itemView.context, "Error copying OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            // Items are the same if they have the same threadId
            return oldItem.threadId == newItem.threadId
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            // Contents are the same if all fields match
            return oldItem.address == newItem.address &&
                   oldItem.snippet == newItem.snippet &&
                   oldItem.date == newItem.date &&
                   oldItem.unreadCount == newItem.unreadCount &&
                   oldItem.contactName == newItem.contactName
        }
        
        override fun getChangePayload(oldItem: Conversation, newItem: Conversation): Any? {
            // Return a payload to indicate what changed for partial updates
            // This allows RecyclerView to only update the specific views that changed
            val payload = mutableSetOf<String>()
            if (oldItem.snippet != newItem.snippet) payload.add("snippet")
            if (oldItem.date != newItem.date) payload.add("date")
            if (oldItem.unreadCount != newItem.unreadCount) payload.add("unreadCount")
            if (oldItem.contactName != newItem.contactName) payload.add("contactName")
            return if (payload.isEmpty()) null else payload
        }
    }
}

