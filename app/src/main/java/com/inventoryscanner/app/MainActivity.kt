package com.inventoryscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoryscanner.app.ui.CountScreen
import com.inventoryscanner.app.ui.InventoryScreen
import com.inventoryscanner.app.ui.InventoryViewModel
import com.inventoryscanner.app.ui.ProductScreen
import com.inventoryscanner.app.ui.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B5ED7))) {
                val nav = rememberNavController()
                val vm: InventoryViewModel = viewModel()
                NavHost(nav, startDestination = "inventory") {
                    composable("inventory") {
                        InventoryScreen(vm,
                            onScan = { nav.navigate("scan") },
                            onCount = { nav.navigate("count?barcode=") },
                            onOpen = { nav.navigate("product?barcode=$it") },
                            onNew = { nav.navigate("product?barcode=") })
                    }
                    composable("scan") {
                        ScanScreen(vm, onBack = { nav.popBackStack() },
                            onBarcode = { code ->
                                nav.popBackStack()
                                nav.navigate("product?barcode=$code")
                            })
                    }
                    composable(
                        "product?barcode={barcode}",
                        arguments = listOf(navArgument("barcode") { type = NavType.StringType; defaultValue = "" }),
                    ) {
                        ProductScreen(vm, it.arguments?.getString("barcode").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onCount = { code -> nav.navigate("count?barcode=$code") })
                    }
                    composable(
                        "count?barcode={barcode}",
                        arguments = listOf(navArgument("barcode") { type = NavType.StringType; defaultValue = "" }),
                    ) {
                        CountScreen(vm, it.arguments?.getString("barcode").orEmpty(),
                            onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
