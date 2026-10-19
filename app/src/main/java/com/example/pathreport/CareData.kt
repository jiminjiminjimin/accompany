package com.example.pathreport

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StopPlace(val id: String = UUID.randomUUID().toString(), val name: String,
    val address: String, val latitude: Double? = null, val longitude: Double? = null)
data class Elder(val id: String = UUID.randomUUID().toString(), val name: String)
data class PlaceElder(val placeId: String, val elderId: String)
data class CareData(val places: List<StopPlace> = emptyList(), val elders: List<Elder> = emptyList(),
    val links: List<PlaceElder> = emptyList(), val legacyDigest: String? = null, val trips: List<Trip> = emptyList()) {
    fun validate() {
        require(places.map { it.id }.distinct().size == places.size)
        require(elders.map { it.id }.distinct().size == elders.size)
        require(places.all { it.id.isNotBlank() && it.name.isNotBlank() && it.address.isNotBlank() && validCoordinates(it.latitude, it.longitude) })
        require(elders.all { it.id.isNotBlank() && it.name.isNotBlank() })
        require(trips.map { it.id }.distinct().size == trips.size)
        require(trips.count { it.endedAt == null } <= 1)
        trips.forEach { it.validate() }
        require(links.distinct().size == links.size)
        require(links.all { link -> places.any { it.id == link.placeId } && elders.any { it.id == link.elderId } })
    }
    fun withoutPlace(id: String) = copy(places = places.filterNot { it.id == id }, links = links.filterNot { it.placeId == id })
    fun withoutElder(id: String) = copy(elders = elders.filterNot { it.id == id }, links = links.filterNot { it.elderId == id })
}

fun validCoordinates(lat: Double?, lon: Double?): Boolean =
    if (lat == null || lon == null) lat == null && lon == null
    else lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

data class LegacyPerson(val name: String, val address: String)
fun migrateLegacyItems(data: CareData, entries: List<LegacyPerson>, digest: String): CareData {
    if (data.legacyDigest == digest) return data
    check(data.legacyDigest == null)
    require(entries.all { it.name.isNotBlank() && it.address.isNotBlank() })
    val places = data.places.toMutableList(); val elders = data.elders.toMutableList(); val links = data.links.toMutableList()
    entries.forEach { item ->
        val place = places.find { it.address == item.address } ?: StopPlace(name = "이전 장소 ${places.size + 1}", address = item.address).also { places.add(it) }
        val elder = Elder(name = item.name) // Identical names are not identities.
        elders.add(elder); links.add(PlaceElder(place.id, elder.id))
    }
    return data.copy(places = places, elders = elders, links = links, legacyDigest = digest).also { it.validate() }
}

object CareCodec {
    fun encode(data: CareData): ByteArray {
        data.validate()
        return JSONObject().put("version", 4).put("legacyDigest", data.legacyDigest)
            .put("trips", TripCodec.encode(data.trips))
            .put("places", JSONArray().apply { data.places.forEach { p -> put(JSONObject()
                .put("id", p.id).put("name", p.name).put("address", p.address)
                .put("lat", p.latitude).put("lon", p.longitude)) } })
            .put("elders", JSONArray().apply { data.elders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
            .put("links", JSONArray().apply { data.links.forEach { put(JSONObject().put("place", it.placeId).put("elder", it.elderId)) } })
            .toString().toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes: ByteArray): CareData {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val version = root.getInt("version")
        require(version in 1..4)
        val p = root.getJSONArray("places"); val e = root.getJSONArray("elders"); val l = root.getJSONArray("links")
        return CareData(List(p.length()) { i -> p.getJSONObject(i).let {
            StopPlace(it.getString("id"), it.getString("name"), it.getString("address"),
                if (it.isNull("lat")) null else it.getDouble("lat"), if (it.isNull("lon")) null else it.getDouble("lon"))
        } }, List(e.length()) { i -> e.getJSONObject(i).let { Elder(it.getString("id"), it.getString("name")) } },
            List(l.length()) { i -> l.getJSONObject(i).let { PlaceElder(it.getString("place"), it.getString("elder")) } },
            if (root.isNull("legacyDigest")) null else root.getString("legacyDigest"),
            if (version == 1) emptyList() else TripCodec.decode(root.getJSONArray("trips"), version)).also { it.validate() }
    }
}
