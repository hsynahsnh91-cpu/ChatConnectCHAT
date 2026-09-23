package com.example.service.admin

/**
 * Admin-only utility for generating and validating unique vanity user identifiers (e.g. VIP0001, GOLD0007).
 */
object SpecialIdentifierGenerator {
    const val DEFAULT_PREFIX = "VIP"
    const val DEFAULT_PAD_LENGTH = 4

    val PRESET_PREFIXES = listOf("VIP", "GOLD", "PROMO", "FOUNDER", "STAFF", "ROYAL")

    /**
     * Formats a vanity ID from a prefix and a numeric sequence (e.g., ("VIP", 1, 4) -> "VIP0001").
     */
    fun formatVanityId(
        prefix: String = DEFAULT_PREFIX,
        sequenceNumber: Int,
        padLength: Int = DEFAULT_PAD_LENGTH
    ): String {
        val cleanPrefix = prefix.trim().uppercase().filter { it.isLetterOrDigit() }.ifEmpty { DEFAULT_PREFIX }
        val safeSeq = sequenceNumber.coerceAtLeast(1)
        val formattedNum = safeSeq.toString().padStart(padLength, '0')
        return "$cleanPrefix$formattedNum"
    }

    /**
     * Finds the next unused sequential vanity identifier that does not collide with existing ones.
     */
    fun generateNextAvailableVanityId(
        prefix: String = DEFAULT_PREFIX,
        existingIdentifiers: Collection<String>,
        startSequence: Int = 1,
        padLength: Int = DEFAULT_PAD_LENGTH
    ): String {
        val cleanPrefix = prefix.trim().uppercase().filter { it.isLetterOrDigit() }.ifEmpty { DEFAULT_PREFIX }
        val upperExisting = existingIdentifiers.map { it.trim().uppercase() }.toSet()

        var seq = startSequence
        while (seq < 999999) {
            val candidate = formatVanityId(cleanPrefix, seq, padLength)
            if (!upperExisting.contains(candidate)) {
                return candidate
            }
            seq++
        }
        val fallbackSeq = (System.currentTimeMillis() % 9000 + 1000).toInt()
        return formatVanityId(cleanPrefix, fallbackSeq, padLength)
    }

    /**
     * Batch generation of sequential unique vanity IDs.
     */
    fun generateBatch(
        prefix: String = DEFAULT_PREFIX,
        count: Int = 5,
        existingIdentifiers: Collection<String>,
        padLength: Int = DEFAULT_PAD_LENGTH
    ): List<String> {
        val result = mutableListOf<String>()
        val currentSet = existingIdentifiers.map { it.trim().uppercase() }.toMutableSet()
        var seq = 1
        while (result.size < count && seq < 999999) {
            val candidate = formatVanityId(prefix, seq, padLength)
            if (!currentSet.contains(candidate)) {
                result.add(candidate)
                currentSet.add(candidate)
            }
            seq++
        }
        return result
    }

    /**
     * Validates if a proposed identifier conforms to standard vanity rules.
     */
    fun validateVanityId(identifier: String): Result<String> {
        val clean = identifier.trim().uppercase()
        if (clean.length < 3) {
            return Result.failure(IllegalArgumentException("يجب أن يتكون المعرف من 3 خانات على الأقل."))
        }
        if (clean.length > 20) {
            return Result.failure(IllegalArgumentException("لا يمكن أن يتجاوز طول المعرف 20 خانة."))
        }
        if (!clean.all { it.isLetterOrDigit() }) {
            return Result.failure(IllegalArgumentException("يجب أن يتكون المعرف من أحرف وأرقام إنجليزية فقط بدون رموز."))
        }
        return Result.success(clean)
    }
}
