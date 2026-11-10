package app.darkio.data

const val PAGE_COUNT = 3

/** Genvejsrækkens pladser på [page] (id til plads). Rækkens størrelse og pladsernes regler er dockens: [DOCK_SIZE], [dockSlots], [firstFreeSlot] og [reorderedSlots]. */
fun shortcutSlotsOnPage(rows: List<PageShortcut>, page: Int): Map<String, Int> = rows.filter { it.page == page }.associate { appId(it.packageName, it.activity, it.userSerial) to it.slot }
