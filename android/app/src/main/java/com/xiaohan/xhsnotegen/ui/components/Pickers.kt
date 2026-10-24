package com.xiaohan.xhsnotegen.ui.components

import com.xiaohan.xhsnotegen.ui.theme.app
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaohan.xhsnotegen.domain.CatalogCountry
import com.xiaohan.xhsnotegen.domain.CatalogRegion
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.PlaceCatalog
import com.xiaohan.xhsnotegen.domain.PlaceName
import com.xiaohan.xhsnotegen.i18n.LanguageStore
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.flow.filter
import java.util.Calendar

// ---------------------------------------------------------------------------
// Wheel
// ---------------------------------------------------------------------------

/**
 * A scrolling column that snaps to one value in the middle, like a date wheel.
 * [onSelected] fires when the wheel comes to rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rows: Int = 5,
    rowHeight: Dp = 44.dp,
) {
    val pad = rows / 2
    val last = (items.size - 1).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, last))
    val fling = rememberSnapFlingBehavior(state)
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    // Blank rows above and below let the first and last value reach the middle.
    val centered by remember {
        derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, last) }
    }

    // Report the value the wheel settled on.
    LaunchedEffect(state, items.size) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            if (items.isNotEmpty() && centered != selectedIndex) onSelected(centered)
        }
    }
    // Follow outside changes (e.g. the day list got shorter).
    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, last)
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != target) state.scrollToItem(target)
    }

    Box(modifier.height(rowHeight * rows), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.app.inset),
        )
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(pad) { Spacer(Modifier.height(rowHeight)) }
            itemsIndexed(items) { i, text ->
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (i == centered) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == centered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(pad) { Spacer(Modifier.height(rowHeight)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Date
// ---------------------------------------------------------------------------

private val DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?""")

/** Year, month and day wheels (and an optional time), for when a photo has no date or you want another. */
@Composable
fun DateWheelDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val now = remember { Calendar.getInstance() }
    val m = remember(initial) { DATE.find(initial) }
    val thisYear = now.get(Calendar.YEAR)
    val years = remember { (thisYear - 40..thisYear + 1).toList() }
    var year by remember { mutableIntStateOf(m?.groupValues?.get(1)?.toInt()?.coerceIn(years.first(), years.last()) ?: thisYear) }
    var month by remember { mutableIntStateOf(m?.groupValues?.get(2)?.toInt()?.coerceIn(1, 12) ?: (now.get(Calendar.MONTH) + 1)) }
    var day by remember { mutableIntStateOf(m?.groupValues?.get(3)?.toInt()?.coerceIn(1, 31) ?: now.get(Calendar.DAY_OF_MONTH)) }
    var withTime by remember { mutableStateOf(m?.groupValues?.get(4)?.isNotEmpty() == true) }
    var hour by remember { mutableIntStateOf(m?.groupValues?.get(4)?.toIntOrNull()?.coerceIn(0, 23) ?: 12) }
    var minute by remember { mutableIntStateOf(m?.groupValues?.get(5)?.toIntOrNull()?.coerceIn(0, 59) ?: 0) }

    val daysInMonth = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (day > daysInMonth) day = daysInMonth
    val zh = LanguageStore.isZh
    val monthNames = remember(zh) {
        if (zh) (1..12).map { "${it}月" }
        else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("When", "时间")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelPicker(years.map { if (zh) "${it}年" else "$it" }, years.indexOf(year), { year = years[it] }, Modifier.weight(1.2f))
                    WheelPicker(monthNames, month - 1, { month = it + 1 }, Modifier.weight(1f))
                    WheelPicker((1..daysInMonth).map { if (zh) "${it}日" else "$it" }, day - 1, { day = it + 1 }, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Add a time", "添加具体时间"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = withTime, onCheckedChange = { withTime = it })
                }
                if (withTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WheelPicker((0..23).map { "%02d".format(it) }, hour, { hour = it }, Modifier.weight(1f), rows = 3)
                        WheelPicker((0..59).map { "%02d".format(it) }, minute, { minute = it }, Modifier.weight(1f), rows = 3)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = "%04d-%02d-%02d".format(year, month, day)
                onConfirm(if (withTime) "$date %02d:%02d".format(hour, minute) else date)
            }) { Text(tr("OK", "确定")) }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text(tr("Clear", "清除")) }
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) }
            }
        },
    )
}

/** "2026-09-29 12:30" → "Sep 29, 2026 · 12:30" / "2026年9月29日 12:30"; anything else as typed. */
fun formatDateForDisplay(raw: String): String {
    val m = DATE.find(raw.trim()) ?: return raw.trim()
    val (y, mo, d) = m.destructured.let { Triple(it.component1().toInt(), it.component2().toInt(), it.component3().toInt()) }
    val time = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { "%02d:%s".format(it.toInt(), m.groupValues[5]) }
    val date = if (LanguageStore.isZh) "${y}年${mo}月${d}日" else
        "${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[mo.coerceIn(1, 12) - 1]} $d, $y"
    return if (time != null) "$date · $time" else date
}

// ---------------------------------------------------------------------------
// Place
// ---------------------------------------------------------------------------

private enum class PlaceStep { COUNTRY, REGION, CITY }

