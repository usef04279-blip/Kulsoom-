package com.example.data.local

import androidx.room.*
import com.example.data.model.AssistantNote
import com.example.data.model.AssistantReminder
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryFact
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM assistant_memories WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getMemoriesForProfile(profileId: Long): Flow<List<MemoryFact>>

    @Query("SELECT * FROM assistant_memories WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getMemoriesListForProfile(profileId: Long): List<MemoryFact>

    @Query("SELECT * FROM assistant_memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryFact>>

    @Query("SELECT * FROM assistant_memories ORDER BY createdAt DESC")
    suspend fun getAllMemoriesList(): List<MemoryFact>

    @Query("SELECT * FROM assistant_memories WHERE (profileId = :profileId OR :profileId = 0) AND (factKey LIKE '%' || :query || '%' OR factValue LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchMemories(profileId: Long, query: String): Flow<List<MemoryFact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryFact): Long

    @Update
    suspend fun updateMemory(memory: MemoryFact)

    @Delete
    suspend fun deleteMemory(memory: MemoryFact)

    @Query("DELETE FROM assistant_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM assistant_memories WHERE (profileId = :profileId OR :profileId = 0) AND (factKey LIKE '%' || :keyQuery || '%' OR factValue LIKE '%' || :keyQuery || '%')")
    suspend fun deleteMemoriesByQuery(profileId: Long, keyQuery: String): Int

    @Query("DELETE FROM assistant_memories WHERE profileId = :profileId")
    suspend fun deleteMemoriesByProfile(profileId: Long)

    @Query("DELETE FROM assistant_memories")
    suspend fun clearAllMemories()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE hasVoiceprint = 1")
    suspend fun getProfilesWithVoiceprint(): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile): Long

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Delete
    suspend fun deleteProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE user_profiles SET voiceprintEmbedding = '', hasVoiceprint = 0 WHERE id = :id")
    suspend fun clearVoiceprint(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE profileId = :profileId OR (:profileId = 0 AND profileId = 0) ORDER BY timestamp DESC")
    fun getMessagesForProfile(profileId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE (profileId = :profileId OR :profileId = 0) AND text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(profileId: Long, query: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_messages WHERE profileId = :profileId")
    suspend fun clearMessagesForProfile(profileId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM assistant_notes WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getNotesForProfile(profileId: Long): Flow<List<AssistantNote>>

    @Query("SELECT * FROM assistant_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<AssistantNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: AssistantNote): Long

    @Delete
    suspend fun deleteNote(note: AssistantNote)

    @Query("DELETE FROM assistant_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM assistant_notes WHERE profileId = :profileId")
    suspend fun deleteNotesByProfile(profileId: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM assistant_reminders WHERE profileId = :profileId ORDER BY triggerTimeMillis ASC")
    fun getRemindersForProfile(profileId: Long): Flow<List<AssistantReminder>>

    @Query("SELECT * FROM assistant_reminders ORDER BY triggerTimeMillis ASC")
    fun getAllReminders(): Flow<List<AssistantReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: AssistantReminder): Long

    @Update
    suspend fun updateReminder(reminder: AssistantReminder)

    @Delete
    suspend fun deleteReminder(reminder: AssistantReminder)

    @Query("DELETE FROM assistant_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM assistant_reminders WHERE profileId = :profileId")
    suspend fun deleteRemindersByProfile(profileId: Long)
}

@Database(
    entities = [UserProfile::class, ChatMessage::class, AssistantNote::class, AssistantReminder::class, MemoryFact::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kulsoom_assistant.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
