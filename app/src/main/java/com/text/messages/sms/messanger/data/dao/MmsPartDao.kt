package com.text.messages.sms.messanger.data.dao

import androidx.room.*
import com.text.messages.sms.messanger.data.model.MmsPart

@Dao
interface MmsPartDao {
    
    @Query("SELECT * FROM mms_parts WHERE messageId = :messageId ORDER BY seq ASC")
    suspend fun getPartsByMessageId(messageId: Long): List<MmsPart>
    
    @Query("SELECT * FROM mms_parts WHERE id = :partId LIMIT 1")
    suspend fun getPartById(partId: Long): MmsPart?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: MmsPart): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<MmsPart>)
    
    @Update
    suspend fun updatePart(part: MmsPart)
    
    @Query("DELETE FROM mms_parts WHERE messageId = :messageId")
    suspend fun deletePartsByMessageId(messageId: Long)
    
    @Query("DELETE FROM mms_parts WHERE id = :partId")
    suspend fun deletePart(partId: Long)
}

