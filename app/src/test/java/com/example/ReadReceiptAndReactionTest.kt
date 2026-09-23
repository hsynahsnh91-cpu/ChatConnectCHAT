package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.MessageReactionEntity
import com.example.data.local.entities.UserEntity
import com.example.service.FirestoreService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadReceiptAndReactionTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMessageReadAtTimestampAndReceiptStatus() = runBlocking {
        val messageDao = db.messageDao()
        val chatId = "chat_test_101"
        val userSender = "user_sender_A"
        val userRecipient = "user_recipient_B"

        // Sender A sends a message to Chat 101
        val sentTimestamp = System.currentTimeMillis() - 60000
        val message = MessageEntity(
            messageId = "msg_001",
            chatId = chatId,
            senderUserId = userSender,
            type = "TEXT",
            content = "مرحباً كيف حالك؟",
            timestamp = sentTimestamp,
            status = "SENT",
            readAt = null
        )
        messageDao.insertMessage(message)

        // Verify initial state: status is SENT, readAt is null
        val storedMsg = messageDao.getMessageById("msg_001")
        assertNotNull(storedMsg)
        assertEquals("SENT", storedMsg!!.status)
        assertNull(storedMsg.readAt)

        // Verify unread messages query returns this message for recipient B
        val unreadBefore = messageDao.getUnreadMessagesForChatSync(chatId, userRecipient)
        assertEquals(1, unreadBefore.size)
        assertEquals("msg_001", unreadBefore[0].messageId)

        // Recipient B opens chat -> marks messages as READ with current timestamp
        val readAtTimestamp = System.currentTimeMillis()
        messageDao.markChatMessagesAsRead(chatId, userRecipient, "READ", readAtTimestamp)

        // Verify message has updated status and readAt timestamp
        val readMsg = messageDao.getMessageById("msg_001")
        assertNotNull(readMsg)
        assertEquals("READ", readMsg!!.status)
        assertEquals(readAtTimestamp, readMsg.readAt)

        // Verify unread query now returns empty
        val unreadAfter = messageDao.getUnreadMessagesForChatSync(chatId, userRecipient)
        assertTrue(unreadAfter.isEmpty())

        // Double blue tick logic evaluation
        val isDoubleBlueTick = readMsg.status == "READ" || readMsg.readAt != null
        assertTrue("Message should satisfy double blue tick criteria", isDoubleBlueTick)
    }

    @Test
    fun testMessageReactionSubCollectionAndSync() = runBlocking {
        val reactionDao = db.messageReactionDao()
        val chatId = "chat_test_202"
        val messageId = "msg_002"
        val user1 = "user_1"
        val user2 = "user_2"

        // User 1 reacts with "❤️"
        val reaction1 = MessageReactionEntity(
            messageId = messageId,
            chatId = chatId,
            userId = user1,
            username = "أحمد",
            emoji = "❤️",
            timestamp = System.currentTimeMillis()
        )
        reactionDao.insertReaction(reaction1)

        // User 2 reacts with "👍"
        val reaction2 = MessageReactionEntity(
            messageId = messageId,
            chatId = chatId,
            userId = user2,
            username = "سارة",
            emoji = "👍",
            timestamp = System.currentTimeMillis()
        )
        reactionDao.insertReaction(reaction2)

        // Verify reactions stored for message
        val reactionsForMsg = reactionDao.getReactionsForMessageSync(messageId)
        assertEquals(2, reactionsForMsg.size)
        assertTrue(reactionsForMsg.any { it.userId == user1 && it.emoji == "❤️" })
        assertTrue(reactionsForMsg.any { it.userId == user2 && it.emoji == "👍" })

        // User 1 changes reaction from "❤️" to "🔥"
        val updatedReaction1 = reaction1.copy(emoji = "🔥")
        reactionDao.insertReaction(updatedReaction1)

        val updatedReactions = reactionDao.getReactionsForMessageSync(messageId)
        assertEquals(2, updatedReactions.size)
        val user1Reaction = updatedReactions.first { it.userId == user1 }
        assertEquals("🔥", user1Reaction.emoji)

        // User 1 removes reaction (taps same emoji again)
        reactionDao.deleteReaction(messageId, user1)
        val remainingReactions = reactionDao.getReactionsForMessageSync(messageId)
        assertEquals(1, remainingReactions.size)
        assertEquals(user2, remainingReactions[0].userId)

        // Clean up message reactions
        reactionDao.deleteReactionsForMessage(messageId)
        val emptyReactions = reactionDao.getReactionsForMessageSync(messageId)
        assertTrue(emptyReactions.isEmpty())
    }

    @Test
    fun testFirestoreServiceMessageMappingWithReadAt() {
        val map = mapOf<String, Any?>(
            "chatId" to "c123",
            "senderUserId" to "u456",
            "type" to "TEXT",
            "content" to "رسالة تجريبية",
            "timestamp" to 1700000000000L,
            "status" to "READ",
            "readAt" to 1700000050000L
        )

        val msg = FirestoreService.mapDocToMessage("doc_msg_1", map)
        assertNotNull(msg)
        assertEquals("doc_msg_1", msg.messageId)
        assertEquals("c123", msg.chatId)
        assertEquals("u456", msg.senderUserId)
        assertEquals("READ", msg.status)
        assertEquals(1700000050000L, msg.readAt)
    }

    @Test
    fun testDoubleBlueTickUiConditionLogic() {
        // Test various message states and tick evaluation
        val sentMsg = MessageEntity(
            messageId = "m1", chatId = "c1", senderUserId = "me",
            type = "TEXT", content = "test", timestamp = 1000L,
            status = "SENT", readAt = null
        )
        assertFalse(sentMsg.status == "READ" || sentMsg.readAt != null)

        val deliveredMsg = MessageEntity(
            messageId = "m2", chatId = "c1", senderUserId = "me",
            type = "TEXT", content = "test", timestamp = 1000L,
            status = "DELIVERED", readAt = null
        )
        assertFalse(deliveredMsg.status == "READ" || deliveredMsg.readAt != null)
        assertEquals("DELIVERED", deliveredMsg.status)

        val readStatusMsg = MessageEntity(
            messageId = "m3", chatId = "c1", senderUserId = "me",
            type = "TEXT", content = "test", timestamp = 1000L,
            status = "READ", readAt = 1050L
        )
        assertTrue(readStatusMsg.status == "READ" || readStatusMsg.readAt != null)

        val readAtOnlyMsg = MessageEntity(
            messageId = "m4", chatId = "c1", senderUserId = "me",
            type = "TEXT", content = "test", timestamp = 1000L,
            status = "DELIVERED", readAt = 1060L
        )
        // If readAt timestamp is present, it counts as READ (double blue ticks)
        assertTrue(readAtOnlyMsg.status == "READ" || readAtOnlyMsg.readAt != null)
    }
}
