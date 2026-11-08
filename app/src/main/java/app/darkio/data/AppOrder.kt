package app.darkio.data

import java.text.Collator
import java.util.Locale

private val danish = Collator.getInstance(Locale.forLanguageTag("da-DK"))

/** Dansk sortering af app-navne: æ, ø, å sidst; uafhængig af store/små bogstaver. */
val appOrder: Comparator<AppEntry> = Comparator<AppEntry> { a, b -> danish.compare(a.label, b.label) }.thenBy { it.id }
