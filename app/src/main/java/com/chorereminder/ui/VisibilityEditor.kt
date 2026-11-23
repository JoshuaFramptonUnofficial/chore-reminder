package com.chorereminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.chorereminder.data.VisibilitySpec

@Composable
fun VisibilityEditor(rule: VisibilitySpec, onChange: (VisibilitySpec) -> Unit) {
    val modes = linkedMapOf("IMMEDIATE" to "Immediately", "AFTER_COMPLETION" to "After completion", "BEFORE_DUE" to "Before next due date")
    var menu by remember { mutableStateOf(false) }
    Column {
        Box {
            TextButton(onClick = { menu = true }) { Text(modes[rule.mode] ?: "Immediately") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                modes.forEach { (mode, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onChange(rule.copy(mode = mode, amount = rule.amount.coerceIn(1, 999))) })
                }
            }
        }
        if (rule.mode != "IMMEDIATE") {
            var amount by remember(rule.amount) { mutableStateOf(rule.amount.takeIf { it > 0 }?.toString() ?: "") }
            OutlinedTextField(
                value = amount,
                onValueChange = { text ->
                    amount = text.filter(Char::isDigit).take(3)
                    onChange(rule.copy(amount = amount.toIntOrNull() ?: 0))
                },
                label = { Text("Amount (1–999)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amount.toIntOrNull() !in 1..999,
                modifier = Modifier.fillMaxWidth(),
            )
            var units by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { units = true }) { Text(rule.unit.lowercase().replaceFirstChar(Char::uppercase)) }
                DropdownMenu(expanded = units, onDismissRequest = { units = false }) {
                    listOf("DAYS", "WEEKS", "MONTHS").forEach { unit ->
                        DropdownMenuItem(text = { Text(unit.lowercase().replaceFirstChar(Char::uppercase)) },
                            onClick = { units = false; onChange(rule.copy(unit = unit)) })
                    }
                }
            }
        }
        Text("Due and overdue chores always show. After-completion rules start only after the first completion.",
            style = MaterialTheme.typography.bodySmall)
    }
}
