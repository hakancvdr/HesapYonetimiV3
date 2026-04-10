package com.example.hesapyonetimi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.hesapyonetimi.data.local.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {
    @Insert
    suspend fun insert(row: GoalContributionEntity): Long

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY contributedAt DESC")
    fun observeForGoal(goalId: Long): Flow<List<GoalContributionEntity>>

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteForGoal(goalId: Long)

    @Query("DELETE FROM goal_contributions")
    suspend fun deleteAll()
}
