package me.rerere.ai.provider.providers.openai

import me.rerere.ai.provider.ProviderSetting

private const val OPENAI_API_HOST = "api.openai.com"

internal fun shouldSendOpenAIPromptCacheKey(host: String): Boolean =
    host == OPENAI_API_HOST

internal fun ProviderSetting.OpenAI.promptCacheKey(): String =
    "provider:$id"
