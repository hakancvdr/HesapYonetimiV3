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
    version = 13,
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
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: MigSQLiteDatabase) {
                // Subcategory support + locked categories
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `parentId` INTEGER")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `isLocked` INTEGER NOT NULL DEFAULT 0")

                // Lock 'Diğer' (both income/expense)
                db.execSQL("UPDATE `categories` SET `isLocked` = 1 WHERE `name` = 'Diğer'")

                // Normalize expense default name: Faturalar -> Fatura
                db.execSQL(
                    "UPDATE `categories` SET `name` = 'Fatura' WHERE `isIncome` = 0 AND `isDefault` = 1 AND `name` = 'Faturalar'"
                )

                // Ensure missing default expense categories exist
                db.execSQL(
                    """
                    INSERT INTO `categories` (`name`, `icon`, `color`, `isIncome`, `isDefault`, `parentId`, `isLocked`, `createdAt`)
                    SELECT 'Yemek dışarı', '🍽️', '#FF7043', 0, 1, NULL, 0, strftime('%s','now')*1000
                    WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `isIncome` = 0 AND `isDefault` = 1 AND `name` = 'Yemek dışarı')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `categories` (`name`, `icon`, `color`, `isIncome`, `isDefault`, `parentId`, `isLocked`, `createdAt`)
                    SELECT 'Kira', '🏠', '#8D6E63', 0, 1, NULL, 0, strftime('%s','now')*1000
                    WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `isIncome` = 0 AND `isDefault` = 1 AND `name` = 'Kira')
                    """.trimIndent()
                )
            }
        }

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
                        MIGRATION_11_12,
                        MIGRATION_12_13
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
                CategoryEntity(name = "Market", icon = "shopping_cart", color = "#4CAF50", isIncome = false, isDefault = true),
                CategoryEntity(name = "Ulaşım", icon = "directions_car", color = "#2196F3", isIncome = false, isDefault = true),
                CategoryEntity(name = "Fatura", icon = "receipt_long", color = "#FF9800", isIncome = false, isDefault = true),
                CategoryEntity(name = "Eğlence", icon = "sports_esports", color = "#9C27B0", isIncome = false, isDefault = true),
                CategoryEntity(name = "Sağlık", icon = "medical_services", color = "#F44336", isIncome = false, isDefault = true),
                CategoryEntity(name = "Eğitim", icon = "school", color = "#3F51B5", isIncome = false, isDefault = true),
                CategoryEntity(name = "Giyim", icon = "checkroom", color = "#E91E63", isIncome = false, isDefault = true),
                CategoryEntity(name = "Yemek dışarı", icon = "restaurant", color = "#FF7043", isIncome = false, isDefault = true),
                CategoryEntity(name = "Kira", icon = "home", color = "#8D6E63", isIncome = false, isDefault = true),
                CategoryEntity(name = "Diğer", icon = "inventory_2", color = "#607D8B", isIncome = false, isDefault = true, isLocked = true)
            )

            val defaultIncomeCategories = listOf(
                CategoryEntity(name = "Maaş", icon = "payments", color = "#4CAF50", isIncome = true, isDefault = true),
                CategoryEntity(name = "Freelance", icon = "laptop_mac", color = "#00BCD4", isIncome = true, isDefault = true),
                CategoryEntity(name = "Yatırım", icon = "trending_up", color = "#FF5722", isIncome = true, isDefault = true),
                CategoryEntity(name = "Hediye", icon = "redeem", color = "#E91E63", isIncome = true, isDefault = true),
                CategoryEntity(name = "Diğer", icon = "paid", color = "#607D8B", isIncome = true, isDefault = true, isLocked = true)
            )

            categoryDao.insertAll(defaultExpenseCategories + defaultIncomeCategories)
        }

        private suspend fun populateDefaultWallets(walletDao: WalletDao) {
            walletDao.insert(WalletEntity(name = "Nakit", icon = "💵", color = "#4CAF50", type = "CASH"))
            walletDao.insert(WalletEntity(name = "Banka Hesabı", icon = "🏦", color = "#2196F3", type = "BANK", isDefault = true))
        }
    }
}
