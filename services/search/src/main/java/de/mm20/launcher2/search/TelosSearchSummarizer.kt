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

    private val sentenceSplit = Regex("(?<=[.!?])\\s+")
    private val wordSplit = Regex("\\W+")

    data class SummaryResult(
        val originalLength: Int,
        val summaryBullets: List<String>,
        val isSummarized: Boolean
    )

    /**
     * Extractive local summary: top sentences by word frequency. Not a cloud or LLM model.
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

        val rawSentences = cleanText.split(sentenceSplit)
            .map { it.trim() }
            .filter { it.length in 20..300 }

        if (rawSentences.isEmpty()) {
            return@withContext SummaryResult(cleanText.length, listOf(cleanText.take(150) + "..."), true)
        }

        val wordFreq = mutableMapOf<String, Int>()
        cleanText.lowercase(Locale.ROOT)
            .split(wordSplit)
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
