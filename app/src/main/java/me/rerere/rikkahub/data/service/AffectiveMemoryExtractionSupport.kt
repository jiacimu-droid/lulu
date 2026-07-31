package me.rerere.rikkahub.data.service

internal fun String.extractJsonPayload(): String {
    val cleaned = trim()
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .trim()

    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    val source = fenced?.takeIf(String::isNotBlank) ?: cleaned
    return source.firstBalancedJsonValue() ?: source
}

/**
 * Returns the first complete JSON object or array while respecting quoted strings and escapes.
 * This accepts provider responses such as reasoning text + JSON + a short explanation without
 * accidentally taking text after the closing brace into the decoder.
 */
private fun String.firstBalancedJsonValue(): String? {
    val start = indices.firstOrNull { index -> this[index] == '{' || this[index] == '[' } ?: return null
    val stack = java.util.ArrayDeque<Char>()
    var inString = false
    var escaped = false

    for (index in start until length) {
        val character = this[index]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
            continue
        }

        when (character) {
            '"' -> inString = true
            '{' -> stack.addLast('}')
            '[' -> stack.addLast(']')
            '}', ']' -> {
                if (stack.isEmpty() || stack.removeLast() != character) return null
                if (stack.isEmpty()) return substring(start, index + 1).trim()
            }
        }
    }
    return null
}

internal const val MAX_MEMORIES_PER_BATCH = 6

internal val DETERMINISTIC_BOUNDARY_MARKERS = listOf(
    "我不希望",
    "我不喜欢",
    "不要再",
    "别再",
    "不许",
)

internal val DETERMINISTIC_CORRECTION_MARKERS = listOf(
    "不是这样的",
    "不是这个意思",
    "应该是",
    "纠正一下",
    "更正一下",
    "你理解错了",
)

internal val DETERMINISTIC_PREFERENCE_MARKERS = listOf(
    "我更喜欢",
    "我喜欢",
    "我希望",
    "我想要",
    "对我来说",
)

internal val TRANSIENT_MEMORY_FAILURE_CLASS_MARKERS = listOf(
    "timeout",
    "connect",
    "rateLimit",
    "tooManyRequests",
    "serviceUnavailable",
)

internal val DURABLE_MEMORY_TYPES = setOf(
    "user_fact",
    "user_preference",
    "user_boundary",
    "promise",
    "relationship",
    "shared_event",
    "correction",
)

internal val FIRST_PERSON_PREFIXES = listOf(
    "我",
    "咱",
    "本人",
    "本小姐",
    "本少爷",
    "本官",
    "本王",
    "本宫",
    "在下",
    "余",
    "吾",
    "I ",
    "I'm ",
    "I’m ",
)

internal val GENERIC_META_MEMORY_MARKERS = listOf(
    "cihai_reflection",
    "我记得这件事。当时感觉",
    "复盘、收束、准备下一轮",
    "后续可复用的长期记忆",
    "感知世界包",
    "意义评估",
    "动态判断",
    "状态生成",
    "辞海记忆架构",
    "七层架构",
    "下一轮判断",
    "我完成了沉淀",
    "我整理了记忆",
    "以后可以参考",
    "等待下一次",
)
