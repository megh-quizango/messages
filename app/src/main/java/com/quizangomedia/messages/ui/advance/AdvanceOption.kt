package com.quizangomedia.messages.ui.advance

data class AdvanceOption(
    val type: AdvanceOptionType,
    val iconRes: Int?,
    val title: String,
    val detail: String?,
    val hasToggle: Boolean = false,
    val toggleState: Boolean = false
)

enum class AdvanceOptionType {
    DELAYED_SENDING,
    DELETE_OLD_MESSAGES,
    DELIVERY_CONFIRMATIONS,
    STRIP_ACCENTS,
    MOBILE_NUMBER_ONLY,
    SEND_LONG_AS_MMS
}

