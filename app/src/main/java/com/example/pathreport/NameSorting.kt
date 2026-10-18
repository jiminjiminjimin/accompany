package com.example.pathreport

import java.text.Collator
import java.util.Locale

fun <T> Iterable<T>.sortedByKoreanName(name: (T) -> String): List<T> {
    val collator = Collator.getInstance(Locale.KOREAN)
    return sortedWith { left, right -> collator.compare(name(left).trim(), name(right).trim()) }
}
