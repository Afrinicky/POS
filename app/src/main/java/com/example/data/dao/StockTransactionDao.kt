package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction)

    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC LIMIT 100")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE sku = :sku ORDER BY timestamp DESC")
    fun getTransactionsForProduct(sku: String): Flow<List<StockTransaction>>
}
