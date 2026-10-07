package com.inventoryscanner.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inventoryscanner.app.InventoryApp
import com.inventoryscanner.app.data.CountLog
import com.inventoryscanner.app.data.Product
import com.inventoryscanner.app.ml.Detector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as InventoryApp).db.dao()

    val query = MutableStateFlow("")
    val products = query.flatMapLatest { dao.search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = dao.recentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Loaded lazily the first time an AI screen opens (model load takes a moment). */
    @Volatile private var detectorInstance: Detector? = null
    suspend fun detector(): Detector = withContext(Dispatchers.Default) {
        detectorInstance ?: synchronized(this@InventoryViewModel) {
            detectorInstance ?: Detector(getApplication()).also { detectorInstance = it }
        }
    }

    suspend fun find(barcode: String): Product? = dao.get(barcode)

    fun save(p: Product, source: String = "manual", previousQty: Int? = null) = viewModelScope.launch {
        dao.upsert(p.copy(updatedAt = System.currentTimeMillis()))
        if (previousQty != null && previousQty != p.quantity) {
            dao.log(CountLog(barcode = p.barcode, source = source, count = p.quantity,
                detail = "qty $previousQty -> ${p.quantity}"))
        }
    }

    fun delete(barcode: String) = viewModelScope.launch { dao.delete(barcode) }

    /** Applies an AI count to a product (sets its quantity) and logs it. */
    fun applyCount(barcode: String, count: Int, source: String, detail: String) = viewModelScope.launch {
        val p = dao.get(barcode) ?: return@launch
        dao.upsert(p.copy(quantity = count, updatedAt = System.currentTimeMillis()))
        dao.log(CountLog(barcode = barcode, source = source, count = count, detail = detail))
    }

    fun logCount(source: String, count: Int, detail: String) = viewModelScope.launch {
        dao.log(CountLog(barcode = null, source = source, count = count, detail = detail))
    }

    /** Writes inventory.csv to the cache dir and returns a share Intent. */
    suspend fun exportCsv(ctx: Context): Intent = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val f = File(dir, "inventory_$stamp.csv")
        f.bufferedWriter().use { w ->
            w.write("barcode,name,category,location,quantity,updated_at\n")
            for (p in dao.all()) {
                w.write(listOf(p.barcode, p.name, p.category, p.location, p.quantity.toString(),
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(p.updatedAt)))
                    .joinToString(",") { csv(it) } + "\n")
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Imports "barcode,name,category,location,quantity" rows (header optional). Returns rows imported. */
    suspend fun importCsv(ctx: Context, uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            for (line in lines) {
                val c = parseCsvLine(line)
                if (c.size < 2 || c[0].equals("barcode", true) || c[0].isBlank()) continue
                dao.upsert(Product(c[0].trim(), c[1].trim(), c.getOrElse(2) { "" }.trim(),
                    c.getOrElse(3) { "" }.trim(), c.getOrElse(4) { "0" }.trim().toIntOrNull() ?: 0))
                n++
            }
        }
        n
    }

    private fun csv(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString()); return out
    }

    override fun onCleared() { detectorInstance?.close() }
}
