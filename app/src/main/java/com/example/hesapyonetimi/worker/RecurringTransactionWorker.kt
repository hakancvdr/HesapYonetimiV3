package com.example.hesapyonetimi.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hesapyonetimi.data.local.dao.TransactionDao
import com.example.hesapyonetimi.data.local.entity.TransactionEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val recurring = transactionDao.getRecurringTransactions()

            for (tx in recurring) {
                val intervalMs = TimeUnit.DAYS.toMillis(tx.recurringDays.toLong())
                val nextDueDate = tx.date + intervalMs
                if (now >= nextDueDate) {
                    // Create a new transaction based on the recurring one
                    val newTx = TransactionEntity(
                        amount = tx.amount,
                        categoryId = tx.categoryId,
                        description = tx.description,
                        date = now,
                        isIncome = tx.isIncome,
                        walletId = tx.walletId,
                        tags = tx.tags,
                        isRecurring = true,
                        recurringDays = tx.recurringDays
                    )
                    transactionDao.insert(newTx)

                    // Update the original transaction's date so it won't trigger again until next cycle
                    transactionDao.update(tx.copy(date = now, updatedAt = now))
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
