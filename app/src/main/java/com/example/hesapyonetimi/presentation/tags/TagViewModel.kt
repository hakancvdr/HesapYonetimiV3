package com.example.hesapyonetimi.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hesapyonetimi.data.local.dao.TagDao
import com.example.hesapyonetimi.data.local.entity.TagEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagViewModel @Inject constructor(
    private val tagDao: TagDao
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> = tagDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(nameRaw: String) {
        val name = nameRaw.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            tagDao.insert(TagEntity(name = name))
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            tagDao.deleteById(id)
        }
    }
}

