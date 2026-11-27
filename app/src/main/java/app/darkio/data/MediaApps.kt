package app.darkio.data

/** Brugerens valgte medie-apps i gemt rækkefølge; skjulte og afinstallerede apps udelades, men deres rækker bevares i Room. */
fun mediaAppList(visible: List<AppEntry>, rows: List<MediaApp>): List<AppEntry> {
    val byId = visible.associateBy { it.id }
    return rows.sortedBy { it.position }.mapNotNull { byId[appId(it.packageName, it.activity, it.userSerial)] }
}
