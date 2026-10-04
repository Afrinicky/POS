package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.random.Random

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["category"])
    ]
)
data class Product(
    @PrimaryKey
    val sku: String,
    val name: String,
    val category: String, // "Provisions", "Pharmaceuticals", "Groceries", "Cosmetics", "General"
    val price: Double, // In Ghanaian Cedis (GH₵)
    val costPrice: Double = 0.0,
    val stockLevel: Int = 0,
    val reorderThreshold: Int = 5,
    val barcode: String? = null,
    val unit: String = "pcs", // "tin", "can", "blister", "sachet", "box", "btl", "bar", "kg", "bag"
    val expiryDate: String? = null, // Essential for Pharmaceuticals & Provisions in Ghana
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = stockLevel <= reorderThreshold

    val isOutOfStock: Boolean
        get() = stockLevel <= 0

    val grossMarginPct: Double
        get() = if (price > 0.0) ((price - costPrice) / price) * 100.0 else 0.0

    companion object {
        fun generateBarcode(sku: String): String {
            val prefix = "200" // In-store standard retail prefix
            val hash = Math.abs(sku.hashCode() % 100000000).toString().padStart(8, '0')
            val base = "$prefix$hash"
            var sum = 0
            for (i in base.indices) {
                val d = base[i].digitToInt()
                sum += if (i % 2 == 1) d * 3 else d
            }
            val checkDigit = (10 - (sum % 10)) % 10
            return "$base$checkDigit"
        }
    }
}
