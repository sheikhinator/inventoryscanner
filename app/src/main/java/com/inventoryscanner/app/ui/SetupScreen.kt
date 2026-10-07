package com.inventoryscanner.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inventoryscanner.app.data.Master

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: InventoryViewModel, onDone: () -> Unit, firstRun: Boolean) {
    val s by vm.settings.collectAsState()
    var store by remember { mutableStateOf(s.store) }
    var role by remember { mutableStateOf(s.role) }
    var dept by remember { mutableStateOf(s.dept) }

    Scaffold(topBar = { TopAppBar(title = { Text(if (firstRun) "Set up" else "Store & role") }, colors = brandBarColors()) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Store (GIMA code)", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Master.stores.forEach { st ->
                    FilterChip(store == st.code, { store = st.code }, label = { Text(st.label) })
                }
            }
            Text("Your role", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Master.roles.forEach { r -> FilterChip(role == r, { role = r }, label = { Text(r) }) }
            }
            Text("Department focus", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(dept == "", { dept = "" }, label = { Text("All") })
                Master.depts.forEach { d -> FilterChip(dept == d.code, { dept = d.code }, label = { Text("${d.short} · ${d.name}") }) }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = store.isNotEmpty() && role.isNotEmpty(), onClick = {
                vm.updateSettings(s.copy(store = store, role = role, dept = dept))
                vm.deptFilter.value = dept
                onDone()
            }) { Text(if (firstRun) "Continue" else "Save") }
        }
    }
}
