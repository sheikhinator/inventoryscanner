package com.inventoryscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoryscanner.app.ui.*
import kotlinx.coroutines.launch
import android.net.Uri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                val vm: InventoryViewModel = viewModel()
                val scope = rememberCoroutineScope()
                val settings by vm.settings.collectAsState()
                val start = if (settings.store.isEmpty() || settings.role.isEmpty()) "setup" else "inventory"
                val str = { name: String -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }

                NavHost(nav, startDestination = start) {
                    composable("setup") {
                        SetupScreen(vm, firstRun = start == "setup", onDone = {
                            if (!nav.popBackStack()) nav.navigate("inventory") { popUpTo("setup") { inclusive = true } }
                        })
                    }
                    composable("inventory") {
                        InventoryScreen(vm,
                            onScan = { nav.navigate("scan") },
                            onCount = { nav.navigate("count?code=") },
                            onOpen = { nav.navigate("item?code=${Uri.encode(it)}") },
                            onNew = { nav.navigate("item?code=") },
                            onSettings = { nav.navigate("settings") })
                    }
                    composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onStore = { nav.navigate("setup") }) }
                    composable("scan") {
                        ScanScreen(vm, onBack = { nav.popBackStack() }, onBarcode = { bc ->
                            scope.launch {
                                val found = vm.itemByBarcode(bc)
                                nav.popBackStack()
                                if (found != null) nav.navigate("item?code=${Uri.encode(found.itemCode)}")
                                else nav.navigate("link?barcode=${Uri.encode(bc)}")
                            }
                        })
                    }
                    composable("link?barcode={barcode}", arguments = listOf(str("barcode"))) {
                        val bc = it.arguments?.getString("barcode").orEmpty()
                        LinkBarcodeScreen(vm, bc, onBack = { nav.popBackStack() },
                            onLinked = { code -> nav.popBackStack(); nav.navigate("item?code=${Uri.encode(code)}") },
                            onNew = { nav.popBackStack(); nav.navigate("item?code=&barcode=${Uri.encode(bc)}") })
                    }
                    composable("item?code={code}&barcode={barcode}", arguments = listOf(str("code"), str("barcode"))) {
                        ItemScreen(vm, it.arguments?.getString("code").orEmpty(), it.arguments?.getString("barcode").orEmpty(),
                            onBack = { nav.popBackStack() }, onCount = { c -> nav.navigate("count?code=${Uri.encode(c)}") })
                    }
                    composable("count?code={code}", arguments = listOf(str("code"))) {
                        CountScreen(vm, it.arguments?.getString("code").orEmpty(), onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
