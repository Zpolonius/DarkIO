package app.darkio.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.data.appOrder
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.text.Normalizer
import java.util.Locale

private val markRegex = Regex("\\p{M}+")
private val danish = Locale.forLanguageTag("da-DK")
private val wordsRegex = Regex("[\\s\\p{P}]+")

/** Normaliserer tekst til søgning: accenter fjernes (é->e, ü->u), men æ, ø og å bevares. */
fun normalizeForSearch(text: String): String {
    val nfc = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(danish).replace('å', '\uE000')
    return markRegex.replace(Normalizer.normalize(nfc, Normalizer.Form.NFD), "").replace('\uE000', 'å')
}

/** 0 = navn starter med søgning, 1 = ord starter med den, 2 = navn indeholder den, null = intet match. */
private fun matchRank(label: String, normQuery: String): Int? {
    val n = normalizeForSearch(label)
    if (n.startsWith(normQuery)) return 0
    if (n.split(wordsRegex).any { it.isNotEmpty() && it.startsWith(normQuery) }) return 1
    return if (n.contains(normQuery)) 2 else null
}

fun searchApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = normalizeForSearch(query.trim())
    if (q.isEmpty()) return emptyList()
    val ranks = HashMap<String, Int>(apps.size)
    return apps.filter { app -> matchRank(app.label, q)?.also { ranks[app.id] = it } != null }
        .sortedWith { a, b ->
            val cmp = ranks.getValue(a.id).compareTo(ranks.getValue(b.id))
            if (cmp != 0) cmp else appOrder.compare(a, b)
        }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = DarkIOColors.Surface,
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().height(DarkIOSize.FieldHeight).clip(DarkIOShapes.Lg)
            .background(background).border(1.dp, if (isFocused) DarkIOColors.Accent else DarkIOColors.Line, DarkIOShapes.Lg)
            .padding(horizontal = DarkIOSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(">", style = DarkIOType.Input.copy(color = if (isFocused) DarkIOColors.Accent else DarkIOColors.InkMuted))
        Spacer(Modifier.width(DarkIOSpace.S2))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) BasicText(stringResource(R.string.search_placeholder), style = DarkIOType.Input.copy(color = DarkIOColors.InkMuted))
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("searchField").onFocusChanged { isFocused = it.isFocused },
                textStyle = DarkIOType.Input.copy(color = DarkIOColors.Ink), cursorBrush = SolidColor(DarkIOColors.Accent),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
    }
}

@Composable
fun SearchResultsPane(results: List<AppEntry>, onLaunch: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty()) {
        val text = stringResource(R.string.search_no_results)
        val styled = buildAnnotatedString {
            append(text.removeSuffix("_"))
            withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("_") }
        }
        Box(modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchMin).testTag("searchEmptyState").padding(DarkIOSpace.S4), Alignment.Center) {
            BasicText(styled, style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted))
        }
    } else {
        LazyColumn(modifier.fillMaxWidth(), reverseLayout = true) {
            items(results, key = { it.id }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchMin).clickable(role = Role.Button) { onLaunch(app) }
                        .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, size = DarkIOSize.IconTileSm)
                    Spacer(Modifier.width(DarkIOSpace.S3))
                    BasicText(app.label, style = DarkIOType.Item.copy(color = DarkIOColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
