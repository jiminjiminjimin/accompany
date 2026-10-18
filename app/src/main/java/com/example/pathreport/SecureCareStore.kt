package com.example.pathreport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** No plaintext files, keys in preferences, network calls, or sensitive logging. */
class SecureCareStore(context: Context, fileName: String = "care-v1.enc", private val alias: String = "care-data-v1") {
    companion object { private val storeLock = Any() }
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val prefs = context.getSharedPreferences("pathreport", Context.MODE_PRIVATE)
    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun key(allowCreate: Boolean): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        check(allowCreate) // Never silently replace a lost key for existing ciphertext.
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun read(): CareData {
        val bytes = file.readFully()
        return CareCodec.decode(CareCipher.decrypt(bytes, key(false)))
    }
    fun save(data: CareData): Unit = synchronized(storeLock) {
        val plain = CareCodec.encode(data)
        val bytes = CareCipher.encrypt(plain, key(!exists()))
        val stream = file.startWrite()
        try { stream.write(bytes); stream.fd.sync(); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        check(read() == data)
    }
    /** Idempotent two-phase migration: encrypted marker+payload, read verification, then synchronous removal. */
    fun loadAndMigrate(): CareData = synchronized(storeLock) {
        var data = if (exists()) read() else CareData()
        if (!prefs.contains("places")) return data
        val raw = requireNotNull(prefs.getString("places", null))
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        if (data.legacyDigest != digest) {
            check(data.legacyDigest == null) // Unexpected changed legacy source: retain it and block rather than duplicate.
            val array = JSONArray(raw) // Strict parse: malformed legacy data must not become an empty list.
            val entries = List(array.length()) { i ->
                val item = array.getJSONObject(i)
                val name = requireNotNull(item.get("name") as? String).trim()
                val address = requireNotNull(item.get("address") as? String).trim()
                LegacyPerson(name, address)
            }
            data = migrateLegacyItems(data, entries, digest)
            save(data)
        }
        check(read() == data)
        check(prefs.edit().remove("places").commit())
        check(!prefs.contains("places"))
        return data
    }
}
