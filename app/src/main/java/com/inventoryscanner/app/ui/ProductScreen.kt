package com.inventoryscanner.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventoryscanner.app.data.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(vm: InventoryViewModel, initialBarcode: String, onBack: () -> Unit, onCount: (String) -> Unit) {
    var existing by remember { mutableStateOf<Product?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf(initialBarcode) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("0") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(initialBarcode) {
        if (initialBarcode.isNotBlank()) vm.find(initialBarcode)?.let {
            existing = it; name = it.name; category = it.category; location = it.location; qty = it.quantity.toString()
        }
        loaded = true
    }
    val isNew = existing == null
    val qtyInt = qty.toIntOrNull() ?: 0

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isNew) "New product" else "Edit product") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loaded && isNew && initialBarcode.isNotBlank()) {
                Text("New barcode — fill in the product details.", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(barcode, { barcode = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Barcode") }, enabled = isNew)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
            OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Category") })
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Location (aisle / shelf)") })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { qty = (qtyInt - 1).coerceAtLeast(0).toString() }) { Text("−") }
                OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedButton(onClick = { qty = (qtyInt + 1).toString() }) { Text("+") }
            }
            Button(Modifier.fillMaxWidth(), enabled = barcode.isNotBlank() && name.isNotBlank(), onClick = {
                vm.save(Product(barcode, name.trim(), category.trim(), location.trim(), qtyInt),
                    source = "manual", previousQty = existing?.quantity ?: 0)
                onBack()
            }) { Text("Save") }
            if (barcode.isNotBlank()) {
                FilledTonalButton(Modifier.fillMaxWidth(), onClick = { onCount(barcode) }, enabled = !isNew) {
                    Text(if (isNew) "Save first to count with camera" else "Count with camera")
                }
            }
            if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete product") }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete product?") },
        text = { Text("$name will be removed from inventory.") },
        confirmButton = { TextButton(onClick = { vm.delete(barcode); confirmDelete = false; onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
