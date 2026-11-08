package app.darkio

import app.darkio.data.AppEntry
import app.darkio.data.appOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private fun sorted(vararg labels: String) =
        labels.mapIndexed { i, l -> AppEntry("id$i", l) }.sortedWith(appOrder).map { it.label }

    @Test
    fun danishLettersAeOeAaSortLastInDanishOrder() {
        assertEquals(
            listOf("Aften", "Zoom", "Æble", "Ærtepotter", "Øl", "Åbn"),
            sorted("Øl", "Zoom", "Åbn", "Æble", "Aften", "Ærtepotter"),
        )
    }

    @Test
    fun sortingIgnoresCase() {
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("Banana", "cherry", "apple"))
    }
}
