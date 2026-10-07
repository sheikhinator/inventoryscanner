package com.inventoryscanner.app.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inventoryscanner.app.data.Product
import com.inventoryscanner.app.ml.CentroidTracker
import com.inventoryscanner.app.ml.Detection
import com.inventoryscanner.app.ml.Detector
import com.inventoryscanner.app.ml.LineCounter
import com.inventoryscanner.app.ml.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max

private val CLASS_COLORS = listOf(Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFF1565C0), Color(0xFFEF6C00), Color(0xFF6A1B9A))
private fun classColor(i: Int) = CLASS_COLORS[i % CLASS_COLORS.size]

/** Upright (rotated) bitmap from an ImageProxy, optionally downscaled so the long side is <= maxSide. */
private fun ImageProxy.uprightBitmap(maxSide: Int): Bitmap {
    val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val s = maxSide.toFloat() / max(width, height)
    if (s < 1f) m.postScale(s, s)
    val src = toBitmap()
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountScreen(vm: InventoryViewModel, barcode: String, onBack: () -> Unit) {
    var detector by remember { mutableStateOf<Detector?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<Product?>(null) }
    LaunchedEffect(Unit) {
        runCatching { detector = vm.detector() }.onFailure { loadError = it.message ?: "Could not load model" }
    }
    LaunchedEffect(barcode) { if (barcode.isNotBlank()) target = vm.find(barcode) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(target?.let { "Count: ${it.name}" } ?: "AI count") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Shelf / pile photo") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Live belt") })
            }
            val det = detector
            when {
                loadError != null -> Text("Model error: $loadError", Modifier.padding(16.dp))
                det == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> CameraPermissionGate(onDenied = onBack) {
                    if (tab == 0) SnapshotCount(vm, det, target, onDone = onBack)
                    else BeltCount(vm, det, target, onDone = onBack)
                }
            }
        }
    }
}

/** Shared "apply the result" UI: choose a product (if none preselected) and set/add the quantity. */
@Composable
private fun ApplyBar(vm: InventoryViewModel, target: Product?, count: Int, source: String, detail: String,
                     addMode: Boolean, onDone: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val products by vm.products.collectAsState()
    val scope = rememberCoroutineScope()

    fun apply(p: Product) {
        val newQty = if (addMode) p.quantity + count else count
        vm.applyCount(p.barcode, newQty, source, detail); onDone()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { if (target != null) apply(target) else picking = true }, Modifier.weight(1f)) {
            Text(if (addMode) "Add $count to stock" else "Set stock to $count")
        }
        OutlinedButton(onClick = { vm.logCount(source, count, detail); onDone() }) { Text("Log only") }
    }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        title = { Text("Which product?") },
        text = {
            if (products.isEmpty()) Text("Add a product first.")
            LazyColumn {
                items(products, key = { it.barcode }) { p ->
                    TextButton(onClick = { picking = false; scope.launch { apply(p) } }, Modifier.fillMaxWidth()) {
                        Text("${p.name} (${p.quantity})", Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

@Composable
private fun DetectionOverlay(frameW: Int, frameH: Int, dets: List<Detection>, modifier: Modifier = Modifier,
                             line: FloatArray? = null) {
    Canvas(modifier) {
        val sx = size.width / frameW; val sy = size.height / frameH
        for (d in dets) drawRect(classColor(d.cls), Offset(d.box.left * sx, d.box.top * sy),
            Size(d.box.width() * sx, d.box.height() * sy), style = Stroke(2.5f))
        if (line != null) drawLine(Color.Red, Offset(line[0] * size.width, line[1] * size.height),
            Offset(line[2] * size.width, line[3] * size.height), strokeWidth = 5f)
    }
}

// ---------------------------------------------------------------- snapshot (shelf / pile)

@Composable
private fun SnapshotCount(vm: InventoryViewModel, detector: Detector, target: Product?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = remember { ImageCapture.Builder().setResolutionSelector(CAMERA_RESOLUTION)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var raw by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conf by remember { mutableFloatStateOf(0.35f) }
    var adjust by remember { mutableIntStateOf(0) }

    fun shoot() {
        busy = true; error = null
        capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val bmp = image.uprightBitmap(1920)
                            image.close()
                            bmp to detector.detectTiled(bmp, conf = 0.15f)
                        }
                    }.onSuccess { (bmp, dets) -> bitmap = bmp; raw = dets; adjust = 0 }
                        .onFailure { error = it.message }
                    busy = false
                }
            }
            override fun onError(e: ImageCaptureException) { error = e.message; busy = false }
        })
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxWidth().aspectRatio(FRAME_ASPECT)) { listOf(capture) }
            Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
                Button(onClick = ::shoot, enabled = !busy) { Text(if (busy) "Counting…" else "Capture & count") }
            }
        }
        return
    }

    val shown = raw.filter { it.conf >= conf }
    val perClass = detector.labels.indices.associateWith { i -> shown.count { it.cls == i } }
    val total = (shown.size + adjust).coerceAtLeast(0)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)) {
            Image(bmp.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            DetectionOverlay(bmp.width, bmp.height, shown, Modifier.matchParentSize())
        }
        Text("$total items", style = MaterialTheme.typography.headlineMedium)
        Text(perClass.entries.joinToString("  ·  ") { "${detector.labels[it.key]}: ${it.value}" })
        Text("Confidence threshold: ${"%.2f".format(conf)}")
        Slider(conf, { conf = it }, valueRange = 0.15f..0.9f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Manual correction: ${if (adjust >= 0) "+" else ""}$adjust")
            OutlinedButton(onClick = { adjust-- }) { Text("−1") }
            OutlinedButton(onClick = { adjust++ }) { Text("+1") }
        }
        ApplyBar(vm, target, total, "snapshot",
            "conf>=${"%.2f".format(conf)}, adjust=$adjust, $perClass", addMode = false, onDone = onDone)
        OutlinedButton(onClick = { bitmap = null; raw = emptyList() }, Modifier.fillMaxWidth()) { Text("Retake") }
    }
}

