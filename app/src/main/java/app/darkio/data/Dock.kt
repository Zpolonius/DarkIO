package app.darkio.data

const val DOCK_SIZE = 4

/** Dockens felter: hver synlig favorit i sin gemte plads. Skjulte og afinstallerede apps udelades, men deres plads er gemt i Room, så de vender tilbage dertil. */
fun dockSlots(visible: List<AppEntry>, slots: Map<String, Int>): List<AppEntry?> {
    val shown = arrayOfNulls<AppEntry>(DOCK_SIZE)
    visible.forEach { app ->
        val slot = slots[app.id]
        if (slot != null && slot in 0 until DOCK_SIZE && shown[slot] == null) shown[slot] = app
    }
    return shown.toList()
}

/** Første felt der ikke er gemt til en installeret app (også skjulte), eller null når alle fire er optaget. */
fun firstFreeSlot(used: Collection<Int>): Int? = (0 until DOCK_SIZE).firstOrNull { it !in used }

/** Nye pladser (id til plads) når appen i [from] flyttes til [to]; en app der står der bytter plads. Tom hvis intet ændres, også når [to] er [reserved] af en skjult favorit. */
fun reorderedSlots(shown: List<AppEntry?>, from: Int, to: Int, reserved: Set<Int>): Map<String, Int> {
    val moved = shown.getOrNull(from)
    if (moved == null || from == to || to !in 0 until DOCK_SIZE || to in reserved) return emptyMap()
    return buildMap {
        put(moved.id, to)
        shown[to]?.let { put(it.id, from) }
    }
}
