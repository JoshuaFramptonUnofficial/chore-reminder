package com.chorereminder.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chorereminder.data.IconType
import com.chorereminder.ui.theme.bouncyClickable

/**
 * FR-4: a dual icon picker presented as two clearly separated tabs -- a curated
 * Material set and emoji -- rather than one long mixed grid.
 */
@Composable
fun IconPicker(
    selectedType: IconType,
    selectedValue: String,
    onSelect: (IconType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(if (selectedType == IconType.EMOJI) 1 else 0) }

    Column(modifier) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Icons") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Emoji") })
        }
        when (tab) {
            0 -> MaterialIconTab(selectedType, selectedValue, onSelect)
            else -> EmojiTab(selectedType, selectedValue, onSelect)
        }
    }
}

@Composable
private fun MaterialIconTab(
    selectedType: IconType,
    selectedValue: String,
    onSelect: (IconType, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query) { searchIcons(query) }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search icons") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (groups.isEmpty()) {
            Text(
                "No icons match \"$query\" -- try the Emoji tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 56.dp),
            modifier = Modifier.heightIn(max = 300.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${group.title}") {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(group.icons, key = { it.key }) { icon ->
                    val selected = selectedType == IconType.MATERIAL && selectedValue == icon.key
                    PickerCell(
                        selected = selected,
                        onClick = { onSelect(IconType.MATERIAL, icon.key) },
                    ) {
                        Icon(
                            imageVector = iconForKey(icon.key),
                            contentDescription = icon.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiTab(
    selectedType: IconType,
    selectedValue: String,
    onSelect: (IconType, String) -> Unit,
) {
    val custom = if (selectedType == IconType.EMOJI) selectedValue else ""

    Column {
        // FR-4 prefers the system emoji keyboard over a bespoke grid, so this field
        // accepts anything the user types; the grid below is just a shortcut.
        OutlinedTextField(
            value = custom,
            onValueChange = { typed ->
                val trimmed = typed.trim()
                if (trimmed.isNotEmpty()) onSelect(IconType.EMOJI, trimmed)
            },
            label = { Text("Type any emoji") },
            supportingText = { Text("Use your keyboard's emoji button for anything else.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 52.dp),
            modifier = Modifier.heightIn(max = 200.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(SUGGESTED_EMOJI, key = { it }) { emoji ->
                val selected = selectedType == IconType.EMOJI && selectedValue == emoji
                PickerCell(
                    selected = selected,
                    onClick = { onSelect(IconType.EMOJI, emoji) },
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .bouncyClickable(pressedScale = 0.82f, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