/**
 * Pick a place from lists, country → region → city, with search and a way to type one that
 * isn't listed. Works without internet or Google services. Notes are stored under the Chinese
 * names (the same ones the phone's geocoder gives), whichever language the lists are shown in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerDialog(
    initial: Place,
    showAddress: Boolean,
    onConfirm: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    val zh = LanguageStore.isZh
    var country by remember { mutableStateOf(initial.country.takeIf { it.isNotBlank() }?.let { PlaceCatalog.country(it) ?: CatalogCountry(PlaceName(it, it), "") }) }
    var region by remember {
        mutableStateOf(initial.region.takeIf { it.isNotBlank() }?.let { r -> country?.regions?.firstOrNull { it.name.cn == r }?.name ?: PlaceName(r, r) })
    }
    var city by remember { mutableStateOf(initial.city) }
    var address by remember { mutableStateOf(initial.address) }
    var step by remember {
        mutableStateOf(
            when {
                country == null -> PlaceStep.COUNTRY
                region == null && country!!.regions.isNotEmpty() && initial.city.isBlank() -> PlaceStep.REGION
                else -> PlaceStep.CITY
            }
        )
    }
    var query by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }

    val countries = remember(zh) { PlaceCatalog.countries(zh) }
    val regionsOfCountry = country?.regions.orEmpty()
    val currentRegion: CatalogRegion? = region?.let { r -> regionsOfCountry.firstOrNull { it.name.cn == r.cn } }
    val options: List<PlaceName> = when (step) {
        PlaceStep.COUNTRY -> countries.map { it.name }
        PlaceStep.REGION -> regionsOfCountry.map { it.name }
        PlaceStep.CITY -> currentRegion?.cities ?: if (region == null) country?.cities.orEmpty() else emptyList()
    }
    val shown = options.filter { query.isBlank() || it.cn.contains(query, ignoreCase = true) || it.en.contains(query, ignoreCase = true) }
    val selectedCn = when (step) { PlaceStep.COUNTRY -> country?.name?.cn; PlaceStep.REGION -> region?.cn; PlaceStep.CITY -> city.takeIf { it.isNotBlank() } }

    fun pick(name: PlaceName) {
        query = ""; typed = ""
        when (step) {
            PlaceStep.COUNTRY -> {
                val c = countries.firstOrNull { it.name.cn == name.cn } ?: CatalogCountry(name, "")
                country = c; region = null; city = ""
                step = if (c.regions.isNotEmpty()) PlaceStep.REGION else PlaceStep.CITY
            }
            PlaceStep.REGION -> { region = name; city = ""; step = PlaceStep.CITY }
            PlaceStep.CITY -> city = name.cn
        }
    }

    fun label(n: PlaceName) = n.label(zh)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.app.tile,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Where was it?", "在哪里？"), style = MaterialTheme.typography.headlineSmall)
                }

                // Where you are in country → region → city; tap a step to go back to it.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        FilterChip(
                            selected = step == PlaceStep.COUNTRY, onClick = { step = PlaceStep.COUNTRY; query = "" },
                            label = { Text(country?.name?.let(::label) ?: tr("Country", "国家")) }, shape = CircleShape,
                        )
                    }
                    if (country != null) {
                        if (regionsOfCountry.isNotEmpty()) item {
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(
                                selected = step == PlaceStep.REGION, onClick = { step = PlaceStep.REGION; query = "" },
                                label = { Text(region?.let(::label) ?: tr("Province / state", "省 / 州")) }, shape = CircleShape,
                            )
                        }
                        item {
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(
                                selected = step == PlaceStep.CITY, onClick = { step = PlaceStep.CITY; query = "" },
                                label = {
                                    // The city may have a listed English name.
                                    val listed = (currentRegion?.cities ?: country?.cities.orEmpty()).firstOrNull { it.cn == city }
                                    Text(city.takeIf { it.isNotBlank() }?.let { listed?.label(zh) ?: it } ?: tr("City", "城市"))
                                },
                                shape = CircleShape,
                            )
                        }
                    }
                }

                if (options.size > 8) {
                    TextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(tr("Search", "搜索")) },
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }

                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (step == PlaceStep.REGION) item(key = "skip") {
                        Text(
                            tr("Skip — pick a city directly", "跳过，直接选城市"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { region = null; city = ""; step = PlaceStep.CITY; query = "" }.padding(vertical = 12.dp),
                        )
                    }
                    items(shown, key = { "${step}:${it.cn}:${it.en}" }) { n ->
                        val selected = n.cn == selectedCn
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(n) }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label(n), style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                val other = n.label(!zh)
                                if (other != label(n)) Text(other, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (shown.isEmpty()) item(key = "empty") {
                        Text(
                            if (step == PlaceStep.CITY && options.isEmpty()) tr("No list for this one — type the city below.", "这里没有城市列表，请在下面直接填写城市。")
                            else tr("Nothing matches — type it below.", "没有匹配的，请在下面直接填写。"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }

                // Not in the list: type it. Country needs no city to be saved; a typed city is used as it is.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(when (step) {
                                PlaceStep.COUNTRY -> tr("Not listed? Type the country", "没有？直接填写国家")
                                PlaceStep.REGION -> tr("Not listed? Type the province / state", "没有？直接填写省 / 州")
                                PlaceStep.CITY -> tr("Not listed? Type the city", "没有？直接填写城市")
                            })
                        },
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                    FilledTonalButton(onClick = { pick(PlaceName(typed.trim(), typed.trim())) }, enabled = typed.isNotBlank()) { Text(tr("Use", "使用")) }
                }
                if (showAddress) {
                    TextField(
                        value = address, onValueChange = { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Street address (optional)", "详细地址（选填）")) },
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) }
                    TextButton(
                        onClick = {
                            val p = PlaceCatalog.place(country!!.name.cn, region?.cn.orEmpty(), city, address)
                            // Photo coordinates only still fit if the place didn't change.
                            val same = p.country == initial.country && p.city == initial.city
                            onConfirm(if (same) p.copy(latitude = initial.latitude, longitude = initial.longitude) else p)
                        },
                        enabled = country != null,
                    ) { Text(tr("Done", "完成")) }
                }
            }
        }
    }
}
