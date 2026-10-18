package com.example.pathreport

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Platform-independent authenticated envelope; production keys come only from Android Keystore. */
object CareCipher {
    private val header = byteArrayOf(67, 65, 82, 49)
    private val aad = "care-schema-1".toByteArray(Charsets.UTF_8)
    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == 12)
        cipher.updateAAD(aad)
        return header + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, key: SecretKey): ByteArray {
        require(bytes.size >= 4 + 12 + 16 && bytes.copyOfRange(0, 4).contentEquals(header))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(4, 16)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(16, bytes.size))
    }
}
