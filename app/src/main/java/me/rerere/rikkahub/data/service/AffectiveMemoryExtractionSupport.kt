package me.rerere.rikkahub.data.service

internal fun String.extractJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) return fenced

    val start = listOf(trimmed.indexOf('{'), trimmed.indexOf('['))
        .filter { it >= 0 }
        .minOrNull()
        ?: return trimmed
    val end = maxOf(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'))
    return if (end >= start) trimmed.substring(start, end + 1) else trimmed
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