// ---------------------------------------------------------------- live belt (line crossing)

@Composable
private fun BeltCount(vm: InventoryViewModel, detector: Detector, target: Product?, onDone: () -> Unit) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    val tracker = remember { CentroidTracker() }
    val counter = remember { LineCounter(detector.labels.size) }
    var lineY by remember { mutableFloatStateOf(0.55f) }
    val lineYState = rememberUpdatedState(lineY)
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var frameW by remember { mutableIntStateOf(480) }
    var frameH by remember { mutableIntStateOf(640) }
    var total by remember { mutableIntStateOf(0) }
    var perClass by remember { mutableStateOf(IntArray(detector.labels.size)) }
    var running by remember { mutableStateOf(true) }
    val runningState = rememberUpdatedState(running)
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    fun lineFor(y: Float) = floatArrayOf(0f, y - 0.07f, 1f, y + 0.07f)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(frameW.toFloat() / frameH)) {
            CameraPreview(Modifier.matchParentSize()) {
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(CAMERA_RESOLUTION)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    try {
                        if (runningState.value) {
                            val bmp = proxy.uprightBitmap(640)
                            val dets = detector.detect(bmp, conf = 0.25f)
                            val t = tracker.update(dets)
                            counter.update(t, bmp.width.toFloat(), bmp.height.toFloat(), lineFor(lineYState.value))
                            frameW = bmp.width; frameH = bmp.height
                            tracks = t; total = counter.total; perClass = counter.perClass.copyOf()
                        }
                    } finally { proxy.close() }
                }
                listOf(analysis)
            }
            DetectionOverlay(frameW, frameH,
                tracks.map { Detection(it.box, it.conf, it.cls) }, Modifier.matchParentSize(), line = lineFor(lineY))
        }
        Text("$total counted", style = MaterialTheme.typography.headlineMedium)
        Text(detector.labels.indices.joinToString("  ·  ") { "${detector.labels[it]}: ${perClass[it]}" })
        Text("Counting line position")
        Slider(lineY, { lineY = it }, valueRange = 0.2f..0.8f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
            OutlinedButton(onClick = { counter.reset(); tracker.reset(); total = 0; perClass = IntArray(detector.labels.size) }) { Text("Reset") }
        }
        Text("Items are counted once, when they cross the red line (e.g. goods moving along a belt or being unloaded).",
            style = MaterialTheme.typography.bodySmall)
        if (total > 0) ApplyBar(vm, target, total, "belt", "line-crossing ${perClass.toList()}", addMode = true, onDone = onDone)
    }
}
