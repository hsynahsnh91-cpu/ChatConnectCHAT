package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.MessageReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageReactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: MessageReactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReactions(reactions: List<MessageReactionEntity>)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId AND userId = :userId")
    suspend fun deleteReaction(messageId: String, userId: String)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId")
    suspend fun deleteReactionsForMessage(messageId: String)

    @Query("SELECT * FROM message_reactions WHERE messageId = :messageId ORDER BY timestamp ASC")
    fun getReactionsForMessage(messageId: String): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE messageId = :messageId ORDER BY timestamp ASC")
    suspend fun getReactionsForMessageSync(messageId: String): List<MessageReactionEntity>

    @Query("SELECT * FROM message_reactions WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getReactionsForChat(chatId: String): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getReactionsForChatSync(chatId: String): List<MessageReactionEntity>
}
