package com.example.financeapp.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoEngineTest {

    private const val TEST_PASSPHRASE = "TestPass123"
    private val testJson = """{"version":1,"test":"data"}"""

    @Test
    fun `encryptDecrypt round trip works`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        val input = ByteArrayInputStream(output.toByteArray())
        val decrypted = BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())

        assertEquals(testJson, decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt throws on wrong magic bytes`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        // Corrupt magic bytes
        val bytes = output.toByteArray()
        bytes[0] = 0x00
        bytes[1] = 0x00
        bytes[2] = 0x00
        bytes[3] = 0x00

        val input = ByteArrayInputStream(bytes)
        BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt throws on wrong version`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        // Corrupt version (bytes 4-5)
        val bytes = output.toByteArray()
        bytes[4] = 0x00
        bytes[5] = 0x02 // Version 2 instead of 1

        val input = ByteArrayInputStream(bytes)
        BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
    }

    @Test(expected = Exception::class)
    fun `decrypt throws on wrong passphrase`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        val input = ByteArrayInputStream(output.toByteArray())
        BackupCryptoEngine.decryptPayload(input, "WrongPass123".toCharArray())
    }

    @Test(expected = Exception::class)
    fun `decrypt throws on truncated data`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        // Truncate the data
        val bytes = output.toByteArray()
        val truncated = bytes.copyOf(bytes.size - 10)

        val input = ByteArrayInputStream(truncated)
        BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
    }

    @Test(expected = Exception::class)
    fun `decrypt throws on tampered ciphertext`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        // Tamper with ciphertext (after header: magic(4) + version(2) + salt(16) + iv(12) + cipherLen(4) = 38)
        val bytes = output.toByteArray()
        if (bytes.size > 50) {
            bytes[50] = (bytes[50] xor 0xFF).toByte() // Flip bits in ciphertext
        }

        val input = ByteArrayInputStream(bytes)
        BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
    }

    @Test(expected = Exception::class)
    fun `decrypt throws on tampered GCM tag`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        // Tamper with the last 16 bytes (GCM tag)
        val bytes = output.toByteArray()
        if (bytes.size >= 16) {
            bytes[bytes.size - 1] = (bytes[bytes.size - 1] xor 0xFF).toByte()
        }

        val input = ByteArrayInputStream(bytes)
        BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
    }

    @Test
    fun `encrypt produces correct format`() {
        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output)

        val bytes = output.toByteArray()

        // Check magic bytes: APEX = 0x41, 0x50, 0x45, 0x58
        assertEquals(0x41, bytes[0].toInt() and 0xFF)
        assertEquals(0x50, bytes[1].toInt() and 0xFF)
        assertEquals(0x45, bytes[2].toInt() and 0xFF)
        assertEquals(0x58, bytes[3].toInt() and 0xFF)

        // Check version: 1 (big-endian short)
        assertEquals(0x00, bytes[4].toInt() and 0xFF)
        assertEquals(0x01, bytes[5].toInt() and 0xFF)

        // Check minimum size: header(38) + ciphertext + tag(16)
        assertTrue(bytes.size >= 54)
    }

    @Test
    fun `decrypt rejects empty input`() {
        val input = ByteArrayInputStream(ByteArray(0))

        try {
            BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
            fail("Expected exception for empty input")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `decrypt rejects input too small for header`() {
        val input = ByteArrayInputStream(ByteArray(10)) // Less than 38 byte header

        try {
            BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())
            fail("Expected exception for too small input")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `different passphrases produce different ciphertexts`() {
        val output1 = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, "Pass1234".toCharArray(), output1)

        val output2 = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, "Pass1235".toCharArray(), output2)

        assertFalse(output1.toByteArray().contentEquals(output2.toByteArray()))
    }

    @Test
    fun `same passphrase produces different ciphertexts due to random IV/salt`() {
        val output1 = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output1)

        val output2 = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(testJson, TEST_PASSPHRASE.toCharArray(), output2)

        // Ciphertexts should be different due to random salt/IV
        assertFalse(output1.toByteArray().contentEquals(output2.toByteArray()))

        // But both should decrypt correctly
        val input1 = ByteArrayInputStream(output1.toByteArray())
        val input2 = ByteArrayInputStream(output2.toByteArray())
        assertEquals(testJson, BackupCryptoEngine.decryptPayload(input1, TEST_PASSPHRASE.toCharArray()))
        assertEquals(testJson, BackupCryptoEngine.decryptPayload(input2, TEST_PASSPHRASE.toCharArray()))
    }

    @Test
    fun `large JSON encrypt/decrypt works`() {
        val largeJson = """{"version":1,"data":"${"x".repeat(10000)}"}"""

        val output = ByteArrayOutputStream()
        BackupCryptoEngine.encryptPayload(largeJson, TEST_PASSPHRASE.toCharArray(), output)

        val input = ByteArrayInputStream(output.toByteArray())
        val decrypted = BackupCryptoEngine.decryptPayload(input, TEST_PASSPHRASE.toCharArray())

        assertEquals(largeJson, decrypted)
    }
}