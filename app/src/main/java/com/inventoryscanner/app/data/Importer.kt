package com.inventoryscanner.app.data

import android.content.Context
import android.net.Uri
import org.dhatim.fastexcel.reader.ReadableWorkbook

data class ImportResult(val items: Int, val barcodes: Int, val badBarcodes: Int, val problem: String? = null)

/**
 * Reads a GIMA RealTime export or any simple item list (.xlsx or .csv). Column names are matched
 * loosely (NARTW1 / Item / Item code ...), the header row is found automatically, and barcodes that
 * Excel already destroyed (6.2E+11) are counted and skipped, never guessed.
 */
object Importer {
    private val COLS = mapOf(
        "itemCode" to listOf("NARTW1", "ITEMCODE", "ITEMNO", "ITEMNUMBER", "ITEM", "ITEMPR", "ITEMTO", "SKU", "CODE"),
        "description" to listOf("LARTW1", "ITMDSC", "ITEMDESCRIPTION", "DESCRIPTION", "ITEMNAME", "NAME", "DESC"),
        "dept" to listOf("CSECW1", "DEPARTMENT", "DEPT", "DEPARTMENTCODE"),
        "section" to listOf("CRAYW1", "SECTION", "SECTIONCODE"),
        "supplier" to listOf("NFOUW1", "SUPPLIER", "SUPPLIERCODE", "VENDOR"),
        "barcode" to listOf("CEANW1", "BARCODE", "EAN", "GTIN", "UPC"),
        "price" to listOf("PVTCW1", "PRICE", "SELLINGPRICE", "SELPPR", "RETAIL"),
        "stock" to listOf("QPHYW1", "PHQTPR", "PHQTTO", "SYSTEMSTOCK", "STOCK", "STOCKQTY", "ONHAND"),
        "status" to listOf("CARRW1", "STATUS", "ITEMSTATUS"),
    )

    private fun norm(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

    suspend fun import(ctx: Context, uri: Uri, name: String, dao: InventoryDao): ImportResult {
        val rows: List<List<String>> = try {
            if (name.lowercase().endsWith(".xlsx") || name.lowercase().endsWith(".xlsm")) readXlsx(ctx, uri) else readCsv(ctx, uri)
        } catch (e: Exception) {
            return ImportResult(0, 0, 0, "Could not read the file: ${e.message}")
        }
        // header = first row (of the first 30) that matches the most known column names
        var headerIdx = -1; var best = 0
        for (i in 0 until minOf(rows.size, 30)) {
            val n = rows[i].map(::norm)
            val score = COLS.values.count { names -> n.any { it in names } }
            if (score > best) { best = score; headerIdx = i }
        }
        if (headerIdx < 0 || best < 2) return ImportResult(0, 0, 0, "No item code / description columns found. Expected headers like Item, Description, Barcode.")
        val header = rows[headerIdx].map(::norm)
        fun col(key: String) = COLS.getValue(key).firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val ci = COLS.keys.associateWith(::col)
        if (ci.getValue("itemCode") < 0) return ImportResult(0, 0, 0, "No item code column found.")

        val existing = dao.all().associateBy { it.itemCode }
        val items = LinkedHashMap<String, Item>()
        val codes = ArrayList<Barcode>(); var bad = 0
        for (r in rows.drop(headerIdx + 1)) {
            fun v(key: String) = ci.getValue(key).let { if (it in r.indices) r[it].trim() else "" }
            val code = clean(v("itemCode")); if (code.isEmpty()) continue
            val old = existing[code]
            val stockRaw = v("stock")
            items[code] = Item(
                itemCode = code,
                description = v("description").ifEmpty { old?.description ?: code },
                dept = Master.deptCode(v("dept")).ifEmpty { old?.dept ?: "" },
                section = v("section").ifEmpty { old?.section ?: "" },
                supplier = v("supplier").ifEmpty { old?.supplier ?: "" },
                price = v("price").replace(",", "").toDoubleOrNull() ?: old?.price ?: 0.0,
                status = v("status").ifEmpty { old?.status ?: "" },
                systemStock = stockRaw.replace(",", "").toDoubleOrNull()?.toInt() ?: old?.systemStock,
                countedQty = old?.countedQty,
            )
            val bc = v("barcode")
            if (bc.isNotEmpty()) {
                val c = clean(bc)
                if (c.any { !it.isDigit() } || c.length < 6) bad++ else codes.add(Barcode(c, code))
            }
        }
        dao.upsertItems(items.values.toList())
        dao.upsertBarcodes(codes.distinctBy { it.barcode })
        return ImportResult(items.size, codes.distinctBy { it.barcode }.size, bad)
    }

    /** "207227.0" -> "207227"; scientific notation is left as is (caller rejects it for barcodes). */
    private fun clean(s: String): String {
        val t = s.trim()
        return if (Regex("^\\d+\\.0+$").matches(t)) t.substringBefore('.') else t
    }

    private fun readXlsx(ctx: Context, uri: Uri): List<List<String>> {
        val input = ctx.contentResolver.openInputStream(uri) ?: error("cannot open file")
        input.use { ReadableWorkbook(it).use { wb ->
            // use the sheet with the most rows (reports often have a small summary sheet first)
            val best = wb.sheets.toList().map { sh -> sh to sh.read() }.maxByOrNull { it.second.size } ?: return emptyList()
            return best.second.map { row -> (0 until row.cellCount).map { c -> row.getCellText(c) ?: "" } }
        } }
    }

    private fun readCsv(ctx: Context, uri: Uri): List<List<String>> {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return emptyList()
        val delim = listOf(',', ';', '\t', '|').maxByOrNull { d -> text.lineSequence().take(5).sumOf { l -> l.count { it == d } } } ?: ','
        return text.lineSequence().filter { it.isNotBlank() }.map { splitLine(it, delim) }.toList()
    }

    private fun splitLine(line: String, d: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == d && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString()); return out
    }
}
