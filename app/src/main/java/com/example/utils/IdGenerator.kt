package com.example.utils

import java.util.UUID
import kotlin.random.Random

object IdGenerator {
    /**
     * Generates a unique 10-digit ID string (e.g. "7392846152").
     */
    fun generate10DigitId(): String {
        val firstDigit = Random.nextInt(1, 10).toString()
        val remainingDigits = (1..9)
            .map { Random.nextInt(0, 10) }
            .joinToString("")
        return firstDigit + remainingDigits
    }

    /**
     * Generates a UUID string for internal resource tracking.
     */
    fun generateUuid(): String = UUID.randomUUID().toString()
}
