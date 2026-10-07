package com.inventoryscanner.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Identity = item code (never the barcode: Excel destroys barcodes). */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val itemCode: String,
    val description: String,
    val dept: String = "",
    val section: String = "",
    val supplier: String = "",
    val price: Double = 0.0,
    val status: String = "",
    val systemStock: Int? = null,
    val countedQty: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** counted - system, when both are known. */
    val variance get() = if (countedQty != null && systemStock != null) countedQty - systemStock else null
}

@Entity(tableName = "barcodes", indices = [Index("itemCode")])
data class Barcode(@PrimaryKey val barcode: String, val itemCode: String)

@Entity(tableName = "count_logs")
data class CountLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemCode: String?,
    val source: String,   // manual | snapshot | belt | video | import
    val count: Int,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface InventoryDao {
    @Query(
        "SELECT * FROM items WHERE (:dept = '' OR dept = :dept) AND (:q = '' OR description LIKE '%' || :q || '%' " +
            "OR itemCode LIKE '%' || :q || '%' OR section LIKE '%' || :q || '%' OR supplier LIKE '%' || :q || '%' " +
            "OR itemCode IN (SELECT itemCode FROM barcodes WHERE barcode LIKE '%' || :q || '%')) " +
            "ORDER BY description COLLATE NOCASE LIMIT 2000"
    )
    fun search(q: String, dept: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE description LIKE '%' || :q || '%' OR itemCode LIKE '%' || :q || '%' ORDER BY description COLLATE NOCASE LIMIT 30")
    suspend fun searchOnce(q: String): List<Item>

    @Query("SELECT * FROM items ORDER BY itemCode")
    suspend fun all(): List<Item>

    @Query("SELECT * FROM items WHERE itemCode = :code")
    suspend fun item(code: String): Item?

    @Query("SELECT i.* FROM items i JOIN barcodes b ON b.itemCode = i.itemCode WHERE b.barcode = :barcode LIMIT 1")
    suspend fun itemByBarcode(barcode: String): Item?

    @Query("SELECT * FROM barcodes WHERE itemCode = :code ORDER BY barcode")
    suspend fun barcodesOf(code: String): List<Barcode>

    @Query("SELECT * FROM barcodes")
    suspend fun allBarcodes(): List<Barcode>

    @Upsert suspend fun upsert(i: Item)
    @Upsert suspend fun upsertItems(list: List<Item>)
    @Upsert suspend fun upsertBarcode(b: Barcode)
    @Upsert suspend fun upsertBarcodes(list: List<Barcode>)
    @Query("DELETE FROM barcodes WHERE barcode = :barcode") suspend fun deleteBarcode(barcode: String)
    @Query("DELETE FROM items WHERE itemCode = :code") suspend fun deleteItem(code: String)
    @Query("DELETE FROM barcodes WHERE itemCode = :code") suspend fun deleteBarcodesOf(code: String)
    @Query("UPDATE items SET countedQty = :qty, updatedAt = :at WHERE itemCode = :code")
    suspend fun setCounted(code: String, qty: Int, at: Long)
    @Insert suspend fun log(l: CountLog)

    @Query("SELECT * FROM count_logs ORDER BY createdAt DESC LIMIT 200")
    fun recentLogs(): Flow<List<CountLog>>

    @Transaction
    suspend fun deleteItemFully(code: String) { deleteBarcodesOf(code); deleteItem(code) }
}

@Database(entities = [Item::class, Barcode::class, CountLog::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): InventoryDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "inventory.db")
                .fallbackToDestructiveMigration() // v1 was never released
                .build().also { inst = it }
        }
    }
}
