package app.darkio.data

const val PAGE_COUNT = 3

/** Side-id for arbejdsrækken (-1 så de 3 personlige sider 0..2 ikke berøres). */
const val WORK_SHORTCUTS_PAGE = -1

/** Genvejsrækkens pladser på [page] (id til plads). Rækkens størrelse og pladsernes regler er dockens: [DOCK_SIZE], [dockSlots], [firstFreeSlot] og [reorderedSlots]. */
fun shortcutSlotsOnPage(rows: List<PageShortcut>, page: Int): Map<String, Int> = rows.filter { it.page == page }.associate { appId(it.packageName, it.activity, it.userSerial) to it.slot }
