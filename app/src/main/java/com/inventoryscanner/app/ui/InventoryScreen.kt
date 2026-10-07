package com.inventoryscanner.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inventoryscanner.app.data.Master
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    vm: InventoryViewModel,
    onScan: () -> Unit, onCount: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val dept by vm.deptFilter.collectAsState()
    val logs by vm.logs.collectAsState()
    val settings by vm.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val r = vm.importFile(ctx, uri)
            snackbar.showSnackbar(
                r.problem ?: "Imported ${r.items} items, ${r.barcodes} barcodes" +
                    if (r.badBarcodes > 0) " (${r.badBarcodes} barcodes unreadable — Excel damaged them; scan to link)" else "")
        }
    }

    val counted = items.count { it.countedQty != null }
    val varUnits = items.sumOf { it.variance ?: 0 }
    val varPkr = items.sumOf { (it.variance ?: 0) * it.price }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Stock count"); Text("${settings.store} · ${settings.role}", style = MaterialTheme.typography.labelSmall) } },
                colors = brandBarColors(),
                actions = {
                    TextButton(onClick = { menu = true }) { Text("More", color = Color.White) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Import items (.xlsx / .csv)") }, onClick = { menu = false; importer.launch("*/*") })
                        DropdownMenuItem(text = { Text("Export counts (CSV)") }, onClick = {
                            menu = false
                            scope.launch { ctx.startActivity(android.content.Intent.createChooser(vm.exportCsv(ctx), "Share counts")) }
                        })
                        DropdownMenuItem(text = { Text("Count history") }, onClick = { menu = false; showLogs = true })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = Brand.Gold, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
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
                label = { Text("Search item, code, barcode, section") })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(dept == "", { vm.deptFilter.value = "" }, label = { Text("All") })
                Master.depts.forEach { d -> FilterChip(dept == d.code, { vm.deptFilter.value = d.code }, label = { Text(d.short) }) }
            }
            Text("${items.size} items · $counted counted · variance $varUnits units / PKR ${"%,.0f".format(varPkr)}",
                Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
            if (items.isEmpty()) {
                Text("No items yet. Import a GIMA RealTime export (.xlsx or .csv) from More, scan a barcode, or tap + to add one.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(items, key = { it.itemCode }) { p ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(p.itemCode) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.description, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text("${p.itemCode} · ${Master.dept(p.dept)?.short ?: "—"}" +
                                    (if (p.section.isNotBlank()) " · ${p.section}" else ""), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(p.countedQty?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall)
                                val v = p.variance
                                if (v != null) Text(
                                    if (v == 0) "matches system" else "${if (v > 0) "+" else ""}$v vs system ${p.systemStock}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (v == 0) Brand.Good else if (v < 0) Brand.Bad else Brand.Warn)
                                else if (p.systemStock != null) Text("system ${p.systemStock}", style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                            }
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
                        (l.itemCode?.let { " · $it" } ?: "") + (if (l.detail.isNotBlank()) "\n${l.detail}" else ""),
                        Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
