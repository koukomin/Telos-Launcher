package de.mm20.launcher2.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-device smart text summarizer for Telos Search (Wikipedia, web, notes, and long text results).
 * Provides fast 3-bullet summaries and key sentence extraction off the main thread
 * with zero cloud latency and 100% local privacy.
 */
object TelosSearchSummarizer {

    data class SummaryResult(
        val originalLength: Int,
        val summaryBullets: List<String>,
        val isSummarized: Boolean
    )

    /**
     * Synthesizes [text] into a clean 3-bullet summary off the main thread.
     */
    suspend fun summarize(text: String, maxBullets: Int = 3): SummaryResult = withContext(Dispatchers.Default) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || cleanText.length < 150) {
            return@withContext SummaryResult(
                originalLength = cleanText.length,
                summaryBullets = if (cleanText.isNotBlank()) listOf(cleanText) else emptyList(),
                isSummarized = false
            )
        }

        // Split text into sentences
        val rawSentences = cleanText.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length in 20..300 }

        if (rawSentences.isEmpty()) {
            return@withContext SummaryResult(cleanText.length, listOf(cleanText.take(150) + "..."), true)
        }

        // Score sentences by word frequency / key terms
        val wordFreq = mutableMapOf<String, Int>()
        cleanText.lowercase(Locale.ROOT)
            .split(Regex("\\W+"))
            .filter { it.length > 3 }
            .forEach { word -> wordFreq[word] = (wordFreq[word] ?: 0) + 1 }

        val scored = rawSentences.map { sentence ->
            val score = sentence.lowercase(Locale.ROOT)
                .split(Regex("\\W+"))
                .sumOf { wordFreq[it] ?: 0 }
            sentence to score
        }.sortedByDescending { it.second }

        val topBullets = scored.take(maxBullets).map { it.first }

        SummaryResult(
            originalLength = cleanText.length,
            summaryBullets = topBullets,
            isSummarized = true
        )
    }
}
