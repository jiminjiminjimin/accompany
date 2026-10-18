package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class CareCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun authenticatedEncryptionRoundtripAndFreshIv() {
        val key = key(); val plain = "가상 이름·가상 주소".toByteArray()
        val first = CareCipher.encrypt(plain, key); val second = CareCipher.encrypt(plain, key)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("가상"))
        assertArrayEquals(plain, CareCipher.decrypt(first, key))
    }
    @Test fun tamperingWrongKeyAndTruncationRejected() {
        val key = key(); val cipher = CareCipher.encrypt("가상 자료".toByteArray(), key)
        val changed = cipher.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { CareCipher.decrypt(changed, key) }
        assertThrows(Exception::class.java) { CareCipher.decrypt(cipher, key()) }
        assertThrows(Exception::class.java) { CareCipher.decrypt(cipher.copyOf(10), key) }
        val headerChanged = cipher.copyOf().apply { this[0] = 0 }
        assertThrows(Exception::class.java) { CareCipher.decrypt(headerChanged, key) }
    }
}
