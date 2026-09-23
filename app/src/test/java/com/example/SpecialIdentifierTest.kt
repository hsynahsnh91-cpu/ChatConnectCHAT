package com.example

import com.example.data.model.PublicUserProfile
import com.example.service.admin.SpecialIdentifierGenerator
import org.junit.Assert.*
import org.junit.Test

class SpecialIdentifierTest {

    @Test
    fun testGenerateVanityIdFormatting() {
        val id1 = SpecialIdentifierGenerator.formatVanityId(prefix = "VIP", sequenceNumber = 1, padLength = 4)
        assertEquals("VIP0001", id1)

        val id7 = SpecialIdentifierGenerator.formatVanityId(prefix = "GOLD", sequenceNumber = 7, padLength = 4)
        assertEquals("GOLD0007", id7)

        val id99 = SpecialIdentifierGenerator.formatVanityId(prefix = "VIP", sequenceNumber = 99, padLength = 4)
        assertEquals("VIP0099", id99)
    }

    @Test
    fun testNextAvailableVanityIdCollisionAvoidance() {
        val existing = setOf("VIP0001", "VIP0002", "VIP0003")
        val next = SpecialIdentifierGenerator.generateNextAvailableVanityId(
            prefix = "VIP",
            existingIdentifiers = existing,
            startSequence = 1,
            padLength = 4
        )
        assertEquals("VIP0004", next)
    }

    @Test
    fun testBatchVanityIdGeneration() {
        val existing = setOf("VIP0001")
        val batch = SpecialIdentifierGenerator.generateBatch(
            prefix = "VIP",
            count = 3,
            existingIdentifiers = existing,
            padLength = 4
        )
        assertEquals(listOf("VIP0002", "VIP0003", "VIP0004"), batch)
    }

    @Test
    fun testVanityIdValidation() {
        // Valid identifiers
        assertTrue(SpecialIdentifierGenerator.validateVanityId("VIP0001").isSuccess)
        assertTrue(SpecialIdentifierGenerator.validateVanityId("gold777").isSuccess)
        assertEquals("GOLD777", SpecialIdentifierGenerator.validateVanityId("gold777").getOrNull())

        // Invalid: too short (< 3)
        assertTrue(SpecialIdentifierGenerator.validateVanityId("AB").isFailure)

        // Invalid: illegal symbols
        assertTrue(SpecialIdentifierGenerator.validateVanityId("VIP@001").isFailure)
        assertTrue(SpecialIdentifierGenerator.validateVanityId("VIP 001").isFailure)
    }

    @Test
    fun testPublicUserProfileSafety() {
        val profile = PublicUserProfile(
            userId = "usr_12345",
            customId = "VIP0001",
            username = "VIP Member",
            profilePicUri = "https://example.com/pic.jpg",
            bio = "Official VIP User",
            isOnline = true,
            isSpecialIdentifier = true,
            specialCategory = "VIP"
        )
        assertEquals("VIP0001", profile.customId)
        assertEquals("VIP Member", profile.username)
        assertTrue(profile.isSpecialIdentifier)

        // Verify that sensitive fields (firebaseUid, email, passwordHash) are NOT properties of PublicUserProfile
        val fields = PublicUserProfile::class.java.declaredFields.map { it.name }
        assertFalse("Public profile must never expose firebaseUid", fields.contains("firebaseUid"))
        assertFalse("Public profile must never expose email", fields.contains("email"))
        assertFalse("Public profile must never expose passwordHash", fields.contains("passwordHash"))
        assertFalse("Public profile must never expose oauthToken", fields.contains("oauthToken"))
    }
}
