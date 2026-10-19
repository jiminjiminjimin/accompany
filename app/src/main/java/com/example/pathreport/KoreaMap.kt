package com.example.pathreport

/** Includes Jeju, Ulleungdo and Dokdo. Search results and new map pins stay in this area. */
internal fun inKoreaMap(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in 32.5..39.0 && longitude in 124.0..132.5

internal fun mapErrorMessage(code: Int, http: Boolean = false): String = when {
    http && code in listOf(401, 403, 418, 429) -> "지도 서버가 요청을 제한했습니다. 잠시 후 다시 불러오기를 눌러 주세요."
    http -> "지도 서버가 응답하지 못했습니다. 잠시 후 다시 시도해 주세요."
    code == -2 -> "지도 서버 주소를 찾지 못했습니다. 기기의 네트워크·DNS 상태를 확인해 주세요."
    code == -11 -> "지도 서버의 보안 연결에 실패했습니다. 기기의 날짜와 시간을 확인해 주세요."
    code == -8 -> "지도 응답이 늦어지고 있습니다. 다시 불러오기를 눌러 주세요."
    else -> "배경 지도에 연결하지 못했습니다. 기기 연결 또는 지도 서버 상태를 확인해 주세요."
}
