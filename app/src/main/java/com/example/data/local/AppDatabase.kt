package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*

@Database(
    entities = [
        UserEntity::class,
        ContactEntity::class,
        ChatEntity::class,
        ChatMemberEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        AuthIdentityEntity::class,
        StatusEntity::class,
        StatusViewEntity::class,
        StatusMuteEntity::class,
        CallLogEntity::class,
        BlockedUserEntity::class,
        UserSettingsEntity::class,
        DeviceSessionEntity::class,
        SpecialIdentifierEntity::class,
        MessageReactionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun messageReactionDao(): MessageReactionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun authIdentityDao(): AuthIdentityDao
    abstract fun statusDao(): StatusDao
    abstract fun callDao(): CallDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun deviceSessionDao(): DeviceSessionDao
    abstract fun specialIdentifierDao(): SpecialIdentifierDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatconnect_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
