package com.example.pathreport

import org.json.JSONArray
import org.json.JSONObject

internal object TripCodec {
    fun encode(trips: List<Trip>) = JSONArray().apply {
        trips.forEach { trip -> put(JSONObject().put("id", trip.id).put("direction", trip.direction.name)
            .put("startedAt", trip.startedAt).put("endedAt", trip.endedAt)
            .put("arrivals", JSONArray().apply { trip.arrivals.forEach { arrival ->
                put(JSONObject().put("elderId", arrival.elderId).put("placeId", arrival.placeId)
                    .put("estimatedAt", arrival.estimatedAt).put("confirmedAt", arrival.confirmedAt).put("source", arrival.source))
            } })
            .put("targets", JSONArray().apply {
                trip.targets.forEach { target -> put(JSONObject().put("elderId", target.elderId)
                    .put("elderName", target.elderName).put("place", JSONObject()
                        .put("id", target.place.id).put("name", target.place.name).put("address", target.place.address)
                        .put("lat", target.place.latitude).put("lon", target.place.longitude))) }
            })) }
    }
    fun decode(array: JSONArray, version: Int): List<Trip> = List(array.length()) { index ->
        val trip = array.getJSONObject(index)
        val targets = trip.getJSONArray("targets")
        Trip(id = trip.getString("id"), direction = TripDirection.valueOf(trip.getString("direction")),
            startedAt = trip.getLong("startedAt"), endedAt = if (trip.isNull("endedAt")) null else trip.getLong("endedAt"),
            arrivals = if (version < 3) emptyList() else trip.getJSONArray("arrivals").let { array ->
                List(array.length()) { i -> array.getJSONObject(i).let { item ->
                    Arrival(item.getString("elderId"), item.getString("placeId"), item.getLong("estimatedAt"),
                        item.getLong("confirmedAt"), item.getString("source"))
                } }
            },
            targets = List(targets.length()) { i ->
                val target = targets.getJSONObject(i)
                val p = target.getJSONObject("place")
                TripTarget(target.getString("elderId"), target.getString("elderName"),
                    StopPlace(p.getString("id"), p.getString("name"), p.getString("address"), p.getDouble("lat"), p.getDouble("lon")))
            })
    }
}
