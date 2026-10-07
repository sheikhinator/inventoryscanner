package com.inventoryscanner.app.ui

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(vm: InventoryViewModel, onBack: () -> Unit, onBarcode: (String) -> Unit) {
    CameraPermissionGate(onDenied = onBack) {
        val executor = remember { Executors.newSingleThreadExecutor() }
        val scanner = remember { BarcodeScanning.getClient() }
        val fired = remember { AtomicBoolean(false) }
        var manual by remember { mutableStateOf("") }
        DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

        Box(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxSize()) {
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(CAMERA_RESOLUTION)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || fired.get()) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val v = codes.firstNotNullOfOrNull { it.rawValue }
                            if (v != null && fired.compareAndSet(false, true)) onBarcode(v)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                listOf(analysis)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                    Text("Point the camera at a barcode", Modifier.padding(12.dp))
                }
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(manual, { manual = it }, Modifier.weight(1f), singleLine = true,
                            label = { Text("Or type barcode") })
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = manual.isNotBlank(), onClick = { if (fired.compareAndSet(false, true)) onBarcode(manual.trim()) }) { Text("Go") }
                    }
                }
                OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
