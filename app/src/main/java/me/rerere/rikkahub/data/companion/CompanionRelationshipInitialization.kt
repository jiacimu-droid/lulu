package me.rerere.rikkahub.data.companion

/**
 * Establishes an evidence-safe relationship baseline from an explicit character card.
 *
 * A configured lover, spouse, family member, close friend, rival, or enemy must not
 * behave as though the relationship started at zero. This only applies declared
 * identity; it never invents shared events, affection, or a speaking style.
 */
internal fun initializeCompanionRelationshipFromCharacterCard(
    current: CompanionRelationshipState,
    characterCard: String,
    nowMillis: Long,
): CompanionRelationshipState {
    if (current.updatedAt > 0L || characterCard.isBlank()) return current
    val declaration = characterCard.relationshipDeclaration() ?: return current
    return current.copy(
        roleLabel = current.roleLabel.ifBlank { declaration.label },
        trust = maxOf(current.trust, declaration.trust),
        closeness = maxOf(current.closeness, declaration.closeness),
        reliability = maxOf(current.reliability, declaration.reliability),
        boundaryConfidence = maxOf(current.boundaryConfidence, declaration.boundaryConfidence),
        unresolvedTension = maxOf(current.unresolvedTension, declaration.tension),
        updatedAt = nowMillis,
    )
}

private data class RelationshipDeclaration(
    val label: String,
    val trust: Float,
    val closeness: Float,
    val reliability: Float,
    val boundaryConfidence: Float,
    val tension: Float,
)

private fun String.relationshipDeclaration(): RelationshipDeclaration? {
    val normalized = lowercase().replace(Regex("\\s+"), "")
    return RELATIONSHIP_DECLARATIONS.firstNotNullOfOrNull { (markers, declaration) ->
        markers.firstOrNull { marker ->
            marker in normalized && !normalized.isNegatedNear(marker)
        }?.let { declaration }
    }
}

private fun String.isNegatedNear(marker: String): Boolean {
    val index = indexOf(marker)
    if (index < 0) return false
    val prefix = substring(maxOf(0, index - 4), index)
    return NEGATION_MARKERS.any(prefix::endsWith)
}

private val NEGATION_MARKERS = listOf("不是", "并非", "不算", "非", "not")

private val RELATIONSHIP_DECLARATIONS = listOf(
    listOf("夫妻", "配偶", "丈夫", "妻子", "husband", "wife", "spouse") to RelationshipDeclaration(
        label = "伴侣",
        trust = 0.70f,
        closeness = 0.75f,
        reliability = 0.60f,
        boundaryConfidence = 0.55f,
        tension = 0f,
    ),
    listOf("恋人", "男朋友", "女朋友", "情侣", "boyfriend", "girlfriend", "lover") to RelationshipDeclaration(
        label = "恋人",
        trust = 0.65f,
        closeness = 0.70f,
        reliability = 0.55f,
        boundaryConfidence = 0.50f,
        tension = 0f,
    ),
    listOf("家人", "亲人", "兄妹", "姐弟", "姐妹", "兄弟", "family") to RelationshipDeclaration(
        label = "家人",
        trust = 0.65f,
        closeness = 0.60f,
        reliability = 0.60f,
        boundaryConfidence = 0.55f,
        tension = 0f,
    ),
    listOf("挚友", "至交", "最好的朋友", "bestfriend") to RelationshipDeclaration(
        label = "挚友",
        trust = 0.62f,
        closeness = 0.58f,
        reliability = 0.55f,
        boundaryConfidence = 0.55f,
        tension = 0f,
    ),
    listOf("朋友", "friend") to RelationshipDeclaration(
        label = "朋友",
        trust = 0.55f,
        closeness = 0.35f,
        reliability = 0.52f,
        boundaryConfidence = 0.52f,
        tension = 0f,
    ),
    listOf("宿敌", "死敌", "敌人", "enemy", "nemesis") to RelationshipDeclaration(
        label = "敌对关系",
        trust = 0.15f,
        closeness = 0.05f,
        reliability = 0.30f,
        boundaryConfidence = 0.55f,
        tension = 0.65f,
    ),
    listOf("竞争对手", "对手", "rival") to RelationshipDeclaration(
        label = "竞争关系",
        trust = 0.35f,
        closeness = 0.15f,
        reliability = 0.45f,
        boundaryConfidence = 0.55f,
        tension = 0.40f,
    ),
)
