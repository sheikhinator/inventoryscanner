package com.inventoryscanner.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String = "",
    val location: String = "",
    val quantity: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "count_logs")
data class CountLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val source: String,   // "snapshot" | "belt" | "manual" | "scan"
    val count: Int,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface InventoryDao {
    @Query("SELECT * FROM products WHERE name LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE")
    fun search(q: String): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Product>

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun get(barcode: String): Product?

    @Upsert suspend fun upsert(p: Product)
    @Query("DELETE FROM products WHERE barcode = :barcode") suspend fun delete(barcode: String)
    @Insert suspend fun log(l: CountLog)

    @Query("SELECT * FROM count_logs ORDER BY createdAt DESC LIMIT 200")
    fun recentLogs(): Flow<List<CountLog>>
}

@Database(entities = [Product::class, CountLog::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): InventoryDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "inventory.db")
                .build().also { inst = it }
        }
    }
}
