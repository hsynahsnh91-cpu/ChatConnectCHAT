package com.example

import com.example.utils.PasswordUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPasswordHashingAndVerification() {
    val password = "MySecurePassword123"
    val hash = PasswordUtils.hashPassword(password)
    assertNotNull(hash)
    assertTrue(hash.isNotEmpty())

    // Verification with correct old password
    assertTrue(PasswordUtils.verifyPassword(password, hash))

    // Verification with wrong old password
    assertFalse(PasswordUtils.verifyPassword("WrongPassword", hash))
    assertFalse(PasswordUtils.verifyPassword("mysecurepassword123", hash))
  }
}
