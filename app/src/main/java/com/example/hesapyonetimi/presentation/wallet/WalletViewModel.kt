package com.example.hesapyonetimi.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.dao.WalletDao
import com.example.hesapyonetimi.data.local.entity.WalletEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUiItem(
    val wallet: WalletEntity,
    val totalIncome: Double,
    val totalExpense: Double
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    val wallets: StateFlow<List<WalletUiItem>> = walletDao.getAllWallets()
        .map { walletList ->
            val allTx = transactionDao.getAllTransactionsOnce()
            walletList.map { wallet ->
                val walletTx = allTx.filter { it.walletId == wallet.id }
                WalletUiItem(
                    wallet = wallet,
                    totalIncome = walletTx.filter { it.isIncome }.sumOf { it.amount },
                    totalExpense = walletTx.filter { !it.isIncome }.sumOf { it.amount }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWallet(name: String, icon: String, type: String) {
        viewModelScope.launch {
            walletDao.insert(WalletEntity(name = name, icon = icon, type = type))
        }
    }

    fun deleteWallet(wallet: WalletEntity) {
        if (wallet.isDefault) return
        viewModelScope.launch { walletDao.delete(wallet) }
    }

    fun setDefault(wallet: WalletEntity) {
        viewModelScope.launch {
            val all = walletDao.getAllWallets().first()
            all.forEach { w ->
                if (w.isDefault && w.id != wallet.id) {
                    walletDao.update(w.copy(isDefault = false))
                }
            }
            walletDao.update(wallet.copy(isDefault = true))
        }
    }
}
