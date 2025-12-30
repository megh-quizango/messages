package com.quizangomedia.messages.data.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Contact : RealmObject {
    @PrimaryKey
    var phoneNumber: String = ""
    var displayName: String = ""
    var photoUri: String? = null
    var lastContacted: Long = 0
}

