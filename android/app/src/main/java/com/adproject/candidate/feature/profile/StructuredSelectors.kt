package com.adproject.candidate.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.adproject.candidate.core.designsystem.AdBorder
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText

// Age selectable via the number wheel on the profile edit screen.
val AGE_OPTIONS: List<Int> = (16..80).toList()

/**
 * A read-only, tappable form row that shows a label and the current selection (or a muted
 * placeholder when nothing is selected). Tapping it opens a selection panel. Selecting via a
 * panel keeps the form field visually consistent with the free-text OutlinedTextFields around it,
 * while supporting an error message from server/field validation.
 */
@Composable
fun SelectorField(
    label: String,
    value: String?,
    placeholder: String,
    isError: Boolean = false,
    errorText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, if (isError) MaterialTheme.colorScheme.error else AdBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, color = AdMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value ?: placeholder,
                    modifier = Modifier.weight(1f),
                    color = if (value.isNullOrBlank()) AdMuted else AdText,
                    fontSize = 14.sp,
                )
                Text("›", color = AdMuted, fontSize = 18.sp)
            }
        }
        if (isError && errorText != null) {
            Text(
                errorText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

/**
 * Single-selection panel over an arbitrary option list. Works on a local copy so dismissing (tap
 * outside, back, or Cancel) never mutates the enclosing form state; only Confirm commits. A
 * leading "clear" row is shown when [clearLabel] is non-null, allowing the value to be unset.
 */
@Composable
fun <T> SingleSelectSheet(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    initialSelected: T?,
    clearLabel: String? = "Not specified",
    onConfirm: (T?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AdText) },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                if (clearLabel != null) {
                    item { RadioRow(clearLabel, selected == null) { selected = null } }
                }
                itemsIndexed(options) { _, option ->
                    RadioRow(optionLabel(option), selected == option) { selected = option }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Single-selection panel for the profile location: pick from the curated list or add a custom
 * location. A pre-existing custom value (not in [options]) is surfaced as a selected row so it is
 * never silently dropped when the panel is reopened.
 */
@Composable
fun LocationSelectSheet(
    title: String,
    options: List<String>,
    initialSelected: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelected) }
    var custom by remember { mutableStateOf("") }
    val existingCustom = remember(initialSelected, options) {
        initialSelected?.takeIf { it.isNotBlank() && it !in options }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AdText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    if (existingCustom != null) {
                        item { RadioRow(existingCustom, selected == existingCustom) { selected = existingCustom } }
                    }
                    itemsIndexed(options) { _, option ->
                        RadioRow(option, selected == option) { selected = option }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        custom, { custom = it }, Modifier.weight(1f),
                        label = { Text("Add a location") }, singleLine = true,
                    )
                    TextButton(
                        enabled = custom.isNotBlank(),
                        onClick = {
                            val value = custom.trim()
                            if (value.isNotEmpty()) selected = value
                            custom = ""
                        },
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Searchable multi-select panel over a string catalog with in-panel custom item creation. Custom
 * values already selected (but not in the catalog) remain visible and removable, so reopening the
 * panel does not lose them. Confirm commits the working copy; Cancel/dismiss discards it.
 */
@Composable
fun SearchableMultiSelectSheet(
    title: String,
    options: List<String>,
    initialSelected: List<String>,
    searchPlaceholder: String,
    addPlaceholder: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(initialSelected) } }
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }

    val filteredCatalog = remember(options, query) {
        if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
    }
    val matchingCustom = selected.filter { it !in options }.let { existing ->
        if (query.isBlank()) existing else existing.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AdText)
                Text("${selected.size} selected", fontSize = 12.sp, color = AdMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(searchPlaceholder) }, singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    itemsIndexed(filteredCatalog) { _, option ->
                        CheckRow(option, option in selected) {
                            if (option in selected) selected.remove(option) else selected.add(option)
                        }
                    }
                    itemsIndexed(matchingCustom) { _, option ->
                        CheckRow(option, checked = true) { selected.remove(option) }
                    }
                    if (filteredCatalog.isEmpty() && matchingCustom.isEmpty()) {
                        item { Text("No matches", color = AdMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        custom, { custom = it }, Modifier.weight(1f),
                        label = { Text(addPlaceholder) }, singleLine = true,
                    )
                    TextButton(
                        enabled = custom.isNotBlank(),
                        onClick = {
                            val value = custom.trim()
                            if (value.isNotEmpty() && value !in selected) selected.add(value)
                            custom = ""
                        },
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Multi-select panel over a fixed enum list (workplace / employment type). No search or custom
 * items — the list is small and closed. Works on a local copy until Confirm.
 */
@Composable
fun <T> EnumMultiSelectSheet(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    initialSelected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateListOf<T>().apply { addAll(initialSelected) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AdText)
                Text("${selected.size} selected", fontSize = 12.sp, color = AdMuted)
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                itemsIndexed(options) { _, option ->
                    CheckRow(optionLabel(option), option in selected) {
                        if (option in selected) selected.remove(option) else selected.add(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toSet()) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Number wheel panel: a "Not specified" clear row plus a snapping vertical wheel. The clear row
 * sets the value to null; scrolling the wheel selects a value. Confirm commits, Cancel discards.
 */
@Composable
fun <T> NumberWheelSheet(
    title: String,
    values: List<T>,
    labelOf: (T) -> String,
    initialValue: T?,
    clearLabel: String,
    onConfirm: (T?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AdText) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selected = null }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == null, onClick = { selected = null })
                    Text(clearLabel, Modifier.weight(1f), color = AdText, fontSize = 14.sp)
                }
                NumberWheel(
                    values = values,
                    initialValue = selected,
                    labelOf = labelOf,
                    onValueChange = { selected = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A snapping vertical number wheel. The selected value is the item snapped to the top of the
 * viewport; a highlight band marks it. The wheel positions itself on the initial value once and
 * reports the snapped value upward as it changes. See [NumberWheelSheet] for the enclosing panel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> NumberWheel(
    values: List<T>,
    initialValue: T?,
    labelOf: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // -1 means nothing is selected yet (the "Not specified" state).
    val initialIndex = remember {
        (initialValue?.let { values.indexOf(it) } ?: -1).coerceIn(-1, values.lastIndex.coerceAtLeast(0))
    }
    var selectedIndex by remember { mutableStateOf(initialIndex) }
    val scope = rememberCoroutineScope()

    // Position the wheel on the existing value once. Without a value it starts at the top with
    // nothing selected.
    LaunchedEffect(values) {
        if (values.isNotEmpty()) listState.scrollToItem(initialIndex.coerceAtLeast(0), 0)
    }

    // When the enclosing sheet clears the value (tapping "Not specified"), reset the wheel so the
    // stale highlight is removed and nothing is reported until the user interacts again.
    LaunchedEffect(initialValue) {
        if (initialValue == null) {
            selectedIndex = -1
            if (values.isNotEmpty()) listState.scrollToItem(0, 0)
        }
    }

    // Report a value only after the user actually scrolls the wheel — never on first composition,
    // so simply opening and confirming does not turn "Not specified" into the first item.
    LaunchedEffect(listState) {
        var hasScrolled = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    hasScrolled = true
                } else if (hasScrolled) {
                    val index = listState.firstVisibleItemIndex
                    if (index in values.indices) {
                        selectedIndex = index
                        onValueChange(values[index])
                    }
                }
            }
    }

    Box(modifier.height(220.dp)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AdTealSoft),
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 176.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(values) { index, value ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable {
                            selectedIndex = index
                            onValueChange(value)
                            scope.launch { listState.scrollToItem(index, 0) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labelOf(value),
                        color = if (selected) AdTealDark else AdMuted,
                        fontSize = if (selected) 18.sp else 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text, Modifier.weight(1f), color = AdText, fontSize = 14.sp)
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text, Modifier.weight(1f), color = AdText, fontSize = 14.sp)
    }
}
