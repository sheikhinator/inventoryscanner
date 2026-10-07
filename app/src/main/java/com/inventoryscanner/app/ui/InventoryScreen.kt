package com.inventoryscanner.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    vm: InventoryViewModel,
    onScan: () -> Unit,
    onCount: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by vm.products.collectAsState()
    val query by vm.query.collectAsState()
    val logs by vm.logs.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.importCsv(ctx, uri) }.getOrDefault(-1)
            snackbar.showSnackbar(if (n >= 0) "Imported $n products" else "Import failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                actions = {
                    TextButton(onClick = { menu = true }) { Text("More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = {
                            menu = false
                            scope.launch { ctx.startActivity(android.content.Intent.createChooser(vm.exportCsv(ctx), "Share inventory")) }
                        })
                        DropdownMenuItem(text = { Text("Import CSV") }, onClick = { menu = false; importer.launch("*/*") })
                        DropdownMenuItem(text = { Text("Count history") }, onClick = { menu = false; showLogs = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, contentDescription = "Add product") }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScan, Modifier.weight(1f)) { Text("Scan barcode") }
                FilledTonalButton(onClick = onCount, Modifier.weight(1f)) { Text("AI count") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            OutlinedTextField(query, { vm.query.value = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search name, barcode, category") })
            Text("${products.size} products · ${products.sumOf { it.quantity }} units",
                Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            if (products.isEmpty()) {
                Text("No products yet. Scan a barcode, tap + to add one, or import a CSV (barcode,name,category,location,quantity).")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(products, key = { it.barcode }) { p ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(p.barcode) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(p.barcode + if (p.location.isNotBlank()) " · ${p.location}" else "",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${p.quantity}", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }

    if (showLogs) AlertDialog(
        onDismissRequest = { showLogs = false },
        confirmButton = { TextButton(onClick = { showLogs = false }) { Text("Close") } },
        title = { Text("Count history") },
        text = {
            val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
            LazyColumn {
                items(logs, key = { it.id }) { l ->
                    Text("${fmt.format(Date(l.createdAt))} · ${l.source} · ${l.count}" +
                        (l.barcode?.let { " · $it" } ?: "") + (if (l.detail.isNotBlank()) "\n${l.detail}" else ""),
                        Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
