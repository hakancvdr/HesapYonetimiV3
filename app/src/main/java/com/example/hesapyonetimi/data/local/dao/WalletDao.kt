package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Delete
    suspend fun delete(wallet: WalletEntity)

    @Query("SELECT * FROM wallets ORDER BY isDefault DESC, name ASC")
    fun getAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: Long): WalletEntity?

    @Query("SELECT * FROM wallets WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultWallet(): WalletEntity?
}
