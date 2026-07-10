package me.rerere.rikkahub.data.cihai

class CihaiService(
    private val store: CihaiStore,
) {
    suspend fun addEntry(entry: CihaiEntry) {
        if (entry.kind != CihaiEntryKind.DIARY) return
        store.addEntry(entry)
    }
}
