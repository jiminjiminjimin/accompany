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
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.noBackupFilesDir, "isolated-store-tests").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                base.getSharedPreferences("isolated-store-tests-$name", mode)
        }
        check(root.isDirectory)
        context.getSharedPreferences("pathreport", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() {
        root.deleteRecursively() // Only this test-owned subdirectory is removed; production files are preserved.
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
    @Test fun versionOneEncryptedFileUpgradesWithoutLosingIds() {
        // Bootstrap only the isolated test key, then replace with a genuine v1 payload.
        store().save(CareData())
        val v1 = """{"version":1,"legacyDigest":"digest","places":[{"id":"p","name":"가상 장소","address":"가상 주소","lat":37.5,"lon":127.0}],"elders":[{"id":"e","name":"가상 어르신"}],"links":[{"place":"p","elder":"e"}]}""".toByteArray()
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as javax.crypto.SecretKey
        File(root, "test.enc").writeBytes(CareCipher.encrypt(v1, key))
        val old = store().loadAndMigrate()
        assertEquals("p", old.places.single().id)
        assertEquals("e", old.elders.single().id)
        assertEquals("digest", old.legacyDigest)
        assertTrue(old.trips.isEmpty())
        val started = old.startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 100)
        store().save(started)
        assertEquals(started, store().loadAndMigrate())
        val ended = started.endTrip(started.trips.single().id, 200).withoutPlace("p").withoutElder("e")
        store().save(ended)
        assertEquals(ended, store().loadAndMigrate())
        assertEquals("가상 어르신", store().loadAndMigrate().trips.single().targets.single().elderName)
    }
    @Test fun invalidTripPayloadIsRejected() {
        val rootJson = org.json.JSONObject(CareCodec.encode(CareData()).toString(Charsets.UTF_8))
        rootJson.put("trips", org.json.JSONArray("""[{"id":"bad","direction":"UNKNOWN","startedAt":100,"targets":[]}]"""))
        assertThrows(Exception::class.java) { CareCodec.decode(rootJson.toString().toByteArray()) }
        rootJson.remove("trips")
        assertThrows(Exception::class.java) { CareCodec.decode(rootJson.toString().toByteArray()) }
    }
    @Test fun versionTwoTripsUpgradeAndArrivalsSurviveEncryptedReopen() {
        val place = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
        val elder = Elder("e", "가상 어르신")
        val old = CareData(listOf(place), listOf(elder), listOf(PlaceElder("p", "e")))
            .startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 1_000_000)
        val v2 = org.json.JSONObject(CareCodec.encode(old).toString(Charsets.UTF_8)).put("version", 2)
        v2.getJSONArray("trips").getJSONObject(0).remove("arrivals")
        store().save(CareData())
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as javax.crypto.SecretKey
        File(root, "test.enc").writeBytes(CareCipher.encrypt(v2.toString().toByteArray(), key))
        assertEquals(old, store().loadAndMigrate())
        val arrival = Arrival("e", "p", 1_000_000, 1_020_000)
        val recorded = old.recordArrivals(old.trips.single().id, listOf(arrival))
        store().save(recorded)
        assertEquals(recorded, store().loadAndMigrate())
        assertEquals(recorded, store().loadAndMigrate().recordArrivals(old.trips.single().id, listOf(arrival)))
        val ended = recorded.endTrip(old.trips.single().id, 1_030_000).withoutElder("e").withoutPlace("p")
        store().save(ended)
        assertEquals(ended, store().loadAndMigrate())
        val v3 = org.json.JSONObject(CareCodec.encode(ended).toString(Charsets.UTF_8))
        v3.getJSONArray("trips").getJSONObject(0).remove("arrivals")
        assertThrows(Exception::class.java) { CareCodec.decode(v3.toString().toByteArray()) }
    }
    @Test fun concurrentStoreInstancesDoNotLoseChanges() {
        val p = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
        val e = Elder("e", "가상 어르신")
        val initial = CareData(listOf(p), listOf(e), listOf(PlaceElder("p", "e")))
            .startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 1000)
        store().save(initial)
        val tripId = initial.trips.single().id
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val ui = executor.submit {
                start.await()
                repeat(10) { i -> store().update { it.copy(elders = it.elders + Elder("new-$i", "가상 추가 $i")) } }
            }
            val service = executor.submit {
                start.await()
                repeat(10) { store().update { it.appendDetectedArrivals(tripId, listOf(Arrival("e", "p", 1000, 21000))) } }
            }
            start.countDown()
            ui.get(30, java.util.concurrent.TimeUnit.SECONDS)
            service.get(30, java.util.concurrent.TimeUnit.SECONDS)
            val final = store().loadAndMigrate()
            assertEquals(11, final.elders.size)
            assertEquals(1, final.trips.single().arrivals.size)
            store().update { it.endTrip(tripId, 23000) }
            store().update { it.appendDetectedArrivals(tripId, listOf(Arrival("e", "p", 1000, 21000))) }
            assertEquals(23000L, store().loadAndMigrate().trips.single().endedAt)
        } finally { executor.shutdownNow() }
    }
    @Test fun rejectedTransactionPreservesExistingEncryptedFile() {
        store().save(CareData())
        val original = File(root, "test.enc").readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            store().update { it.copy(links = listOf(PlaceElder("missing", "missing"))) }
        }
        assertArrayEquals(original, File(root, "test.enc").readBytes())
    }
}
