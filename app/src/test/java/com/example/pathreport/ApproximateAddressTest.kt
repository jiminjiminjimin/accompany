package com.example.pathreport

import org.junit.Assert.assertEquals
import org.junit.Test

class ApproximateAddressTest {
    @Test fun hidesBuildingNumbersButKeepsRoadNameDigits() {
        assertEquals("서울특별시 동작구 국사봉8길 부근", approximateAddress("대한민국 서울특별시 동작구 국사봉8길 3"))
        assertEquals("서울특별시 동작구 상도로15마길 부근", approximateAddress("대한민국 서울특별시 동작구 상도로15마길 28 101동 203호"))
        assertEquals("서울특별시 동작구 성대로14길 부근", approximateAddress("대한민국 서울특별시 동작구 성대로14길 74 (상도동)"))
    }
    @Test fun hidesLotNumbersAndUnrecognizedDetails() {
        assertEquals("서울특별시 동작구 상도동 부근", approximateAddress("서울특별시 동작구 상도동 123-45 101동 203호"))
        assertEquals("등록 장소 부근", approximateAddress("123 Main Street Apt 4"))
        assertEquals("등록 장소 부근", approximateAddress(""))
    }
    @Test fun usesLandmarksOnlyWhenRoadAndDistrictAreMissing() {
        assertEquals("신대방삼거리역 부근", approximateAddress("서울특별시 신대방삼거리역"))
        assertEquals("보라매공원 부근", approximateAddress("대한민국 서울특별시 보라매공원"))
        assertEquals("행복한하우스 부근", approximateAddress("서울특별시 행복한하우스 101동 203호"))
        assertEquals("서울특별시 동작구 상도로 부근", approximateAddress("서울특별시 동작구 상도로 123 신대방삼거리역"))
        assertEquals("서울특별시 동작구 상도동 부근", approximateAddress("서울특별시 동작구 상도동 123 신대방삼거리역"))
        assertEquals("서울특별시 부근", approximateAddress("서울특별시"))
    }
    @Test fun usesPlaceNamesWithoutKnownSuffixesAndOmitsDetails() {
        assertEquals("보라매 부근", approximateAddress("서울특별시 보라매 (현대에이치티)"))
        assertEquals("보라매 부근", approximateAddress("대한민국 서울특별시 동작구 보라매 (현대에이치티)"))
        assertEquals("보라매 부근", approximateAddress("서울특별시 보라매 101동 203호"))
        assertEquals("행복한 쉼터 부근", approximateAddress("서울특별시 행복한 쉼터 (본관)"))
        assertEquals("서울특별시 부근", approximateAddress("서울특별시 101동 203호"))
    }
}