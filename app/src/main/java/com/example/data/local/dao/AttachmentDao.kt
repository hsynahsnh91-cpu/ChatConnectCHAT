package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Update
    suspend fun updateAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getAttachmentsForMessage(messageId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    fun observeAttachmentsForMessage(messageId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun getAttachmentsForChat(chatId: String): Flow<List<AttachmentEntity>>

    @Query("UPDATE attachments SET uploadProgress = :progress, uploadStatus = :status WHERE attachmentId = :attachmentId")
    suspend fun updateProgress(attachmentId: String, progress: Float, status: String)

    @Query("DELETE FROM attachments WHERE attachmentId = :attachmentId")
    suspend fun deleteAttachment(attachmentId: String)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: String)
}
