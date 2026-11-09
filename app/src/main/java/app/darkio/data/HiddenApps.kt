package app.darkio.data

/** Installerede apps delt i dem, der vises overalt, og dem, brugeren har skjult, samt gemte dockpladser (id til plads). */
data class AppLists(val visible: List<AppEntry>, val hidden: List<AppEntry>, val favoriteSlots: Map<String, Int> = emptyMap())

/** Eneste sted, der filtrerer skjulte apps; skuffe, kategorier og søgning bygger på [AppLists.visible]. */
fun splitApps(installed: List<AppEntry>, hiddenIds: Set<String>): AppLists {
    val (hidden, visible) = installed.partition { it.id in hiddenIds }
    return AppLists(visible, hidden)
}
