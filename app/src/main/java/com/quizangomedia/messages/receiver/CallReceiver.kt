package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.quizangomedia.messages.ui.caller.CallAfterActivity
import com.quizangomedia.messages.util.AfterCallNotificationHelper
import com.quizangomedia.messages.util.CallerWidgetWindow

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private const val PREFS_NAME = "caller_settings"
        private const val KEY_MISSED_CALL = "missed_call"
        private const val KEY_COMPLETED_CALL = "completed_call"
        private const val KEY_NO_ANSWER = "no_answer"
        private const val KEY_UNKNOWN_CALLER = "unknown_caller"
        private const val KEY_CALL_STATE = "call_state"
        private const val KEY_INCOMING_NUMBER = "incoming_number"
        private const val KEY_CALL_START_TIME = "call_start_time"
        private const val KEY_IS_INCOMING = "is_incoming"
        
        private var windowView: CallerWidgetWindow? = null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        Log.d(TAG, "CallReceiver: state=$state, incomingNumber=$incomingNumber")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call detected
                Log.d(TAG, "RINGING: Incoming call from $incomingNumber")
                prefs.edit().apply {
                    putBoolean(KEY_IS_INCOMING, true)
                    putString(KEY_INCOMING_NUMBER, incomingNumber)
                    putLong(KEY_CALL_START_TIME, System.currentTimeMillis())
                    apply()
                }
            }
            
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing call started
                Log.d(TAG, "OFFHOOK: Call active")
                val isIncoming = prefs.getBoolean(KEY_IS_INCOMING, false)
                
                // If this is an outgoing call (no RINGING state before), get the dialed number
                if (!isIncoming) {
                    val dialedNumber = getLastDialedNumber(context)
                    if (!dialedNumber.isNullOrEmpty()) {
                        Log.d(TAG, "OFFHOOK: Outgoing call to $dialedNumber")
                        prefs.edit().apply {
                            putBoolean(KEY_IS_INCOMING, false)
                            putString(KEY_INCOMING_NUMBER, dialedNumber)
                            putLong(KEY_CALL_START_TIME, System.currentTimeMillis())
                            apply()
                        }
                    }
                }
                
                // Preload ads, check for MIUI devices, start overlay service if needed
                if (checkMIUIDevice() && isIncoming) {
                    startService(context)
                }
            }
            
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended
                Log.d(TAG, "IDLE: Call ended")
                // For outgoing calls, incomingNumber in IDLE might contain the dialed number
                // Store it if we don't already have a number stored
                val storedNumber = prefs.getString(KEY_INCOMING_NUMBER, null)
                if (storedNumber.isNullOrEmpty() && !incomingNumber.isNullOrEmpty()) {
                    val isIncoming = prefs.getBoolean(KEY_IS_INCOMING, false)
                    if (!isIncoming) {
                        // This is likely an outgoing call, store the number
                        prefs.edit().putString(KEY_INCOMING_NUMBER, incomingNumber).apply()
                        Log.d(TAG, "IDLE: Stored outgoing call number: $incomingNumber")
                    }
                }
                handleCallEnded(context, prefs, incomingNumber)
            }
        }
    }
    
    private fun handleCallEnded(
        context: Context,
        prefs: SharedPreferences,
        incomingNumber: String?
    ) {
        val callStartTime = prefs.getLong(KEY_CALL_START_TIME, 0)
        val isIncoming = prefs.getBoolean(KEY_IS_INCOMING, false)
        val storedNumber = prefs.getString(KEY_INCOMING_NUMBER, null)
        
        // Use stored number if available, otherwise use incomingNumber from intent
        val phoneNumber = storedNumber ?: incomingNumber
        
        val endTime = System.currentTimeMillis()
        val callDuration = if (callStartTime > 0) endTime - callStartTime else 0
        
        // If we still don't have a number, try to get it from CallLog (for outgoing calls)
        val finalNumber = if (phoneNumber.isNullOrEmpty() && !isIncoming) {
            getLastDialedNumber(context) ?: phoneNumber
        } else {
            phoneNumber
        }
        
        // Determine call type
        val callType = when {
            callDuration == 0L && isIncoming -> "missed"
            callDuration < 5000 && isIncoming -> "no_answer" // Less than 5 seconds
            else -> "completed"
        }
        
        // Check if number is unknown
        val isUnknownCaller = finalNumber.isNullOrEmpty() || 
                !isContactInPhonebook(context, finalNumber)
        
        // Check if overlay should be shown based on settings
        val shouldShow = shouldShowOverlay(context, prefs, callType, isUnknownCaller)
        
        Log.d(TAG, "Call ended - type: $callType, number: $finalNumber, unknown: $isUnknownCaller, shouldShow: $shouldShow")
        
        if (shouldShow) {
            // Check overlay permission
            if (Settings.canDrawOverlays(context)) {
                openCallAfterActivity(context, finalNumber, endTime, callType)
            } else {
                // Show notification instead
                AfterCallNotificationHelper.showNotification(
                    context,
                    finalNumber,
                    callType,
                    callDuration
                )
            }
        }
        
        // Clear call state
        prefs.edit().apply {
            remove(KEY_IS_INCOMING)
            remove(KEY_INCOMING_NUMBER)
            remove(KEY_CALL_START_TIME)
            apply()
        }
        
        // Hide overlay if it was shown
        stopService()
    }
    
    /**
     * Get the last dialed number from CallLog
     * This is used for outgoing calls where EXTRA_INCOMING_NUMBER is null
     */
    private fun getLastDialedNumber(context: Context): String? {
        return try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )
            val selection = "${CallLog.Calls.TYPE} = ?"
            val selectionArgs = arrayOf(CallLog.Calls.OUTGOING_TYPE.toString())
            // Note: LIMIT cannot be used in sortOrder, so we'll get the first result manually
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                // Get the first (most recent) outgoing call
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        Log.d(TAG, "Last dialed number from CallLog: $number")
                        number
                    } else null
                } else null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read CallLog", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last dialed number", e)
            null
        }
    }
    
    private fun shouldShowOverlay(
        context: Context,
        prefs: SharedPreferences,
        callType: String,
        isUnknownCaller: Boolean
    ): Boolean {
        return when (callType) {
            "missed" -> prefs.getBoolean(KEY_MISSED_CALL, true)
            "completed" -> prefs.getBoolean(KEY_COMPLETED_CALL, true)
            "no_answer" -> prefs.getBoolean(KEY_NO_ANSWER, true)
            else -> false
        } && (!isUnknownCaller || prefs.getBoolean(KEY_UNKNOWN_CALLER, true))
    }
    
    private fun isContactInPhonebook(context: Context, phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contact", e)
            false
        }
    }
    
    private fun openCallAfterActivity(
        context: Context,
        number: String?,
        endTime: Long,
        callType: String
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isIncoming = prefs.getBoolean(KEY_IS_INCOMING, false)
            val callStartTime = prefs.getLong(KEY_CALL_START_TIME, endTime)
            
            val intent = Intent(context, CallAfterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("CALLER_NUMBER", number)
                putExtra("CALL_END_TIME", endTime)
                putExtra("CALL_START_TIME", callStartTime)
                putExtra("CALL_TYPE", callType)
                putExtra("IS_INCOMING", isIncoming)
            }
            context.startActivity(intent)
            Log.d(TAG, "CallAfterActivity started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CallAfterActivity", e)
            // Fallback to notification
            AfterCallNotificationHelper.showNotification(context, number, callType, 0)
        }
    }
    
    fun startService(context: Context) {
        try {
            if (windowView == null) {
                windowView = CallerWidgetWindow(context.applicationContext)
            }
            windowView?.show()
            Log.d(TAG, "Overlay service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start overlay service", e)
        }
    }
    
    fun stopService() {
        try {
            windowView?.hide()
            Log.d(TAG, "Overlay service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop overlay service", e)
        }
    }
    
    private fun checkMIUIDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val miuiDevices = listOf("xiaomi", "redmi", "oppo", "vivo", "oneplus", "realme")
        return miuiDevices.contains(brand) || miuiDevices.contains(manufacturer)
    }
}

