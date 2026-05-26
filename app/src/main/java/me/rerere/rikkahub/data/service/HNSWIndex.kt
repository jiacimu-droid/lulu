package me.rerere.rikkahub.data.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 轻量级 HNSW (Hierarchical Navigable Small World) 向量索引
 * 纯 Kotlin 实现，支持持久化到本地文件
 * 用于记忆库的本地向量近似最近邻搜索
 */
class HNSWIndex(
    private val context: Context,
    private val dimensions: Int = 1536,
    private val maxConnections: Int = 16,   // M: 每层最大连接数
    private val efConstruction: Int = 200,  // 构建时的搜索宽度
    private val maxLevel: Int = 4          // 最大层数
) {
    companion object {
        private const val TAG = "HNSWIndex"
        private const val INDEX_FILE = "memory_hnsw_index.json"
    }

    // 节点数据
    data class Node(
        val id: Int,
        val vector: FloatArray,
        var level: Int = 0,
        val maxLevels: Int = 16,
        val neighbors: Array<MutableList<Int>> = Array(maxLevels) { mutableListOf() }
    ) {
        override fun equals(other: Any?): Boolean = other is Node && id == other.id
        override fun hashCode(): Int = id
    }

    // 搜索结果
    data class SearchResult(
        val id: Int,
        val score: Double  // 余弦相似度
    )

    private val nodes = ConcurrentHashMap<Int, Node>()
    private var entryPoint: Int = -1
    private val rand = java.util.Random()

    private val indexFile: File
        get() = File(context.filesDir, INDEX_FILE)

    /**
     * 添加向量到索引
     */
    suspend fun add(id: Int, vector: FloatArray) = withContext(Dispatchers.Default) {
        if (vector.size != dimensions) {
            Log.w(TAG, "Vector dimension mismatch: expected $dimensions, got ${vector.size}")
            return@withContext
        }

        val node = Node(id, vector, level = randomLevel(), maxLevels = maxLevel + 1)
        nodes[id] = node

        if (entryPoint == -1) {
            entryPoint = id
            return@withContext
        }

        // 从入口点开始搜索最近邻
        var current = entryPoint
        for (level in node.level downTo 0) {
            val nearest = searchLevel(node.vector, current, efConstruction, level)
            val neighbors = nearest.take(maxConnections)
            node.neighbors[level].addAll(neighbors.map { it.id })

            // 双向连接
            for (neighbor in neighbors) {
                val neighborNode = nodes[neighbor.id] ?: continue
                neighborNode.neighbors[level].add(id)
                // 如果超过最大连接数，保留最近邻
                if (neighborNode.neighbors[level].size > maxConnections) {
                    val kept = searchLevel(neighborNode.vector, neighborNode.id, maxConnections, level)
                        .map { it.id }
                    neighborNode.neighbors[level].clear()
                    neighborNode.neighbors[level].addAll(kept)
                }
            }

            if (level > 0) {
                current = nearest.first().id
            }
        }

        // 如果新节点层级更高，更新入口点
        if (node.level > (nodes[entryPoint]?.level ?: 0)) {
            entryPoint = id
        }
    }

    /**
     * 搜索最相似的向量
     * @param query 查询向量
     * @param k 返回数量
     * @param ef 搜索宽度（越大越精确但越慢）
     * @return 搜索结果列表，按相似度降序
     */
    suspend fun search(query: FloatArray, k: Int = 5, ef: Int = 50): List<SearchResult> =
        withContext(Dispatchers.Default) {
            if (nodes.isEmpty() || entryPoint == -1) {
                return@withContext emptyList()
            }

            if (query.size != dimensions) {
                Log.w(TAG, "Query dimension mismatch: expected $dimensions, got ${query.size}")
                return@withContext emptyList()
            }

            // 从最高层向下搜索入口
            var current = entryPoint
            val topLevel = nodes[entryPoint]?.level ?: 0

            for (level in topLevel downTo 1) {
                val result = searchLevel(query, current, 1, level)
                current = result.firstOrNull()?.id ?: current
            }

            // 在第0层进行详细搜索
            val results = searchLevel(query, current, maxOf(ef, k), 0)
            return@withContext results.take(k)
        }

    /**
     * 在指定层搜索最近邻（贪心搜索）
     */
    private fun searchLevel(
        query: FloatArray,
        entryId: Int,
        k: Int,
        level: Int
    ): List<SearchResult> {
        val visited = mutableSetOf<Int>()
        val candidates = mutableListOf<SearchResult>()
        val results = mutableListOf<SearchResult>()

        val entryNode = nodes[entryId] ?: return emptyList()
        val entryScore = cosineSimilarity(query, entryNode.vector)

        candidates.add(SearchResult(entryId, entryScore))
        results.add(SearchResult(entryId, entryScore))
        visited.add(entryId)

        while (candidates.isNotEmpty()) {
            // 取出最近的候选
            val nearest = candidates.minByOrNull { -it.score } ?: break
            candidates.remove(nearest)

            // 取出结果中最远的
            val furthest = results.maxByOrNull { -it.score } ?: break

            if (nearest.score < furthest.score && results.size >= k) break

            // 扩展邻居
            val node = nodes[nearest.id] ?: continue
            for (neighborId in node.neighbors.getOrElse(level) { emptyList() }) {
                if (neighborId in visited) continue
                visited.add(neighborId)

                val neighborNode = nodes[neighborId] ?: continue
                val score = cosineSimilarity(query, neighborNode.vector)

                val furthestResult = results.maxByOrNull { -it.score }
                if (results.size < k || score > (furthestResult?.score ?: 0.0)) {
                    candidates.add(SearchResult(neighborId, score))
                    results.add(SearchResult(neighborId, score))
                    if (results.size > k * 2) {
                        // 保留 top-k
                        val sorted = results.sortedByDescending { it.score }.take(k)
                        results.clear()
                        results.addAll(sorted)
                    }
                }
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * 从索引中删除节点
     */
    suspend fun remove(id: Int) = withContext(Dispatchers.Default) {
        val node = nodes.remove(id) ?: return@withContext
        // 清除其他节点对该节点的引用
        for (level in 0..maxLevel) {
            for (otherNode in nodes.values) {
                otherNode.neighbors[level].remove(id)
            }
        }
        if (entryPoint == id) {
            entryPoint = nodes.keys.firstOrNull() ?: -1
        }
    }

    /**
     * 获取索引大小
     */
    fun size(): Int = nodes.size

    /**
     * 检查索引是否包含指定ID
     */
    fun contains(id: Int): Boolean = nodes.containsKey(id)

    /**
     * 清空索引
     */
    fun clear() {
        nodes.clear()
        entryPoint = -1
    }

    /**
     * 持久化索引到文件
     */
    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            val json = JSONArray()
            for (node in nodes.values) {
                val nodeJson = JSONArray().apply {
                    put(node.id)
                    put(node.level)
                    // 向量
                    val vecJson = JSONArray()
                    for (v in node.vector) vecJson.put(v.toDouble())
                    put(vecJson)
                    // 邻居
                    for (level in 0..node.level) {
                        val neighborJson = JSONArray()
                        for (n in node.neighbors[level]) neighborJson.put(n)
                        put(neighborJson)
                    }
                }
                json.put(nodeJson)
            }
            indexFile.writeText(json.toString())
            Log.d(TAG, "HNSW index saved: ${nodes.size} nodes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save HNSW index", e)
        }
    }

    /**
     * 从文件加载索引
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        val indexMaxLevel = this@HNSWIndex.maxLevel
        try {
            if (!indexFile.exists()) {
                Log.d(TAG, "No HNSW index file found")
                return@withContext
            }

            nodes.clear()
            entryPoint = -1

            val json = JSONArray(indexFile.readText())
            var topLevel = -1

            for (i in 0 until json.length()) {
                val nodeJson = json.getJSONArray(i)
                val id = nodeJson.getInt(0)
                val level = nodeJson.getInt(1)

                val vecJson = nodeJson.getJSONArray(2)
                val vector = FloatArray(dimensions) { j ->
                    if (j < vecJson.length()) vecJson.getDouble(j).toFloat() else 0f
                }

                val node = Node(id, vector, level, maxLevels = indexMaxLevel + 1)
                for (l in 0..level) {
                    val baseIdx = 3 + l
                    if (baseIdx < nodeJson.length()) {
                        val neighborJson = nodeJson.getJSONArray(baseIdx)
                        for (j in 0 until neighborJson.length()) {
                            node.neighbors[l].add(neighborJson.getInt(j))
                        }
                    }
                }

                nodes[id] = node
                if (level > topLevel) {
                    topLevel = level
                    entryPoint = id
                }
            }

            Log.d(TAG, "HNSW index loaded: ${nodes.size} nodes, entry=$entryPoint")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load HNSW index", e)
            nodes.clear()
            entryPoint = -1
        }
    }

    /**
     * 随机分配层级
     */
    private fun randomLevel(): Int {
        var level = 0
        while (rand.nextDouble() < 0.5 && level < maxLevel) {
            level++
        }
        return level
    }

    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}