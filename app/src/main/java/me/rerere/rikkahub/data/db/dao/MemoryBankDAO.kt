package me.rerere.rikkahub.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryVectorEntity

@Dao
interface MemoryBankDAO {
    // ===== MemoryBank CRUD =====

    @Insert
    suspend fun insertMemory(memory: MemoryBankEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryBankEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryBankEntity)

    @Query("DELETE FROM memory_bank WHERE id = :id")
    suspend fun deleteMemoryById(id: Int)

    @Query("SELECT * FROM memory_bank WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryBankEntity?

    @Query("SELECT * FROM memory_bank ORDER BY created_at DESC")
    suspend fun getAllMemories(): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type ORDER BY created_at DESC")
    suspend fun getMemoriesByType(type: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type ORDER BY created_at DESC LIMIT :limit")
    suspend fun getMemoriesByTypeLimit(type: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId ORDER BY created_at DESC")
    suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId AND type = :type ORDER BY created_at DESC LIMIT :limit")
    suspend fun getMemoriesByAssistantAndTypeLimit(assistantId: String, type: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId AND type = :type AND date_group = :dateGroup ORDER BY created_at DESC")
    suspend fun getMemoriesByAssistantTypeAndDateGroup(assistantId: String, type: String, dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type AND date_group = :dateGroup ORDER BY created_at DESC")
    suspend fun getMemoriesByTypeAndDateGroup(type: String, dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT DISTINCT assistant_id FROM memory_bank WHERE assistant_id IS NOT NULL")
    suspend fun getDistinctAssistantIds(): List<String>

    @Query("SELECT * FROM memory_bank WHERE date_group = :dateGroup ORDER BY created_at DESC")
    suspend fun getMemoriesByDateGroup(dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE date_group = :dateGroup AND type = :type ORDER BY created_at DESC")
    suspend fun getMemoriesByDateGroupAndType(dateGroup: String, type: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE vector_status = :status")
    suspend fun getMemoriesByVectorStatus(status: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE vector_status = 'pending' AND vector_retry_count < :maxRetry ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingVectorMemories(maxRetry: Int, limit: Int = 50): List<MemoryBankEntity>

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = 'message' AND created_at > :sinceTimestamp")
    suspend fun getMessageCountSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = 'message'")
    suspend fun getTotalMessageCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = 'phase_summary' OR type = 'daily_summary'")
    suspend fun getSummaryCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE vector_status = :status")
    suspend fun getCountByVectorStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId")
    suspend fun getCountByAssistant(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId AND type = :type")
    suspend fun getCountByAssistantAndType(assistantId: String, type: String): Int

    @Query("SELECT * FROM memory_bank ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE content LIKE '%' || :keyword || '%' ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchMemoriesByKeyword(keyword: String, limit: Int = 20): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE content LIKE '%' || :keyword || '%' AND type = :type ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchMemoriesByKeywordAndType(keyword: String, type: String, limit: Int = 20): List<MemoryBankEntity>

    @Query("SELECT DISTINCT date_group FROM memory_bank WHERE date_group IS NOT NULL ORDER BY date_group DESC LIMIT :limit")
    suspend fun getRecentDateGroups(limit: Int): List<String>

    @Query("UPDATE memory_bank SET vector_status = :status, vector_retry_count = :retryCount WHERE id = :id")
    suspend fun updateVectorStatus(id: Int, status: String, retryCount: Int)

    // ===== MemoryVector CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVector(vector: MemoryVectorEntity)

    @Query("SELECT * FROM memory_vector WHERE memory_id = :memoryId")
    suspend fun getVectorByMemoryId(memoryId: Int): MemoryVectorEntity?

    @Query("DELETE FROM memory_vector WHERE memory_id = :memoryId")
    suspend fun deleteVectorByMemoryId(memoryId: Int)

    @Query("""
        SELECT mv.memory_id, mv.vector, mv.dimensions, mv.model, mv.created_at,
               mb.content, mb.type
        FROM memory_vector mv 
        INNER JOIN memory_bank mb ON mv.memory_id = mb.id
    """)
    suspend fun getAllVectorsWithMemories(): List<MemoryVectorAndMemory>

    @Query("SELECT COUNT(*) FROM memory_vector")
    suspend fun getVectorCount(): Int
}

/**
 * 向量和记忆的联合查询结果
 */
data class MemoryVectorAndMemory(
    @ColumnInfo(name = "memory_id") val memoryId: Int,
    val vector: String,
    val dimensions: Int,
    val model: String,
    val content: String,
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
