package com.example.financeapp.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltHex: String): String {
        val salt = Base64.decode(saltHex, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val calculated = hashPassword(password, salt)
        return slowEquals(calculated.toByteArray(), expectedHash.toByteArray())
    }

    // Constant-time comparison to prevent timing attacks
    private fun slowEquals(a: ByteArray, b: ByteArray): Boolean {
        var diff = a.size xor b.size
        for (i in a.indices) {
            if (i < b.size) diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
