package app.darkio

import app.darkio.data.AppEntry
import app.darkio.drawer.normalizeForSearch
import app.darkio.drawer.searchApps
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun normalizationPreservesDanishLettersBothCasesAndDecomposed() {
        listOf(
            "æ" to "æ", "Æ" to "æ", "ø" to "ø", "Ø" to "ø",
            "å" to "å", "Å" to "å", "a\u030A" to "å", "A\u030A" to "å",
        ).forEach { (input, expected) -> assertEquals(expected, normalizeForSearch(input)) }
    }

    @Test
    fun normalizationStripsAccentsOnOtherLetters() {
        listOf("é" to "e", "É" to "e", "è" to "e", "ê" to "e", "ü" to "u", "Ü" to "u", "ñ" to "n", "ç" to "c")
            .forEach { (input, expected) -> assertEquals(expected, normalizeForSearch(input)) }
    }

    @Test
    fun matchingFindsPartsOfNameCaseAndAccentInsensitive() {
        val apps = listOf(AppEntry("1", "Café"), AppEntry("2", "München"), AppEntry("3", "Señor"))
        assertEquals(listOf("Café"), searchApps(apps, "cafe").map { it.label })
        assertEquals(listOf("Café"), searchApps(apps, "CAFÉ").map { it.label })
        assertEquals(listOf("München"), searchApps(apps, "mun").map { it.label })
        assertEquals(listOf("Señor"), searchApps(apps, "senor").map { it.label })
    }

    @Test
    fun matchingDoesNotFoldDanishLetters() {
        val apps = listOf(AppEntry("1", "Ål"), AppEntry("2", "Al"), AppEntry("3", "Øl"), AppEntry("4", "Ol"), AppEntry("5", "Æble"), AppEntry("6", "Abe"))
        assertEquals(listOf("Ål"), searchApps(apps, "ål").map { it.label })
        assertEquals(listOf("Ål"), searchApps(apps, "ÅL").map { it.label })
        assertEquals(listOf("Al"), searchApps(apps, "al").map { it.label })
        assertEquals(listOf("Øl"), searchApps(apps, "øl").map { it.label })
        assertEquals(listOf("Øl"), searchApps(apps, "ØL").map { it.label })
        assertEquals(listOf("Ol"), searchApps(apps, "ol").map { it.label })
        assertEquals(listOf("Æble"), searchApps(apps, "æble").map { it.label })
        assertEquals(listOf("Æble"), searchApps(apps, "ÆBLE").map { it.label })
        assertEquals(listOf("Abe"), searchApps(apps, "abe").map { it.label })
    }

    @Test
    fun rankingNameStartsWithBeforeWordStartsWithBeforeContains() {
        val apps = listOf(AppEntry("3", "Rekord"), AppEntry("1", "Kort"), AppEntry("2", "Google–Kort"))
        assertEquals(listOf("Kort", "Google–Kort", "Rekord"), searchApps(apps, "kor").map { it.label })
    }

    @Test
    fun rankingTiesBrokenWithAppOrder() {
        val apps = listOf(AppEntry("1", "Kort"), AppEntry("2", "Kontakt"), AppEntry("3", "Kamera"))
        assertEquals(listOf("Kamera", "Kontakt", "Kort"), searchApps(apps, "k").map { it.label })
    }

    @Test
    fun blankQueryReturnsEmpty() {
        assertEquals(emptyList<AppEntry>(), searchApps(listOf(AppEntry("1", "App")), "   "))
    }
}
