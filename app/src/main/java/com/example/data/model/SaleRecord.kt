package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleRecord(
    @PrimaryKey
    val saleId: String,
    val itemsJson: String,
    val itemCount: Int,
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val totalAmount: Double,
    val paymentMethod: String, // "MTN MoMo", "Telecel Cash", "AT Money", "Cash", "Store Credit", "Card"
    val momoNetwork: String? = null, // "MTN", "Telecel", "AirtelTigo"
    val customerPhone: String? = null,
    val momoReference: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val lineTotal: Double
        get() = product.price * quantity

    val lineCost: Double
        get() = product.costPrice * quantity
}
