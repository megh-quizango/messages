package com.text.messages.sms.messanger.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.text.messages.sms.messanger.data.dao.ContactDao
import com.text.messages.sms.messanger.data.dao.ConversationDao
import com.text.messages.sms.messanger.data.dao.MessageDao
import com.text.messages.sms.messanger.data.dao.MmsPartDao
import com.text.messages.sms.messanger.data.model.Contact
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MmsPart

@Database(
    entities = [Message::class, Conversation::class, Contact::class, MmsPart::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun mmsPartDao(): MmsPartDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private const val DATABASE_NAME = "messages_database"
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // For now, allow destructive migration from Realm
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

