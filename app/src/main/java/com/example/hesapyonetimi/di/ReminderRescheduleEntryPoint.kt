package com.example.hesapyonetimi.di

import com.example.hesapyonetimi.domain.repository.ReminderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderRescheduleEntryPoint {
    fun reminderRepository(): ReminderRepository
}
