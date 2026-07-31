package me.rerere.rikkahub.data.companion

data class CompanionTurnMutation(
    val assistantId: String,
    val state: CompanionState? = null,
    val explainableState: CompanionExplainableState? = null,
    val lifeAnchor: CompanionLifeAnchor? = null,
    val privateImpression: CompanionPrivateImpression? = null,
    val alwaysOnAnchors: List<CompanionAlwaysOnAnchor> = emptyList(),
    val cancelAlwaysOnAnchorIds: List<String> = emptyList(),
    val goals: List<CompanionGoal> = emptyList(),
    val lifeEvents: List<CompanionLifeEvent> = emptyList(),
    val concernChanges: List<CompanionConcernChange> = emptyList(),
    val acceptedCommitments: List<CompanionCommitment> = emptyList(),
    val relationshipEvents: List<CompanionRelationshipEvent> = emptyList(),
    val continuity: CompanionContinuity? = null,
    val interactionEvents: List<CompanionInteractionEvent> = emptyList(),
    val nowMillis: Long,
)

data class CompanionRuntimeReduction(
    val persistedState: CompanionPersistedState,
    val snapshot: CompanionSnapshot,
    val affectedCommitment: CompanionCommitment? = null,
)
