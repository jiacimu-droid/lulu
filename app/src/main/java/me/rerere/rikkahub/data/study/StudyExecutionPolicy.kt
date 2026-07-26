package me.rerere.rikkahub.data.study

/**
 * Shared chapter execution order used by AI scheduling.
 *
 * This is guidance only. It contains no dates, daily tasks, weekly replacements,
 * monthly replacements or state mutation hooks.
 */
object CurrentWeekStudyRecovery {
    val executionOrderReference: String = """
        专业课每章或连续单元的第一轮闭环顺序必须固定为：
        1. 听完当前章或连续单元课程，记录原始分钟与实际有效分钟；课程没有结束，不做整章题。
        2. 合上资料口述目录树和构成关系，先画只含主干、层级、构成要件、易混点的闭卷骨架。
        3. 对照考试分析和课程纠正骨架，形成正式框架图；一般控制在30-45分钟，不抄教材。
        4. 正式框架完成后再做听课配套题，再按章节重要程度进入额外题源或分科真题。
        5. 每道错题只标一个主错因，把新增易混点或遗漏关系补回原框架，并安排隔日与7-14日回炉。
        6. 一本新课主线结束后再启动下一本；额外题源、错题二刷和背诵可以作为复线继续，不能重新覆盖当前新课节点。
        7. 框架先于整章题，但不能为了第一次正确率无限画图；题目本身就是检验和修订框架的工具。
    """.trimIndent()
}
