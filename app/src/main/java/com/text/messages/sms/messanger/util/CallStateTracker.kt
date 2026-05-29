package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.util.Log

/**
 * In-memory call session state shared between [com.text.messages.sms.messanger.receiver.CallReceiver]
 * (overlay path) and [com.text.messages.sms.messanger.receiver.CallEndReceiver] (notification path).
 */
object CallStateTracker {

    private const val TAG = "CallStateTracker"

    const val PREFS_NAME = "caller_settings"
    const val KEY_MISSED_CALL = "missed_call"
    const val KEY_COMPLETED_CALL = "completed_call"
    const val KEY_NO_ANSWER = "no_answer"
    const val KEY_UNKNOWN_CALLER = "unknown_caller"
    const val KEY_SHOW_CALL_INFO = "show_call_info"

    /** Ringing longer than this without offhook is treated as "no_answer" vs "missed". */
    private const val NO_ANSWER_RING_THRESHOLD_MS = 15_000L

    @Volatile
    var phoneNumber: String? = null
        private set

    @Volatile
    var callStartTimeMs: Long = 0L
        private set

    @Volatile
    var isIncoming: Boolean = false
        private set

    @Volatile
    var wasOffhook: Boolean = false
        private set

    fun onOutgoingCall(number: String?) {
        val normalized = normalizeNumber(number) ?: ""
        phoneNumber = normalized.ifEmpty { phoneNumber }
        isIncoming = false
        wasOffhook = false
        callStartTimeMs = System.currentTimeMillis()
        Log.d(TAG, "Outgoing call: $phoneNumber")
    }

    fun onRinging(number: String?) {
        isIncoming = true
        wasOffhook = false
        phoneNumber = normalizeNumber(number) ?: phoneNumber
        callStartTimeMs = System.currentTimeMillis()
        Log.d(TAG, "Ringing: $phoneNumber")
    }

    fun onOffhook(number: String?) {
        val normalized = normalizeNumber(number)
        if (!normalized.isNullOrEmpty()) {
            phoneNumber = normalized
        }
        if (isIncoming) {
            callStartTimeMs = System.currentTimeMillis()
        } else {
            isIncoming = false
            if (callStartTimeMs == 0L) {
                callStartTimeMs = System.currentTimeMillis()
            }
        }
        wasOffhook = true
        Log.d(TAG, "Offhook: $phoneNumber incoming=$isIncoming")
    }

    fun mergeNumberFromIntent(number: String?) {
        val normalized = normalizeNumber(number)
        if (!normalized.isNullOrEmpty()) {
            phoneNumber = normalized
        }
    }

    /**
     * Builds end-of-call payload and clears session state. Returns null if there was no active session.
     */
    fun consumeCallEndEvent(context: Context, fallbackNumber: String? = null): CallEndEvent? {
        mergeNumberFromIntent(fallbackNumber)

        val endTime = System.currentTimeMillis()
        val startTime = callStartTimeMs
        val incoming = isIncoming
        val offhook = wasOffhook
        val number = phoneNumber

        if (startTime == 0L && number.isNullOrEmpty()) {
            return null
        }

        val duration = if (startTime > 0) endTime - startTime else 0L
        val callType = resolveCallType(incoming, offhook, duration)
        val unknownCaller = number.isNullOrEmpty() || !isContactInPhonebook(context, number)
        val shouldShow = shouldShowAfterCall(context, callType, unknownCaller)

        val event = CallEndEvent(
            number = number,
            callType = callType,
            durationMs = duration,
            endTimeMs = endTime,
            startTimeMs = if (startTime > 0) startTime else endTime,
            isIncoming = incoming,
            isUnknownCaller = unknownCaller,
            shouldShow = shouldShow
        )

        resetSession()
        Log.d(
            TAG,
            "Call end: type=${event.callType} number=${event.number} show=${event.shouldShow}"
        )
        return event
    }

    fun resetSession() {
        phoneNumber = null
        callStartTimeMs = 0L
        isIncoming = false
        wasOffhook = false
    }

    private fun resolveCallType(incoming: Boolean, offhook: Boolean, durationMs: Long): String {
        return when {
            incoming && !offhook && durationMs >= NO_ANSWER_RING_THRESHOLD_MS -> "no_answer"
            incoming && !offhook -> "missed"
            else -> "completed"
        }
    }

    private fun shouldShowAfterCall(
        context: Context,
        callType: String,
        isUnknownCaller: Boolean
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isCallTypeEnabled = when (callType) {
            "missed" -> prefs.getBoolean(KEY_MISSED_CALL, true)
            "completed" -> prefs.getBoolean(KEY_COMPLETED_CALL, true)
            "no_answer" -> prefs.getBoolean(KEY_NO_ANSWER, true)
            else -> false
        }
        val shouldShowForCaller = if (isUnknownCaller) {
            prefs.getBoolean(KEY_UNKNOWN_CALLER, true)
        } else {
            prefs.getBoolean(KEY_SHOW_CALL_INFO, false)
        }
        return isCallTypeEnabled && shouldShowForCaller
    }

    private fun isContactInPhonebook(context: Context, phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contact", e)
            false
        }
    }

    fun normalizeNumber(number: String?): String? {
        if (number.isNullOrBlank()) return null
        val trimmed = number.trim()
        if (trimmed.equals("private number", ignoreCase = true)) return null
        return trimmed.replace(Regex("[^\\d+]"), "").takeIf { it.isNotEmpty() }
    }
}

data class CallEndEvent(
    val number: String?,
    val callType: String,
    val durationMs: Long,
    val endTimeMs: Long,
    val startTimeMs: Long,
    val isIncoming: Boolean,
    val isUnknownCaller: Boolean,
    val shouldShow: Boolean
)
