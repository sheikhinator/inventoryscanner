package com.inventoryscanner.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inventoryscanner.app.data.Item

/** A scanned barcode that no item owns yet: link it once to an item code and it is remembered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkBarcodeScreen(vm: InventoryViewModel, barcode: String, onBack: () -> Unit, onLinked: (String) -> Unit, onNew: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(q) { results = if (q.isBlank()) emptyList() else vm.searchItems(q) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Unknown barcode") }, colors = brandBarColors(),
            navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Color.White) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(barcode, style = MaterialTheme.typography.headlineSmall)
            Text("This barcode is not linked to any item yet. Find the item below (by name or item code) to link it once — the app will remember it.")
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search item name or code") })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.itemCode }) { it ->
                    Card(Modifier.fillMaxWidth().clickable { vm.linkBarcode(barcode, it.itemCode); onLinked(it.itemCode) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(it.description, style = MaterialTheme.typography.titleMedium)
                            Text(it.itemCode, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onNew, Modifier.fillMaxWidth()) { Text("Create a new item with this barcode") }
        }
    }
}
