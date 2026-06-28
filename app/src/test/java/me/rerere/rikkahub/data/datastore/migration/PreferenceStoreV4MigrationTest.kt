package me.rerere.rikkahub.data.datastore.migration

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV4MigrationTest {
    @Test
    fun `migration should enable Claude prompt caching when field is missing`() {
        val migrated = enableClaudePromptCaching(
            """
                [
                  {
                    "type": "claude",
                    "id": "provider-1",
                    "name": "Claude"
                  }
                ]
            """.trimIndent()
        )

        val provider = JsonInstant.parseToJsonElement(migrated).jsonArray[0].jsonObject
        assertTrue(provider["promptCaching"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `migration should enable Claude prompt caching when it was disabled`() {
        val migrated = enableClaudePromptCaching(
            """
                [
                  {
                    "type": "claude",
                    "id": "provider-1",
                    "name": "Claude",
                    "promptCaching": false
                  }
                ]
            """.trimIndent()
        )

        val provider = JsonInstant.parseToJsonElement(migrated).jsonArray[0].jsonObject
        assertTrue(provider["promptCaching"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `migration should not modify non Claude providers`() {
        val migrated = enableClaudePromptCaching(
            """
                [
                  {
                    "type": "openai",
                    "id": "provider-1",
                    "name": "OpenAI"
                  }
                ]
            """.trimIndent()
        )

        val provider = JsonInstant.parseToJsonElement(migrated).jsonArray[0].jsonObject
        assertEquals(null, provider["promptCaching"])
    }
}
