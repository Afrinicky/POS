package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SaleRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleRecord)

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleRecord>>

    @Query("SELECT * FROM sales WHERE saleId = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: String): SaleRecord?

    @Query("SELECT SUM(totalAmount) FROM sales")
    fun getTotalRevenue(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sales")
    fun getTotalSaleCount(): Flow<Int>
}
