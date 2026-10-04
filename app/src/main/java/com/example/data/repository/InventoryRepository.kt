package com.example.data.repository

import com.example.data.dao.ProductDao
import com.example.data.dao.SaleRecordDao
import com.example.data.dao.StockTransactionDao
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.model.SaleRecord
import com.example.data.model.StockTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class InventoryRepository(
    private val productDao: ProductDao,
    private val stockTransactionDao: StockTransactionDao,
    private val saleRecordDao: SaleRecordDao
) {
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = productDao.getLowStockProducts()
    val allCategories: Flow<List<String>> = productDao.getAllCategories()
    val allSales: Flow<List<SaleRecord>> = saleRecordDao.getAllSales()
    val allTransactions: Flow<List<StockTransaction>> = stockTransactionDao.getAllTransactions()
    val totalRevenue: Flow<Double?> = saleRecordDao.getTotalRevenue()
    val totalSaleCount: Flow<Int> = saleRecordDao.getTotalSaleCount()

    fun getProductsByCategory(category: String): Flow<List<Product>> {
        return if (category.equals("All", ignoreCase = true)) {
            productDao.getAllProducts()
        } else {
            productDao.getProductsByCategory(category)
        }
    }

    suspend fun findProductByBarcode(barcode: String): Product? = withContext(Dispatchers.IO) {
        productDao.findProductByBarcode(barcode.trim())
    }

    suspend fun getProductBySku(sku: String): Product? = withContext(Dispatchers.IO) {
        productDao.getProductBySku(sku.trim())
    }

    suspend fun saveProduct(product: Product) = withContext(Dispatchers.IO) {
        val existing = productDao.getProductBySku(product.sku)
        productDao.insertProduct(product)
        if (existing == null) {
            stockTransactionDao.insertTransaction(
                StockTransaction(
                    sku = product.sku,
                    productName = product.name,
                    delta = product.stockLevel,
                    previousStock = 0,
                    newStock = product.stockLevel,
                    reason = "INITIAL",
                    notes = "New product added"
                )
            )
        }
    }

    suspend fun adjustStock(
        sku: String,
        delta: Int,
        reason: String,
        notes: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        val product = productDao.getProductBySku(sku) ?: return@withContext Result.failure(Exception("Product not found"))
        val currentStock = product.stockLevel
        val newStock = currentStock + delta
        if (newStock < 0) {
            return@withContext Result.failure(Exception("Insufficient stock. Cannot reduce below 0."))
        }

        productDao.updateStock(sku, newStock)
        stockTransactionDao.insertTransaction(
            StockTransaction(
                sku = sku,
                productName = product.name,
                delta = delta,
                previousStock = currentStock,
                newStock = newStock,
                reason = reason,
                notes = notes
            )
        )
        Result.success(newStock)
    }

    suspend fun completeCheckout(
        items: List<CartItem>,
        subtotal: Double,
        tax: Double,
        discount: Double,
        totalAmount: Double,
        paymentMethod: String,
        momoNetwork: String? = null,
        customerPhone: String? = null,
        momoReference: String? = null
    ): Result<SaleRecord> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(Exception("Cart is empty"))
        }

        // Verify stock for all items
        for (item in items) {
            val p = productDao.getProductBySku(item.product.sku)
                ?: return@withContext Result.failure(Exception("Item ${item.product.name} not found"))
            if (p.stockLevel < item.quantity) {
                return@withContext Result.failure(
                    Exception("Not enough stock for ${p.name}. Available: ${p.stockLevel}, Requested: ${item.quantity}")
                )
            }
        }

        val saleId = "SALE-" + UUID.randomUUID().toString().take(8).uppercase()
        val jsonArray = JSONArray()

        // Atomically decrement stock
        for (item in items) {
            val current = productDao.getProductBySku(item.product.sku)!!
            val newStock = current.stockLevel - item.quantity
            productDao.updateStock(item.product.sku, newStock)

            stockTransactionDao.insertTransaction(
                StockTransaction(
                    sku = item.product.sku,
                    productName = item.product.name,
                    delta = -item.quantity,
                    previousStock = current.stockLevel,
                    newStock = newStock,
                    reason = "SALE",
                    notes = "Checkout sale #$saleId"
                )
            )

            val itemObj = JSONObject().apply {
                put("sku", item.product.sku)
                put("name", item.product.name)
                put("price", item.product.price)
                put("costPrice", item.product.costPrice)
                put("quantity", item.quantity)
                put("total", item.lineTotal)
            }
            jsonArray.put(itemObj)
        }

        val saleRecord = SaleRecord(
            saleId = saleId,
            itemsJson = jsonArray.toString(),
            itemCount = items.sumOf { it.quantity },
            subtotal = subtotal,
            tax = tax,
            discount = discount,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            momoNetwork = momoNetwork,
            customerPhone = customerPhone,
            momoReference = momoReference
        )

        saleRecordDao.insertSale(saleRecord)
        Result.success(saleRecord)
    }
}
