package com.example.pathreport

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureCareStoreTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val alias = "care-test-only"
    @Before fun setup() {
        val base = InstrumentationRegistry.getInstrumentation().context
        root = File(base.noBackupFilesDir, "isolated-store-tests").apply { mkdirs() }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = root }
        context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() {
        root.deleteRecursively() // Test-owned directory only; target app data is never touched.
        context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
    private fun store() = SecureCareStore(context, "test.enc", alias)
    private fun legacy(raw: String) { assertTrue(context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).edit().putString("places", raw).commit()) }
    @Test fun encryptedCrudAndReopen() {
        val p = StopPlace(name = "가상 정차 장소", address = "가상 주소", latitude = 37.0, longitude = 127.0)
        val e = Elder(name = "가상 어르신")
        val original = CareData(listOf(p), listOf(e), listOf(PlaceElder(p.id, e.id)))
        store().save(original); assertEquals(original, store().loadAndMigrate())
        assertFalse(File(root, "test.enc").readBytes().toString(Charsets.UTF_8).contains("가상"))
        val firstCipher = File(root, "test.enc").readBytes()
        store().save(original)
        assertFalse(firstCipher.contentEquals(File(root, "test.enc").readBytes()))
        val modified = original.copy(places = listOf(p.copy(name = "수정한 가상 장소")))
        store().save(modified); assertEquals(modified, store().loadAndMigrate())
        val deleted = modified.withoutPlace(p.id)
        store().save(deleted); assertEquals(deleted, store().loadAndMigrate())
    }
    @Test fun migrationVerifiedAndIdempotentAfterInterruptedCleanup() {
        val raw = """[{"name":"가상 이름","address":"가상 주소"},{"name":"가상 이름","address":"가상 주소"}]"""
        legacy(raw)
        val migrated = store().loadAndMigrate()
        assertEquals(1, migrated.places.size); assertEquals(2, migrated.elders.size); assertEquals(2, migrated.links.size)
        assertNull(migrated.places.single().latitude)
        assertFalse(context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).contains("places"))
        // Reinsert source to simulate death after encrypted commit but before preference removal.
        legacy(raw); assertEquals(migrated, store().loadAndMigrate())
        assertFalse(context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).contains("places"))
    }
    @Test fun malformedSourcePreserved() {
        legacy("not valid JSON")
        assertThrows(Exception::class.java) { store().loadAndMigrate() }
        assertEquals("not valid JSON", context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).getString("places", null))
        assertFalse(File(root, "test.enc").exists())
    }
    @Test fun corruptCiphertextBlocksOverwriteAndKeepsLegacy() {
        store().save(CareData())
        val target = File(root, "test.enc"); val bytes = target.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); target.writeBytes(bytes)
        legacy("[]")
        assertThrows(Exception::class.java) { store().loadAndMigrate() }
        assertTrue(context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).contains("places"))
        assertArrayEquals(bytes, target.readBytes())
    }
    @Test fun missingKeyNeverCreatesReplacement() {
        store().save(CareData())
        val bytes = File(root, "test.enc").readBytes()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        legacy("[]")
        assertThrows(Exception::class.java) { store().loadAndMigrate() }
        assertArrayEquals(bytes, File(root, "test.enc").readBytes())
        assertTrue(context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).contains("places"))
    }
    @Test fun migrationWriteFailureKeepsPlaintext() {
        legacy("[]")
        // AtomicFile needs this path for a new encrypted write; a nonempty directory forces failure.
        File(root, "test.enc.new").apply { mkdirs(); File(this, "blocker").writeText("test-only") }
        assertThrows(Exception::class.java) { store().loadAndMigrate() }
        assertEquals("[]", context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).getString("places", null))
    }
}
