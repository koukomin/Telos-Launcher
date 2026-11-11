package de.mm20.launcher2.search

import com.aallam.similarity.JaroWinkler

@JvmInline
value class ResultScore private constructor(private val packed: Long) : Comparable<ResultScore> {
    constructor(
        isPrefix: Boolean,
        isSubstring: Boolean,
        isPrimary: Boolean,
        similarity: Float,
        isExact: Boolean = false,
    ) : this(
        (similarity.toRawBits().toLong()) or
                (if (isPrefix) (1L shl 32) else 0) or
                (if (isSubstring) (1L shl 33) else 0) or
                (if (isPrimary) (1L shl 34) else 0) or
                (if (isExact) (1L shl 35) else 0)
    )

    /**
     * Whether the query is an exact match for the term.
     */
    val isExact: Boolean
        get() = (packed and (1L shl 35)) != 0L

    /**
     * Whether the query is a literal prefix of the result.
     */
    val isPrefix: Boolean
        get() = (packed and (1L shl 32)) != 0L

    /**
     * Whether the query is a substring of the result.
     */
    val isSubstring: Boolean
        get() = (packed and (1L shl 33)) != 0L

    /**
     * Whether the query was matched against a primary field.
     */
    val isPrimary: Boolean
        get() = (packed and (1L shl 34)) != 0L

    /**
     * The Jaro-Winkler similarity between the query and the result.
     */
    val similarity: Float
        get() = Float.fromBits((packed and 0xffffffffL).toInt())

    /**
     * Tiered ranking score ensuring Exact > Prefix > Substring > High Similarity Fuzzy Match.
     * Prevents false-positive fuzzy matches from overriding exact/prefix query results.
     */
    val score: Float
        get() = when {
            isExact -> 10.0f * (if (isPrimary) 1.0f else 0.9f)
            isPrefix -> 5.0f + (similarity * 0.5f) * (if (isPrimary) 1.0f else 0.8f)
            isSubstring -> 2.0f + (similarity * 0.5f) * (if (isPrimary) 1.0f else 0.8f)
            else -> if (similarity >= MATCH_THRESHOLD) similarity * (if (isPrimary) 0.7f else 0.4f) else 0.0f
        }

    override fun compareTo(other: ResultScore): Int {
        return score.compareTo(other.score)
    }

    companion object {
        /** Minimum similarity required for fuzzy matching (78%). Short typos or unrelated words fail below this. */
        const val MATCH_THRESHOLD = 0.78f

        private val NON_ALPHANUM_REGEX = Regex("[^\\p{L}\\p{N}]")
        private val jaroWinkler = JaroWinkler()

        fun from(
            query: String,
            primaryFields: Iterable<String> = emptyList(),
            secondaryFields: Iterable<String> = emptyList(),
        ): ResultScore {
            if (query.isBlank()) return Zero
            val qLower = query.lowercase()
            val qClean = qLower.replace(NON_ALPHANUM_REGEX, "")

            val primaryTokens = expandTokens(primaryFields.map { it.lowercase() })
            val secondaryTokens = expandTokens(secondaryFields.map { it.lowercase() })

            val literal = maxOf(
                fromInternal(qLower, primaryTokens, secondaryTokens),
                if (qClean.isNotEmpty() && qClean != qLower) {
                    fromInternal(qClean, primaryTokens, secondaryTokens)
                } else Zero
            )

            val qGreek = GreekFold.fold(query)
            val qGreekClean = qGreek.replace(NON_ALPHANUM_REGEX, "")
            val primaryGreekTokens = expandTokens(primaryFields.map { GreekFold.fold(it) })
            val secondaryGreekTokens = expandTokens(secondaryFields.map { GreekFold.fold(it) })

            val greeklish = maxOf(
                fromInternal(qGreek, primaryGreekTokens, secondaryGreekTokens),
                if (qGreekClean.isNotEmpty() && qGreekClean != qGreek) {
                    fromInternal(qGreekClean, primaryGreekTokens, secondaryGreekTokens)
                } else Zero
            )

            return maxOf(literal, greeklish)
        }

        private fun expandTokens(fields: Iterable<String>): List<String> {
            val out = ArrayList<String>()
            for (field in fields) {
                if (field.isBlank()) continue
                out.add(field)
                val clean = field.replace(NON_ALPHANUM_REGEX, "")
                if (clean.isNotEmpty() && clean != field) {
                    out.add(clean)
                }
                val parts = field.split(' ', '-', '_', '/', '.', ',', ':', ';', '|')
                if (parts.size > 1) {
                    for (part in parts) if (part.length >= 2) out.add(part)
                }
            }
            return out
        }

        private fun fromInternal(
            query: String,
            primaryFields: Iterable<String>,
            secondaryFields: Iterable<String>,
        ): ResultScore {
            val bestPrimaryScore = primaryFields.maxOfOrNull { term ->
                val sim = jaroWinkler.similarity(query, term).toFloat()
                ResultScore(
                    isExact = term.equals(query, ignoreCase = true),
                    isPrefix = term.startsWith(query, ignoreCase = true),
                    isSubstring = term.contains(query, ignoreCase = true),
                    isPrimary = true,
                    similarity = sim
                )
            } ?: Zero
            val bestSecondaryScore = secondaryFields.maxOfOrNull { term ->
                val sim = jaroWinkler.similarity(query, term).toFloat()
                ResultScore(
                    isExact = term.equals(query, ignoreCase = true),
                    isPrefix = term.startsWith(query, ignoreCase = true),
                    isSubstring = term.contains(query, ignoreCase = true),
                    isPrimary = false,
                    similarity = sim
                )
            } ?: Zero

            return maxOf(bestPrimaryScore, bestSecondaryScore)
        }

        val Zero = ResultScore(
            isPrefix = false,
            isSubstring = false,
            isPrimary = false,
            similarity = 0f,
            isExact = false
        )

        val Unspecified = ResultScore(
            isPrefix = false,
            isSubstring = false,
            isPrimary = false,
            similarity = Float.NaN,
            isExact = false
        )
    }
}

inline val ResultScore.isUnspecified : Boolean
    get() = this == ResultScore.Unspecified
