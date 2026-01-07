package com.text.messages.sms.messanger.ui.notifications

data class NotificationOption(
    val type: NotificationOptionType,
    val iconRes: Int?,
    val title: String,
    val detail: String?,
    val hasToggle: Boolean = false,
    val toggleState: Boolean = false
)

enum class NotificationOptionType {
    NOTIFICATIONS,
    NOTIFICATION_PREVIEWS,
    WAKE_SCREEN,
    ACTIONS_HEADING,
    BUTTON_1,
    BUTTON_2,
    BUTTON_3
}

