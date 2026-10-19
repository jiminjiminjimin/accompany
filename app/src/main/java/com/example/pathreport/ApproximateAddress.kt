package com.example.pathreport

/** Display only: never replace the address stored on StopPlace. */
internal fun approximateAddress(address: String): String {
    val normalized = address.trim().removePrefix("대한민국").trim()
        .replace(Regex("\\s+"), " ")
        .substringBefore('(').substringBefore(',').trim()
    // Keep digits belonging to road names (e.g. 상도로15마길), but omit building numbers.
    val road = Regex("(?:^|\\s)[가-힣A-Za-z0-9·.]+(?:대로|로|길)(?=\\s|[0-9]|$)").find(normalized)
    if (road != null) return normalized.substring(0, road.range.last + 1).trim() + " 부근"
    // Lot-number addresses: stop at a named district, never an apartment unit like 101동.
    val district = Regex("(?:^|\\s)[가-힣]+(?:읍|면|동|리|가)(?=\\s|[0-9]|$)")
        .findAll(normalized).lastOrNull()
    if (district != null) return normalized.substring(0, district.range.last + 1).trim() + " 부근"
    // Without a road or district, retain a recognizable landmark instead of only the city.
    val landmark = Regex("(?:^|\\s)([가-힣A-Za-z][가-힣A-Za-z0-9·.-]*(?:역|공원|시장|병원|학교|대학교|도서관|주민센터|복지관|회관|터미널|정류장|백화점|마트|아파트|빌딩|타워|빌라|하우스))(?=\\s|$)")
        .find(normalized)
    if (landmark != null) return landmark.groupValues[1] + " 부근"
    // Place names need not end in a known suffix (e.g. 보라매). Remove leading
    // administrative areas and stop before numbers/units instead of showing them.
    val areaToken = Regex("[가-힣]+(?:특별자치도|특별자치시|특별시|광역시|도|시|군|구)")
    val placeWords = normalized.split(' ').dropWhile { areaToken.matches(it) }
        .takeWhile { Regex("[가-힣A-Za-z·-]+").matches(it) }
    if (placeWords.isNotEmpty()) return placeWords.joinToString(" ") + " 부근"
    val region = Regex("(?:^|\\s)[가-힣]+(?:시|군|구)(?=\\s|$)").findAll(normalized).lastOrNull()
    return region?.let { normalized.substring(0, it.range.last + 1).trim() + " 부근" } ?: "등록 장소 부근"
}