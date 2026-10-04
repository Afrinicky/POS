package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sku: String,
    val productName: String,
    val delta: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String, // "INITIAL", "RESTOCK", "SALE", "ADJUSTMENT", "RETURN"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
