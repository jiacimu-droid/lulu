package me.rerere.rikkahub.data.db.entity

import androidx.room.Entity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphEdgeEntityTest {
    @Test
    fun `entity declares the indices created by migration 25 to 26`() {
        val entity = MemoryGraphEdgeEntity::class.java.getAnnotation(Entity::class.java)
        val indices = entity.indices.associateBy { it.name }

        assertTrue(indices.containsKey("index_memory_graph_edge_source_weight"))
        assertEquals(
            listOf("source_memory_id", "weight"),
            indices.getValue("index_memory_graph_edge_source_weight").value.toList(),
        )
        assertTrue(indices.containsKey("index_memory_graph_edge_target"))
        assertEquals(
            listOf("target_memory_id"),
            indices.getValue("index_memory_graph_edge_target").value.toList(),
        )
    }
}
