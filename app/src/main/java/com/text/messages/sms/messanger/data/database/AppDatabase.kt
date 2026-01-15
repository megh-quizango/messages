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
    version = 2,
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

        // Migration from version 1 to 2: Add lastOtp column to conversations table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN lastOtp TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Fallback if migration fails
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

