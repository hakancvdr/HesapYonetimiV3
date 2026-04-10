package com.example.hesapyonetimi.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.GoalContributionDao
import com.example.hesapyonetimi.data.local.dao.GoalDao
import com.example.hesapyonetimi.data.local.entity.GoalContributionEntity
import com.example.hesapyonetimi.data.local.entity.GoalEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val contributionDao: GoalContributionDao
) : ViewModel() {

    val goals: StateFlow<List<GoalEntity>> = goalDao.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun contributionsFlow(goalId: Long): Flow<List<GoalContributionEntity>> =
        contributionDao.observeForGoal(goalId)

    fun addGoal(title: String, icon: String, targetAmount: Double, deadline: Long?) {
        viewModelScope.launch {
            goalDao.insert(GoalEntity(
                title = title,
                icon = icon,
                targetAmount = targetAmount,
                deadline = deadline
            ))
        }
    }

    fun updateGoal(goal: GoalEntity) {
        viewModelScope.launch {
            goalDao.update(goal)
        }
    }

    fun addContribution(goal: GoalEntity, amount: Double) {
        viewModelScope.launch {
            contributionDao.insert(GoalContributionEntity(goalId = goal.id, amount = amount))
            val updated = goal.copy(currentAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount))
            goalDao.update(updated)
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            contributionDao.deleteForGoal(goal.id)
            goalDao.delete(goal)
        }
    }
}
