package com.example.hesapyonetimi

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.util.LocaleHelper
import com.example.hesapyonetimi.worker.BudgetAlertWorker
import com.example.hesapyonetimi.worker.WeeklySummaryWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class HesapYonetimiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        CurrencyFormatter.init(this)
        schedulePeriodic()
    }

    private fun schedulePeriodic() {
        val wm = WorkManager.getInstance(this)

        // Günlük bütçe kontrolü — her gün 14:00
        val budgetDelay = calcDelayToHour(14)
        val budgetRequest = PeriodicWorkRequestBuilder<BudgetAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(budgetDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        wm.enqueueUniquePeriodicWork(
            "budget_alert", ExistingPeriodicWorkPolicy.UPDATE, budgetRequest
        )

        // Haftalık özet — her 7 günde bir, Pazartesi 14:00
        val weeklyDelay = calcDelayToNextMonday(14)
        val weeklyRequest = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(weeklyDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        wm.enqueueUniquePeriodicWork(
            "weekly_summary", ExistingPeriodicWorkPolicy.UPDATE, weeklyRequest
        )

        // Tekrarlayan işlem özelliği kaldırıldı — eski kurulumlardan kalan işi de iptal et
        wm.cancelUniqueWork("recurring_transactions")
    }

    private fun calcDelayToHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun calcDelayToNextMonday(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Advance to next Monday
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || !after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
