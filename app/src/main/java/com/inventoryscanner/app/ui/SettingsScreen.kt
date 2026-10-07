package com.inventoryscanner.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: InventoryViewModel, onBack: () -> Unit, onStore: () -> Unit) {
    val s by vm.settings.collectAsState()
    var gemini by remember { mutableStateOf(s.geminiKey) }
    var router by remember { mutableStateOf(s.openRouterKey) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, colors = brandBarColors(),
            navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = androidx.compose.ui.graphics.Color.White) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Store: ${s.store}   ·   ${s.role}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onStore) { Text("Change store / role") }
            HorizontalDivider()
            Text("Free AI keys", style = MaterialTheme.typography.titleMedium)
            Text("Paste your own free keys. The app tries them in order and falls back to the on-device model when they are busy or missing.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(gemini, { gemini = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Google Gemini API key") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(router, { router = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("OpenRouter API key (backup)") }, visualTransformation = PasswordVisualTransformation())
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                vm.updateSettings(s.copy(geminiKey = gemini, openRouterKey = router)); onBack()
            }) { Text("Save keys") }
        }
    }
}
