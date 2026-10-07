package com.inventoryscanner.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventoryscanner.app.data.Barcode
import com.inventoryscanner.app.data.Item
import com.inventoryscanner.app.data.Master

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemScreen(vm: InventoryViewModel, code: String, newBarcode: String, onBack: () -> Unit, onCount: (String) -> Unit) {
    var existing by remember { mutableStateOf<Item?>(null) }
    var itemCode by remember { mutableStateOf(code) }
    var desc by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var system by remember { mutableStateOf("") }
    var counted by remember { mutableStateOf("") }
    var barcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var addCode by remember { mutableStateOf(newBarcode) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(code, reload) {
        if (code.isNotBlank()) {
            vm.item(code)?.let {
                existing = it; desc = it.description; dept = it.dept; section = it.section; supplier = it.supplier
                price = if (it.price == 0.0) "" else it.price.toString(); system = it.systemStock?.toString() ?: ""
                counted = it.countedQty?.toString() ?: ""
            }
            barcodes = vm.barcodesOf(code)
        }
    }
    val isNew = existing == null
    val countedInt = counted.toIntOrNull()
    val systemInt = system.toIntOrNull()
    val variance = if (countedInt != null && systemInt != null) countedInt - systemInt else null

    fun build() = Item(itemCode.trim(), desc.trim(), dept, section.trim(), supplier.trim(),
        price.toDoubleOrNull() ?: 0.0, existing?.status ?: "", systemInt, countedInt)

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isNew) "New item" else "Item") }, colors = brandBarColors(),
            navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Color.White) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(itemCode, { itemCode = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Item code (GIMA)") }, enabled = isNew)
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
            Text("Department", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Master.depts.forEach { d -> FilterChip(dept == d.code, { dept = d.code }, label = { Text(d.short) }) }
            }
            OutlinedTextField(section, { section = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Section") })
            OutlinedTextField(supplier, { supplier = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Supplier") })
            OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Selling price (PKR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(system, { system = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("System stock (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { counted = ((countedInt ?: 0) - 1).coerceAtLeast(0).toString() }) { Text("−") }
                OutlinedTextField(counted, { counted = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Counted on shelf") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedButton(onClick = { counted = ((countedInt ?: 0) + 1).toString() }) { Text("+") }
            }
            if (variance != null) {
                val pkr = variance * (price.toDoubleOrNull() ?: 0.0)
                Text(if (variance == 0) "Matches the system." else
                    "Variance ${if (variance > 0) "+" else ""}$variance units" + (if (pkr != 0.0) " (PKR ${"%,.0f".format(pkr)})" else ""),
                    color = if (variance == 0) Brand.Good else if (variance < 0) Brand.Bad else Brand.Warn,
                    style = MaterialTheme.typography.titleSmall)
            }

            Text("Barcodes", style = MaterialTheme.typography.labelLarge)
            barcodes.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.barcode, Modifier.weight(1f))
                    TextButton(onClick = { vm.unlinkBarcode(b.barcode); barcodes = barcodes - b }) { Text("Remove") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(addCode, { addCode = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Add barcode") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedButton(enabled = addCode.length >= 6 && itemCode.isNotBlank() && !isNew, onClick = {
                    vm.linkBarcode(addCode, itemCode); barcodes = barcodes + Barcode(addCode, itemCode); addCode = ""
                }) { Text("Link") }
            }
            if (isNew && addCode.isNotBlank()) Text("The barcode will be linked when you save.", style = MaterialTheme.typography.bodySmall)

            Button(modifier = Modifier.fillMaxWidth(), enabled = itemCode.isNotBlank() && desc.isNotBlank(), onClick = {
                val it = build()
                vm.saveItem(it, existing?.countedQty)
                if (isNew && addCode.length >= 6) vm.linkBarcode(addCode, it.itemCode)
                onBack()
            }) { Text("Save") }
            FilledTonalButton(modifier = Modifier.fillMaxWidth(), enabled = !isNew, onClick = { onCount(itemCode) }) {
                Text(if (isNew) "Save first to count with camera" else "Count with camera")
            }
            if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete item", color = Brand.Bad) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete item?") }, text = { Text("$desc will be removed with its barcodes.") },
        confirmButton = { TextButton(onClick = { vm.deleteItem(itemCode); confirmDelete = false; onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
