package com.inventoryscanner.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inventoryscanner.app.InventoryApp
import com.inventoryscanner.app.data.*
import com.inventoryscanner.app.ml.Detector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Settings(
    val store: String = "", val role: String = "", val dept: String = "",
    val geminiKey: String = "", val openRouterKey: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as InventoryApp).db.dao()
    private val sp = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val settings = MutableStateFlow(
        Settings(sp.getString("store", "") ?: "", sp.getString("role", "") ?: "", sp.getString("dept", "") ?: "",
            sp.getString("geminiKey", "") ?: "", sp.getString("openRouterKey", "") ?: ""))

    fun updateSettings(s: Settings) {
        settings.value = s
        sp.edit().putString("store", s.store).putString("role", s.role).putString("dept", s.dept)
            .putString("geminiKey", s.geminiKey.trim()).putString("openRouterKey", s.openRouterKey.trim()).apply()
    }

    val query = MutableStateFlow("")
    val deptFilter = MutableStateFlow("")
    val items = combine(query, deptFilter) { q, d -> q.trim() to d }
        .flatMapLatest { (q, d) -> dao.search(q, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = dao.recentLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Volatile private var detectorInstance: Detector? = null
    suspend fun detector(): Detector = withContext(Dispatchers.Default) {
        detectorInstance ?: synchronized(this@InventoryViewModel) {
            detectorInstance ?: Detector(getApplication()).also { detectorInstance = it }
        }
    }

    suspend fun item(code: String): Item? = dao.item(code)
    suspend fun itemByBarcode(barcode: String): Item? = dao.itemByBarcode(barcode)
    suspend fun barcodesOf(code: String): List<Barcode> = dao.barcodesOf(code)
    suspend fun searchItems(q: String): List<Item> = dao.searchOnce(q.trim())

    fun saveItem(item: Item, previousCounted: Int?) = viewModelScope.launch {
        dao.upsert(item.copy(updatedAt = System.currentTimeMillis()))
        if (item.countedQty != null && item.countedQty != previousCounted) {
            dao.log(CountLog(itemCode = item.itemCode, source = "manual", count = item.countedQty,
                detail = "counted ${previousCounted ?: "-"} -> ${item.countedQty}"))
        }
    }

    fun linkBarcode(barcode: String, itemCode: String) = viewModelScope.launch { dao.upsertBarcode(Barcode(barcode, itemCode)) }
    fun unlinkBarcode(barcode: String) = viewModelScope.launch { dao.deleteBarcode(barcode) }
    fun deleteItem(code: String) = viewModelScope.launch { dao.deleteItemFully(code) }

    /** Sets the counted quantity (or adds to it) from an AI count and logs it. */
    fun applyCount(itemCode: String, count: Int, source: String, detail: String, add: Boolean = false) = viewModelScope.launch {
        val it = dao.item(itemCode) ?: return@launch
        val q = if (add) (it.countedQty ?: 0) + count else count
        dao.setCounted(itemCode, q, System.currentTimeMillis())
        dao.log(CountLog(itemCode = itemCode, source = source, count = q, detail = detail))
    }

    fun logCount(source: String, count: Int, detail: String) = viewModelScope.launch {
        dao.log(CountLog(itemCode = null, source = source, count = count, detail = detail))
    }

    suspend fun importFile(ctx: Context, uri: Uri): ImportResult {
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "file"
        return withContext(Dispatchers.IO) { Importer.import(ctx, uri, name, dao) }
    }

    /** Writes a counts CSV to the cache dir and returns a share Intent. */
    suspend fun exportCsv(ctx: Context): Intent = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val f = File(dir, "stock_count_${settings.value.store.ifEmpty { "store" }}_$stamp.csv")
        f.bufferedWriter().use { w ->
            w.write("store,item_code,description,department,section,supplier,price_pkr,system_stock,counted,variance_units,variance_pkr,barcodes,updated_at\n")
            val codes = dao.allBarcodes().groupBy { it.itemCode }
            for (i in dao.all()) {
                val v = i.variance
                w.write(listOf(settings.value.store, i.itemCode, i.description, Master.dept(i.dept)?.short ?: i.dept, i.section,
                    i.supplier, "%.2f".format(i.price), i.systemStock?.toString() ?: "", i.countedQty?.toString() ?: "",
                    v?.toString() ?: "", v?.let { "%.2f".format(it * i.price) } ?: "",
                    codes[i.itemCode].orEmpty().joinToString(" ") { it.barcode },
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(i.updatedAt)))
                    .joinToString(",") { csv(it) } + "\n")
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun csv(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    override fun onCleared() { detectorInstance?.close() }
}
