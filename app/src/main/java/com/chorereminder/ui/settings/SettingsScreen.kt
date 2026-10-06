package com.chorereminder.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.CategoryWithCount
import com.chorereminder.data.UNCATEGORIZED_ID
import com.chorereminder.notify.Notifications

/** FR-15/FR-16 plus the notifications-denied explainer from the I/O matrix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF: the user picks the destination, so no storage permission is needed.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::import) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationStatusCard(context)

            CategoriesSection(
                categories = categories,
                onAdd = viewModel::addCategory,
                onRename = viewModel::renameCategory,
                onDelete = viewModel::deleteCategory,
            )

            Text(
                "Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Export writes every chore, category and completion to a single JSON " +
                    "file you choose. Import replaces everything currently in the app " +
                    "with the contents of that file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Export") }

                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "*/*"),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Import") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Chore Reminder works entirely on this device. It has no internet " +
                    "permission, no account and no sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * I/O matrix, "Notifications denied": the app stays usable and Settings explains
 * that reminders are off, with a shortcut to the system toggle.
 */
@Composable
private fun NotificationStatusCard(context: android.content.Context) {
    val enabled = remember { Notifications.canPost(context) }
    if (enabled) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Reminders are off",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Notifications are disabled for this app, so chores will not remind " +
                    "you. Everything else still works -- you can mark chores complete " +
                    "from the list at any time.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }) {
                Text("Open notification settings")
            }
        }
    }
}

/** Rename and remove categories from one place -- chores are never deleted by this. */
@Composable
private fun CategoriesSection(
    categories: List<CategoryWithCount>,
    onAdd: (String) -> Unit,
    onRename: (Long, String, (Boolean) -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<CategoryWithCount?>(null) }
    var deleting by remember { mutableStateOf<CategoryWithCount?>(null) }

    Column {
        Text(
            "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))

        Card(colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                categories.forEach { row ->
                    CategoryRow(
                        row = row,
                        onRename = { renaming = row },
                        onDelete = { deleting = row },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New category") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    onAdd(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
            ) { Text("Add") }
        }
    }

    renaming?.let { row ->
        RenameCategoryDialog(
            row = row,
            onDismiss = { renaming = null },
            onConfirm = { name -> onRename(row.category.id, name) { ok -> if (ok) renaming = null } },
        )
    }

    deleting?.let { row ->
        DeleteCategoryDialog(
            row = row,
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(row.category.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun CategoryRow(
    row: CategoryWithCount,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val isUncategorized = row.category.id == UNCATEGORIZED_ID
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (row.choreCount == 1) "1 chore" else "${row.choreCount} chores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isUncategorized) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename ${row.category.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${row.category.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameCategoryDialog(
    row: CategoryWithCount,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(row.category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteCategoryDialog(
    row: CategoryWithCount,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${row.category.name}\"?") },
        text = {
            Text(
                if (row.choreCount > 0) {
                    "${row.choreCount} ${if (row.choreCount == 1) "chore" else "chores"} will " +
                        "move to Uncategorized. The chores themselves are kept."
                } else {
                    "This category has no chores in it."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
