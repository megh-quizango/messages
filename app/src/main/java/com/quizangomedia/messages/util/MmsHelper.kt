package com.quizangomedia.messages.util

import android.content.Context
import android.content.res.XmlResourceParser
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.util.Log
import com.quizangomedia.messages.R
import org.xmlpull.v1.XmlPullParser
import java.io.IOException

object MmsHelper {
    private const val TAG = "MmsHelper"
    
    /**
     * Check if MMS is enabled and available on the device
     */
    fun isMmsEnabled(context: Context): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val isEnabled = telephonyManager.isSmsCapable && 
                           telephonyManager.simState == TelephonyManager.SIM_STATE_READY
            
            if (!isEnabled) {
                Log.w(TAG, "MMS not available: SMS not capable or SIM not ready")
            }
            
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MMS availability", e)
            false
        }
    }
    
    /**
     * Get MMS configuration value
     */
    fun getMmsConfigInt(context: Context, name: String, defaultValue: Int): Int {
        return try {
            val parser = context.resources.getXml(R.xml.mms_config)
            var value = defaultValue
            
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "int" && parser.getAttributeValue(null, "name") == name) {
                        parser.next()
                        value = parser.text.toIntOrNull() ?: defaultValue
                        break
                    }
                }
                parser.next()
            }
            parser.close()
            value
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS config: $name", e)
            defaultValue
        }
    }
    
    /**
     * Get MMS configuration boolean value
     */
    fun getMmsConfigBool(context: Context, name: String, defaultValue: Boolean): Boolean {
        return try {
            val parser = context.resources.getXml(R.xml.mms_config)
            var value = defaultValue
            
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "bool" && parser.getAttributeValue(null, "name") == name) {
                        parser.next()
                        value = parser.text == "true"
                        break
                    }
                }
                parser.next()
            }
            parser.close()
            value
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS config: $name", e)
            defaultValue
        }
    }
    
    /**
     * Get MMS configuration string value
     */
    fun getMmsConfigString(context: Context, name: String, defaultValue: String): String {
        return try {
            val parser = context.resources.getXml(R.xml.mms_config)
            var value = defaultValue
            
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "string" && parser.getAttributeValue(null, "name") == name) {
                        parser.next()
                        value = parser.text ?: defaultValue
                        break
                    }
                }
                parser.next()
            }
            parser.close()
            value
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS config: $name", e)
            defaultValue
        }
    }
    
    /**
     * Get maximum message size from config
     */
    fun getMaxMessageSize(context: Context): Int {
        return getMmsConfigInt(context, "maxMessageSize", 307200)
    }
    
    /**
     * Check if MMS service is available and enabled
     */
    fun isMmsServiceAvailable(context: Context): Boolean {
        val isEnabled = getMmsConfigBool(context, "enabledMMS", true)
        val isMmsCapable = isMmsEnabled(context)
        
        if (!isEnabled) {
            Log.w(TAG, "MMS is disabled in configuration")
            return false
        }
        
        if (!isMmsCapable) {
            Log.w(TAG, "Device is not MMS capable")
            return false
        }
        
        return true
    }
    
    /**
     * Check if mobile data is available for MMS
     */
    fun isMobileDataAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Check if mobile data is available
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking mobile data availability", e)
            true // Assume available if we can't check
        }
    }
    
    /**
     * Get user-friendly error message for MMS send failure
     */
    fun getMmsErrorMessage(resultCode: Int): String {
        return when (resultCode) {
            1 -> "MMS send failed: Generic error. Please check your MMS settings."
            2 -> "MMS send failed: Mobile radio is off. Please enable mobile data."
            3 -> "MMS send failed: Invalid message format."
            4 -> "MMS send failed: No service available. Please check your network connection."
            5 -> "MMS send failed: Network error. Please check:\n• Mobile data is enabled\n• MMS APN is configured\n• Network connection is available"
            else -> "MMS send failed: Unknown error (code: $resultCode). Please check your MMS settings."
        }
    }
}

