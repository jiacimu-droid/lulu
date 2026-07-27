package me.rerere.rikkahub.ui.pages.setting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig

internal const val MCDONALDS_MCP_NAME = "麦当劳 MCP"
internal const val MCDONALDS_MCP_URL = "https://mcp.mcd.cn"

internal fun normalizeMcdonaldsMcpAuthValue(token: String): String {
    val value = token.trim()
    return if (value.startsWith("Bearer ", ignoreCase = true)) value else "Bearer $value"
}

internal fun McpServerConfig.isMcdonaldsMcp(): Boolean =
    commonOptions.name == MCDONALDS_MCP_NAME ||
        (this is McpServerConfig.StreamableHTTPServer && url == MCDONALDS_MCP_URL)

internal fun upsertMcdonaldsMcpServer(
    servers: List<McpServerConfig>,
    token: String,
): List<McpServerConfig> {
    val existing = servers.firstOrNull { it.isMcdonaldsMcp() }
    val commonOptions = (existing?.commonOptions ?: McpCommonOptions()).copy(
        enable = true,
        name = MCDONALDS_MCP_NAME,
        headers = listOf("Authorization" to normalizeMcdonaldsMcpAuthValue(token)),
    )
    val config = when (existing) {
        is McpServerConfig.StreamableHTTPServer -> existing.copy(
            commonOptions = commonOptions,
            url = MCDONALDS_MCP_URL,
        )
        null -> McpServerConfig.StreamableHTTPServer(
            commonOptions = commonOptions,
            url = MCDONALDS_MCP_URL,
        )
        else -> McpServerConfig.StreamableHTTPServer(
            id = existing.id,
            commonOptions = commonOptions,
            url = MCDONALDS_MCP_URL,
        )
    }
    return if (existing == null) {
        servers + config
    } else {
        servers.map { server -> if (server.id == existing.id) config else server }
    }
}

internal fun parseMcpServersFromJson(json: String): List<McpServerConfig> {
    val root = Json.parseToJsonElement(json).jsonObject
    val mcpServers = root["mcpServers"]?.jsonObject ?: return emptyList()
    return mcpServers.entries.mapNotNull { (name, element) ->
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "streamable_http"
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val headers = obj["headers"]?.jsonObject?.entries?.map { (key, value) ->
            key to (value.jsonPrimitive.contentOrNull ?: "")
        } ?: emptyList()
        val commonOptions = McpCommonOptions(name = name, headers = headers)
        when (type) {
            "sse" -> McpServerConfig.SseTransportServer(commonOptions = commonOptions, url = url)
            else -> McpServerConfig.StreamableHTTPServer(commonOptions = commonOptions, url = url)
        }
    }
}
