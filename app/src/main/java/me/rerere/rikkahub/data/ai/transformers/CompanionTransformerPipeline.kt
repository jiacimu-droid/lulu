package me.rerere.rikkahub.data.ai.transformers

internal val companionInputTransformers: List<InputMessageTransformer> = listOf(
    // Remove transient snapshots accidentally retained by previous turns.
    CompanionContextDedupTransformer,
    TimeReminderTransformer,
    PromptInjectionTransformer,
    CompanionPresenceContractTransformer,
    StudyStateTransformer,
    PlaceholderTransformer,
    DocumentAsPromptTransformer,
    OcrTransformer,
    VoiceMessageTransformer,
    // Compact the newly generated runtime by semantic fields, then keep only the
    // newest snapshot of every transient context kind.
    CompanionRuntimeCompactTransformer,
    CompanionContextDedupTransformer,
    // Emergency ceiling only; source filtering and deduplication do the real work.
    CompanionFinalBudgetTransformer,
)

internal val companionOutputTransformers: List<OutputMessageTransformer> = listOf(
    ThinkTagTransformer,
    Base64ImageToLocalFileTransformer,
    RegexOutputTransformer,
    LuluExpressionOutputTransformer,
    CompanionLifeClaimOutputTransformer,
)

internal fun List<InputMessageTransformer>.withRequiredAssistantPromptContext(): List<InputMessageTransformer> =
    if (PromptInjectionTransformer in this) this else listOf(PromptInjectionTransformer) + this
