package com.text.messages.sms.messanger.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.text.messages.sms.messanger.R

object RingtoneSoundResolver {
    const val PREFS_NAME = "ringtone_settings"
    const val KEY_SELECTED_RINGTONE = "selected_ringtone"
    const val KEY_SELECTED_RINGTONE_URI = "selected_ringtone_uri"

    const val SOUND_OFF = "sound_off"
    const val DEFAULT = "default"
    const val SYSTEM = "system"
    const val CUSTOMIZE = "customize"

    private val rawSounds = mapOf(
        "bling" to R.raw.ringtone_bling,
        "android" to R.raw.ringtone_android,
        "whistle" to R.raw.ringtone_whistle,
        "ting" to R.raw.ringtone_ting,
        "alert" to R.raw.ic_ringtone_alert,
        "instruments" to R.raw.ringtone_instrumental,
        "soul" to R.raw.ringtone_soul,
        "love" to R.raw.ringtone_love,
        "water" to R.raw.ringtone_waterdrop,
        "tick" to R.raw.ringtone_tick
    )

    fun getPreviewSoundUri(context: Context, ringtoneName: String?, pickedUri: String? = null): Uri? {
        return when (ringtoneName) {
            SOUND_OFF -> Uri.EMPTY
            DEFAULT, null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SYSTEM, CUSTOMIZE -> pickedUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> rawSounds[ringtoneName]?.let { rawUri(context, it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    fun getNotificationSoundUri(context: Context, ringtoneName: String?, pickedUri: String? = null): Uri? {
        return when (ringtoneName) {
            SOUND_OFF -> Uri.EMPTY
            DEFAULT, null -> null
            SYSTEM, CUSTOMIZE -> pickedUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            else -> rawSounds[ringtoneName]?.let { rawUri(context, it) }
        }
    }

    fun isBundledSound(ringtoneName: String): Boolean = rawSounds.containsKey(ringtoneName)

    private fun rawUri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")
}
