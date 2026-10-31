package com.example.financeapp.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCryptoEngine {
    private val MAGIC = byteArrayOf(0x41, 0x50, 0x45, 0x58) // "APEX"
    private const val VERSION: Short = 1
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128

    fun encryptPayload(jsonString: String, passphrase: CharArray, out: OutputStream) {
        // 1. Generate random salt and IV
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

        // 2. Derive key using PBKDF2
        val keySpec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        // 3. Compress JSON with GZIP
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
        val compressedBytes = byteOut.toByteArray()

        // 4. Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
        val ciphertext = cipher.doFinal(compressedBytes)

        // 5. Write binary container
        val dataOut = DataOutputStream(out)
        dataOut.write(MAGIC)
        dataOut.writeShort(VERSION.toInt())
        dataOut.write(salt)
        dataOut.write(iv)
        dataOut.writeInt(ciphertext.size)
        dataOut.write(ciphertext)
        dataOut.flush()
    }

    fun decryptPayload(input: InputStream, passphrase: CharArray): String {
        val dataIn = DataInputStream(input)

        // 1. Verify Magic Bytes
        val magic = ByteArray(4)
        dataIn.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("Invalid file format. Not an APEX backup.")

        // 2. Read Version
        val version = dataIn.readShort()
        if (version.toInt() != 1) throw IllegalArgumentException("Unsupported backup version: $version")

        // 3. Read Salt & IV
        val salt = ByteArray(SALT_LEN)
        dataIn.readFully(salt)
        val iv = ByteArray(IV_LEN)
        dataIn.readFully(iv)

        // 4. Read Ciphertext
        val cipherLen = dataIn.readInt()
        val ciphertext = ByteArray(cipherLen)
        dataIn.readFully(ciphertext)

        // 5. Derive Key & Decrypt
        val keySpec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
        val compressedBytes = cipher.doFinal(ciphertext)

        // 6. Decompress GZIP to JSON
        return GZIPInputStream(ByteArrayInputStream(compressedBytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
