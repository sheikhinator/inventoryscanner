package com.inventoryscanner.app.data

/** Master data taken from Stock Compass (stores keyed by GIMA code, departments 01-05). */
data class StoreInfo(val code: String, val name: String, val format: String) {
    val label get() = "$code · $name"
}

data class Dept(val code: String, val short: String, val name: String, val group: String)

object Master {
    val stores = listOf(
        StoreInfo("500", "Fortress", "H"), StoreInfo("502", "WTC Islamabad", "H"),
        StoreInfo("503", "Emporium Mall", "H"), StoreInfo("504", "Packages Mall", "H"),
        StoreInfo("505", "Lucky One", "H"), StoreInfo("506", "Lyallpur Galleria", "H"),
        StoreInfo("P03", "Gujranwala", "H"), StoreInfo("P06", "D-12 Islamabad", "S"),
        StoreInfo("P07", "DHA Rahbar", "S"), StoreInfo("P08", "DHA Phase 7", "S"),
        StoreInfo("P09", "Askari 10", "S"), StoreInfo("PA6", "Paragon City", "S"),
        StoreInfo("P04", "Fortress Myli", "M"), StoreInfo("P05", "Packages Myli", "M"),
        StoreInfo("PA2", "Emporium Myli", "M"), StoreInfo("PA4", "Lucky One Myli", "M"),
        StoreInfo("PD4", "DHA Phase 7 Myli", "M"), StoreInfo("PD2", "DHA Rahbar Myli", "M"),
    )

    val depts = listOf(
        Dept("01", "CG", "Consumer Goods", "CG"),
        Dept("02", "FFD", "Fresh Food", "FFD"),
        Dept("03", "LHH", "Light Household", "NF"),
        Dept("04", "HHH", "Heavy Household", "NF"),
        Dept("05", "TXT", "Textile", "NF"),
    )

    val roles = listOf("Section manager", "Department head", "Store manager", "District / head office")

    fun dept(code: String) = depts.firstOrNull { it.code == code }

    private val deptAliases = mapOf(
        "CG" to "01", "CGD" to "01", "FMCG" to "01", "GROCERY" to "01", "CONSUMERGOODS" to "01",
        "FFD" to "02", "FRESH" to "02", "FRESHFOOD" to "02", "FRS" to "02",
        "LHH" to "03", "LIGHTHOUSEHOLD" to "03", "HHH" to "04", "HEAVYHOUSEHOLD" to "04",
        "TXT" to "05", "TEXTILE" to "05", "TEX" to "05",
    )

    /** "1", "01", "01-CGD", "CG", "Fresh Food" -> "01"/"02"/...; unknown -> "". */
    fun deptCode(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        Regex("^0?([1-5])(\\D|$)").find(s)?.let { return "0" + it.groupValues[1] }
        return deptAliases[s.uppercase().filter { it.isLetter() }] ?: ""
    }
}
