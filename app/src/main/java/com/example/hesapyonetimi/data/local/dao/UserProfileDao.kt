package com.example.hesapyonetimi.data.local.dao

import androidx.room.*
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET displayName = :name WHERE id = 1")
    suspend fun updateName(name: String)

    @Query("UPDATE user_profile SET avatarEmoji = :emoji WHERE id = 1")
    suspend fun updateAvatar(emoji: String)

    @Query("UPDATE user_profile SET monthlyBudgetLimit = :limit WHERE id = 1")
    suspend fun updateBudgetLimit(limit: Double)

    @Query("UPDATE user_profile SET themeMode = :mode WHERE id = 1")
    suspend fun updateThemeMode(mode: String)
}
