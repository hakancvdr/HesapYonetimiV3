package com.example.hesapyonetimi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.hesapyonetimi.data.local.converters.Converters
import com.example.hesapyonetimi.data.local.dao.*
import com.example.hesapyonetimi.data.local.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase as MigSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        ReminderEntity::class,
        UserProfileEntity::class,
        WalletEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun reminderDao(): ReminderDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun walletDao(): WalletDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: MigSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `paidAt` INTEGER")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: MigSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `reminders` ADD COLUMN `notificationPolicy` TEXT NOT NULL DEFAULT 'LEGACY_MULTI'"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: MigSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_tags` (
                        `transactionId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`, `tagId`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_transactionId` ON `transaction_tags` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_tagId` ON `transaction_tags` (`tagId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: MigSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_contributions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `goalId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `contributedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: MigSQLiteDatabase) {
                // Yeni kolonlar: transactions tablosuna tags, isRecurring, recurringDays
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isRecurring` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringDays` INTEGER NOT NULL DEFAULT 30")
                // Yeni tablo: goals
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `icon` TEXT NOT NULL DEFAULT '🎯',
                        `targetAmount` REAL NOT NULL,
                        `currentAmount` REAL NOT NULL DEFAULT 0.0,
                        `deadline` INTEGER DEFAULT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: MigSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wallets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL DEFAULT '💳',
                        `color` TEXT NOT NULL DEFAULT '#0099CC',
                        `type` TEXT NOT NULL DEFAULT 'BANK',
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `walletId` INTEGER DEFAULT NULL")
                db.execSQL("""
                    INSERT INTO `wallets` (`name`, `icon`, `color`, `type`, `isDefault`, `createdAt`)
                    VALUES ('Nakit', '💵', '#4CAF50', 'CASH', 0, ${System.currentTimeMillis()}),
                           ('Banka Hesabı', '🏦', '#2196F3', 'BANK', 1, ${System.currentTimeMillis()})
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hesap_yonetimi_database"
                )
                    .addMigrations(
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12
                    )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDefaultCategories(database.categoryDao())
                    populateDefaultWallets(database.walletDao())
                    // Profil kaydı yalnızca hiç yoksa oluştur
                    // (RegistrationActivity sonradan kendi adını yazacak)
                    if (database.userProfileDao().getProfileOnce() == null) {
                        database.userProfileDao().upsertProfile(UserProfileEntity())
                    }
                }
            }
        }

        private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
            val defaultExpenseCategories = listOf(
                CategoryEntity(name = "Market", icon = "🛒", color = "#4CAF50", isIncome = false, isDefault = true),
                CategoryEntity(name = "Ulaşım", icon = "🚗", color = "#2196F3", isIncome = false, isDefault = true),
                CategoryEntity(name = "Faturalar", icon = "📄", color = "#FF9800", isIncome = false, isDefault = true),
                CategoryEntity(name = "Eğlence", icon = "🎮", color = "#9C27B0", isIncome = false, isDefault = true),
                CategoryEntity(name = "Sağlık", icon = "⚕️", color = "#F44336", isIncome = false, isDefault = true),
                CategoryEntity(name = "Eğitim", icon = "📚", color = "#3F51B5", isIncome = false, isDefault = true),
                CategoryEntity(name = "Giyim", icon = "👕", color = "#E91E63", isIncome = false, isDefault = true),
                CategoryEntity(name = "Diğer", icon = "📦", color = "#607D8B", isIncome = false, isDefault = true)
            )

            val defaultIncomeCategories = listOf(
                CategoryEntity(name = "Maaş", icon = "💰", color = "#4CAF50", isIncome = true, isDefault = true),
                CategoryEntity(name = "Freelance", icon = "💻", color = "#00BCD4", isIncome = true, isDefault = true),
                CategoryEntity(name = "Yatırım", icon = "📈", color = "#FF5722", isIncome = true, isDefault = true),
                CategoryEntity(name = "Hediye", icon = "🎁", color = "#E91E63", isIncome = true, isDefault = true),
                CategoryEntity(name = "Diğer", icon = "💵", color = "#607D8B", isIncome = true, isDefault = true)
            )

            categoryDao.insertAll(defaultExpenseCategories + defaultIncomeCategories)
        }

        private suspend fun populateDefaultWallets(walletDao: WalletDao) {
            walletDao.insert(WalletEntity(name = "Nakit", icon = "💵", color = "#4CAF50", type = "CASH"))
            walletDao.insert(WalletEntity(name = "Banka Hesabı", icon = "🏦", color = "#2196F3", type = "BANK", isDefault = true))
        }
    }
}
