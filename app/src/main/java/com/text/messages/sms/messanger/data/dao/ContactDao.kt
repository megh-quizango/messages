package com.text.messages.sms.messanger.data.dao

import androidx.room.*
import com.text.messages.sms.messanger.data.model.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    
    @Query("SELECT * FROM contacts ORDER BY lastContacted DESC")
    fun getAllContacts(): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
    
    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    fun getContactByPhoneNumberFlow(phoneNumber: String): Flow<Contact?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>)
    
    @Update
    suspend fun updateContact(contact: Contact)
    
    @Query("UPDATE contacts SET lastContacted = :timestamp WHERE phoneNumber = :phoneNumber")
    suspend fun updateLastContacted(phoneNumber: String, timestamp: Long)
    
    @Query("DELETE FROM contacts WHERE phoneNumber = :phoneNumber")
    suspend fun deleteContact(phoneNumber: String)
}

