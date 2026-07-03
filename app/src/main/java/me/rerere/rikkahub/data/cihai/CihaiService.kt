package me.rerere.rikkahub.data.cihai

import me.rerere.rikkahub.data.service.MemoryBankService

class CihaiService(
    private val store: CihaiStore,
    private val memoryBankService: MemoryBankService,
) {
    suspend fun addEntryAndRemember(entry: CihaiEntry) {
        store.addEntry(entry)
        memoryBankService.saveExtractedMemories(
            candidates = listOf(entry.toMemoryCandidate()),
            assistantId = entry.assistantId,
            conversationId = null,
            createdAt = entry.createdAt,
        )
        store.markEntryMemorySaved(entry.id)
    }

    suspend fun addBook(book: CihaiBook) {
        store.addBook(book)
    }
}
