package com.example.pathreport

import org.junit.Assert.assertEquals
import org.junit.Test

class NameSortingTest {
    @Test fun sortsKoreanNamesAfterAddingAndRenaming() {
        val original = listOf("이무진", "장문철", "김지환", "황윤상")
        assertEquals(listOf("김지환", "로이조", "이무진", "장문철", "황윤상"),
            (original + "로이조").sortedByKoreanName { it })
        assertEquals(listOf("강민수", "김지환", "이무진", "장문철"),
            original.map { if (it == "황윤상") "강민수" else it }.sortedByKoreanName { it })
        assertEquals("이무진", original.first())
    }

    @Test fun sortsPlaceNamesAndKeepsDuplicateNames() {
        val names = listOf("황 집", "장 집", "두산아파트", "김 집", "김 집")
        assertEquals(listOf("김 집", "김 집", "두산아파트", "장 집", "황 집"), names.sortedByKoreanName { it })
    }
}
