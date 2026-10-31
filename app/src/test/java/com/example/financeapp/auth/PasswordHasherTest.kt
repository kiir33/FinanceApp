package com.example.financeapp.auth

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hashPassword generates consistent hash for same input`() {
        val password = "TestPass123"
        val salt = PasswordHasher.generateSalt()

        val hash1 = PasswordHasher.hashPassword(password, salt)
        val hash2 = PasswordHasher.hashPassword(password, salt)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `verifyPassword returns true for correct password`() {
        val password = "TestPass123"
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)

        val result = PasswordHasher.verifyPassword(password, salt, hash)

        assertTrue(result)
    }

    @Test
    fun `verifyPassword returns false for incorrect password`() {
        val password = "TestPass123"
        val wrongPassword = "WrongPass123"
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)

        val result = PasswordHasher.verifyPassword(wrongPassword, salt, hash)

        assertFalse(result)
    }

    @Test
    fun `verifyPassword returns false for empty password`() {
        val password = "TestPass123"
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)

        val result = PasswordHasher.verifyPassword("", salt, hash)

        assertFalse(result)
    }

    @Test
    fun `generateSalt produces unique salts`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()

        assertNotEquals(salt1, salt2)
        assertEquals(24, salt1.length) // Base64 encoded 16 bytes = 24 chars
    }

    @Test
    fun `slowEquals constant time comparison`() {
        // This test verifies the function exists and doesn't throw
        val a = "test".toByteArray()
        val b = "test".toByteArray()
        val c = "diff".toByteArray()

        // Use reflection to test private function
        val method = PasswordHasher::class.java.getDeclaredMethod("slowEquals", ByteArray::class.java, ByteArray::class.java)
        method.isAccessible = true

        val result1 = method.invoke(null, a, b) as Boolean
        val result2 = method.invoke(null, a, c) as Boolean

        assertTrue(result1)
        assertFalse(result2)
    }

    @Test
    fun `hashPassword with special characters`() {
        val password = "P@ssw0rd!#$%"
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)

        val result = PasswordHasher.verifyPassword(password, salt, hash)

        assertTrue(result)
    }

    @Test
    fun `hashPassword with unicode characters`() {
        val password = "Pässwörd123日本語"
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)

        val result = PasswordHasher.verifyPassword(password, salt, hash)

        assertTrue(result)
    }

    @Test
    fun `different passwords produce different hashes`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hashPassword("Pass1234", salt)
        val hash2 = PasswordHasher.hashPassword("Pass1235", salt)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `same password different salts produce different hashes`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hashPassword("TestPass123", salt1)
        val hash2 = PasswordHasher.hashPassword("TestPass123", salt2)

        assertNotEquals(hash1, hash2)
    }
}