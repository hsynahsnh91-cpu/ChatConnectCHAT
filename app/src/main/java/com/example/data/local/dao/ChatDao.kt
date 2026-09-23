package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.ChatMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMember(member: ChatMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMembers(members: List<ChatMemberEntity>)

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    fun observeChatById(chatId: String): Flow<ChatEntity?>

    @Query("""
        SELECT c.* FROM chats c
        INNER JOIN chat_members cm ON c.chatId = cm.chatId
        WHERE cm.userId = :userId
        ORDER BY c.lastMessageTimestamp DESC
    """)
    fun getChatsForUser(userId: String): Flow<List<ChatEntity>>

    @Query("""
        SELECT c.* FROM chats c
        INNER JOIN chat_members cm1 ON c.chatId = cm1.chatId
        INNER JOIN chat_members cm2 ON c.chatId = cm2.chatId
        WHERE c.type = 'INDIVIDUAL' AND cm1.userId = :user1Id AND cm2.userId = :user2Id
        LIMIT 1
    """)
    suspend fun findIndividualChatBetween(user1Id: String, user2Id: String): ChatEntity?

    @Query("SELECT * FROM chat_members WHERE chatId = :chatId")
    fun getChatMembers(chatId: String): Flow<List<ChatMemberEntity>>

    @Query("SELECT * FROM chat_members WHERE chatId IN (:chatIds)")
    fun getMembersForChats(chatIds: List<String>): Flow<List<ChatMemberEntity>>

    @Query("SELECT * FROM chat_members WHERE chatId = :chatId")
    suspend fun getChatMembersList(chatId: String): List<ChatMemberEntity>

    @Query("DELETE FROM chat_members WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeChatMember(chatId: String, userId: String)

    @Query("UPDATE chat_members SET role = :role WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateMemberRole(chatId: String, userId: String, role: String)

    @Query("UPDATE chats SET groupName = :name, groupIconUri = :iconUri, groupDescription = :description, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateGroupInfoFull(chatId: String, name: String, iconUri: String?, description: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET groupName = :name, groupIconUri = :iconUri WHERE chatId = :chatId")
    suspend fun updateGroupInfo(chatId: String, name: String, iconUri: String?)

    @Query("UPDATE chats SET groupDescription = :description, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateGroupDescription(chatId: String, description: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET editGroupInfoPermission = :editInfo, sendMessagesPermission = :sendMessages, addMembersPermission = :addMembers, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateGroupPermissions(chatId: String, editInfo: String, sendMessages: String, addMembers: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET groupOwnerId = :newOwnerId, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateGroupOwner(chatId: String, newOwnerId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET inviteToken = :token, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updateInviteToken(chatId: String, token: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET pinnedMessageId = :messageId, updatedAt = :timestamp WHERE chatId = :chatId")
    suspend fun updatePinnedMessage(chatId: String, messageId: String?, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chats WHERE inviteToken = :token LIMIT 1")
    suspend fun getChatByInviteToken(token: String): ChatEntity?

    @Query("SELECT * FROM chat_members WHERE chatId = :chatId AND userId = :userId LIMIT 1")
    suspend fun getMember(chatId: String, userId: String): ChatMemberEntity?

    @Query("UPDATE chat_members SET isMuted = :isMuted WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateMemberMute(chatId: String, userId: String, isMuted: Boolean)

    @Query("UPDATE chat_members SET isArchived = :isArchived WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateMemberArchive(chatId: String, userId: String, isArchived: Boolean)

    @Query("UPDATE chats SET lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessageTime(chatId: String, timestamp: Long)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)
}
