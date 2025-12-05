package com.bizur.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bizur.android.data.BizurDataState
import com.bizur.android.model.Conversation
import com.bizur.android.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [ContactEntity::class, ConversationEntity::class, MessageEntity::class, CallLogEntity::class],
    version = 4,
    exportSchema = false
)
abstract class BizurDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        private const val DB_NAME = "bizur.db"

        fun build(context: Context, scope: CoroutineScope, seedProvider: () -> BizurDataState): BizurDatabase {
            val database = Room.databaseBuilder(context, BizurDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
            scope.launch {
                if (database.contactDao().count() == 0) {
                    database.seed(seedProvider())
                }
            }
            return database
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE contacts ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentMimeType TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentDisplayName TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN reaction TEXT DEFAULT ''")
            }
        }
    }
}

suspend fun BizurDatabase.seed(state: BizurDataState) {
    contactDao().insertAll(state.contacts.map { it.toEntity() })
    conversationDao().insertAll(state.conversations.values.map(Conversation::toEntity))
    val messageEntities = state.messages.values.flatten().map(Message::toEntity)
    if (messageEntities.isNotEmpty()) {
        messageDao().insertAll(messageEntities)
    }
    callLogDao().insertAll(state.callLogs.map { it.toEntity() })
}
