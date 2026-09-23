package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMessageForChat(chatId: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageForChatSync(chatId: String): MessageEntity?

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = :status, uploadProgress = :progress WHERE messageId = :messageId")
    suspend fun updateUploadProgress(messageId: String, status: String, progress: Float)

    @Query("UPDATE messages SET mediaUri = :mediaUri, status = :status, uploadProgress = 1.0 WHERE messageId = :messageId")
    suspend fun updateMessageMediaAndStatus(messageId: String, mediaUri: String, status: String)

    @Query("UPDATE messages SET status = :status, readAt = :readAt WHERE chatId = :chatId AND senderUserId != :currentUserId AND (status != 'READ' OR readAt IS NULL)")
    suspend fun markChatMessagesAsRead(chatId: String, currentUserId: String, status: String = "READ", readAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderUserId != :currentUserId AND (status != 'READ' OR readAt IS NULL)")
    suspend fun getUnreadMessagesForChatSync(chatId: String, currentUserId: String): List<MessageEntity>

    @Query("UPDATE messages SET status = :status, readAt = :readAt WHERE messageId = :messageId")
    suspend fun updateMessageReadReceipt(messageId: String, status: String = "READ", readAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND (type = 'IMAGE' OR type = 'VIDEO') ORDER BY timestamp DESC")
    fun getMediaMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND type = 'FILE' ORDER BY timestamp DESC")
    fun getDocumentMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND (content LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMessagesInChat(chatId: String, query: String): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT chatId FROM messages WHERE content LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%'")
    fun getChatIdsMatchingMessageContent(query: String): Flow<List<String>>

    @Query("SELECT DISTINCT chatId FROM messages WHERE content LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%'")
    suspend fun getChatIdsMatchingMessageContentSync(query: String): List<String>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessagesAcrossAllChats(query: String): Flow<List<MessageEntity>>
}
