package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆库条目实体
 * 存储每条记忆的元数据和内容
 */
@Entity(tableName = "memory_bank")
data class MemoryBankEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    
    /** 记忆内容 */
    @ColumnInfo("content")
    val content: String = "",
    
    /** 记忆类型: message, phase_summary, daily_summary, manual */
    @ColumnInfo("type")
    val type: String = "message",
    
    /** 关联的对话ID（可选） */
    @ColumnInfo("conversation_id")
    val conversationId: String? = null,
    
    /** 关联的助手ID */
    @ColumnInfo("assistant_id")
    val assistantId: String? = null,
    
    /** 消息角色: user, assistant */
    @ColumnInfo("role")
    val role: String? = null,
    
    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 所属日期（用于每日总结分组），格式 yyyy-MM-dd */
    @ColumnInfo("date_group")
    val dateGroup: String? = null,
    
    /** 向量化状态: pending, done, failed */
    @ColumnInfo("vector_status")
    val vectorStatus: String = "pending",
    
    /** 向量化重试次数 */
    @ColumnInfo("vector_retry_count")
    val vectorRetryCount: Int = 0,
)

/**
 * 记忆向量实体
 * 存储每条记忆的向量表示，用于相似度搜索
 */
@Entity(tableName = "memory_vector", primaryKeys = ["memory_id"])
data class MemoryVectorEntity(
    /** 关联的记忆ID */
    @ColumnInfo("memory_id")
    val memoryId: Int,
    
    /** 向量数据，以逗号分隔的浮点数字符串 */
    @ColumnInfo("vector")
    val vector: String = "",
    
    /** 向量维度 */
    @ColumnInfo("dimensions")
    val dimensions: Int = 0,
    
    /** 向量化模型名称 */
    @ColumnInfo("model")
    val model: String = "",
    
    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)