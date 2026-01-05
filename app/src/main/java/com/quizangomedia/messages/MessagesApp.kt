package com.quizangomedia.messages

import android.app.Application
import com.quizangomedia.messages.data.model.Contact
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.data.model.Message
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

class MessagesApp : Application() {
    
    companion object {
        lateinit var realm: Realm
            private set
        lateinit var instance: MessagesApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        initRealmSafely()
        com.quizangomedia.messages.util.NotificationHelper.initialize(this)
    }
    
    private fun initRealmSafely() {
        try {
            val config = RealmConfiguration.Builder(
                schema = setOf(
                    Message::class,
                    Conversation::class,
                    Contact::class
                )
            )
                .schemaVersion(14)
                .build()
            
            realm = Realm.open(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

