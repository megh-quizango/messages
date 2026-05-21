package com.text.messages.sms.messanger.util

import android.telephony.PhoneNumberUtils

object MessagingAddressUtils {

    fun canReplyToAddress(address: String?): Boolean {
        val normalizedAddress = address?.trim().orEmpty()
        if (normalizedAddress.isEmpty()) {
            return false
        }

        if (normalizedAddress.any(Char::isLetter)) {
            return false
        }

        if (!PhoneNumberUtils.isWellFormedSmsAddress(normalizedAddress)) {
            return false
        }

        return normalizedAddress.any(Char::isDigit)
    }
}
