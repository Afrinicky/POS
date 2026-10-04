package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleRecordDao
import com.example.data.dao.StockTransactionDao
import com.example.data.model.Product
import com.example.data.model.SaleRecord
import com.example.data.model.StockTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Product::class, StockTransaction::class, SaleRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun saleRecordDao(): SaleRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ghana_pos_inventory.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateGhanaInitialData(database.productDao(), database.stockTransactionDao())
                }
            }
        }

        suspend fun populateGhanaInitialData(productDao: ProductDao, transactionDao: StockTransactionDao) {
            val initialProducts = listOf(
                // Provisions (Staple Ghanaian neighborhood store items)
                Product("PRO-001", "Nestle Milo Refill Pack 400g", "Provisions", 45.00, 36.00, 25, 8, "200000100011", "tin", null),
                Product("PRO-002", "Ideal Evaporated Milk 160g", "Provisions", 12.50, 9.80, 48, 12, "200000100028", "tin", null),
                Product("PRO-003", "Geisha Mackerel in Tomato Sauce 425g", "Provisions", 22.00, 17.50, 30, 8, "200000100035", "can", null),
                Product("PRO-004", "Key Soap Bar (Original Brown)", "Provisions", 18.00, 14.00, 20, 6, "200000100042", "bar", null),
                Product("PRO-005", "Pepsodent Cavity Fighter 140g", "Provisions", 16.00, 12.00, 18, 5, "200000100059", "tube", null),
                Product("PRO-006", "St. Louis Sugar Cubes 500g", "Provisions", 25.00, 19.50, 4, 8, "200000100066", "box", null), // Low stock
                Product("PRO-007", "Cerelac Maize with Milk 400g", "Provisions", 55.00, 44.00, 12, 4, "200000100073", "tin", null),

                // Pharmaceuticals (Licensed OTC / Pharmacy essentials in Ghana)
                Product("PHA-101", "Paracetamol 500mg (Blister 10s)", "Pharmaceuticals", 5.00, 2.50, 50, 15, "200000200018", "blister", "2027-12-31"),
                Product("PHA-102", "Coartem 20/120mg (Artemether)", "Pharmaceuticals", 38.00, 28.00, 8, 5, "200000200025", "box", "2027-08-30"),
                Product("PHA-103", "Oral Rehydration Salts (ORS Sachet)", "Pharmaceuticals", 3.50, 1.80, 40, 10, "200000200032", "sachet", "2028-01-31"),
                Product("PHA-104", "Methylated Spirit 70% 200ml", "Pharmaceuticals", 15.00, 9.50, 2, 6, "200000200049", "btl", "2028-06-30"), // Low stock
                Product("PHA-105", "Dettol Antiseptic Liquid 250ml", "Pharmaceuticals", 32.00, 24.00, 14, 5, "200000200056", "btl", "2028-04-30"),
                Product("PHA-106", "Lonart DS Malaria Suspension 60ml", "Pharmaceuticals", 42.00, 32.00, 3, 5, "200000200063", "btl", "2027-05-31"), // Low stock
                Product("PHA-107", "Vitamin C 1000mg Effervescent (10s)", "Pharmaceuticals", 28.00, 19.00, 16, 5, "200000200070", "tube", "2027-10-31"),

                // Groceries (Ghanaian foodstuffs)
                Product("GRO-201", "Ghana Jasmine Perfume Rice 5kg", "Groceries", 115.00, 92.00, 15, 5, "200000300015", "bag", null),
                Product("GRO-202", "Frytol Pure Cooking Oil 1L", "Groceries", 42.00, 33.00, 24, 6, "200000300022", "btl", null),
                Product("GRO-203", "Gari Premium White (Olorka Bowl)", "Groceries", 20.00, 14.00, 18, 5, "200000300039", "bowl", null),

                // Cosmetics
                Product("COS-301", "Raw Organic Shea Butter 500g", "Cosmetics", 35.00, 18.00, 25, 6, "200000400012", "jar", null),
                Product("COS-302", "Cocoa Butter Body Lotion 400ml", "Cosmetics", 48.00, 34.00, 16, 5, "200000400029", "btl", null),
                Product("COS-303", "Dudu-Osun African Black Soap", "Cosmetics", 14.00, 9.00, 30, 8, "200000400036", "bar", null),
                Product("COS-304", "Sunscreen SPF 50+ Invisible 50ml", "Cosmetics", 95.00, 65.00, 3, 5, "200000400043", "tube", "2027-11-30") // Low stock
            )

            productDao.insertAll(initialProducts)

            for (p in initialProducts) {
                transactionDao.insertTransaction(
                    StockTransaction(
                        sku = p.sku,
                        productName = p.name,
                        delta = p.stockLevel,
                        previousStock = 0,
                        newStock = p.stockLevel,
                        reason = "INITIAL",
                        notes = "Initial Ghanaian store inventory setup"
                    )
                )
            }
        }
    }
}
