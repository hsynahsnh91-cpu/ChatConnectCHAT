package com.example.utils

import java.security.MessageDigest

object PasswordUtils {
    /**
     * Hashes a password string using SHA-256 with a salt prefix for security.
     */
    fun hashPassword(password: String): String {
        if (password.isBlank()) return ""
        val salted = "ChatConnect_Salt_2026#$password"
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies plain-text password against stored SHA-256 hash.
     */
    fun verifyPassword(plainText: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) {
            // Legacy account without password set yet
            return true
        }
        val computed = hashPassword(plainText)
        return computed.equals(storedHash, ignoreCase = true)
    }
}
