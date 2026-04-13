package com.example.hesapyonetimi.presentation.categories

import com.example.hesapyonetimi.domain.model.Category

object CategorySlotBuilder {
    /**
     * Builds a fixed-size list for entry screens.
     *
     * Rules:
     * - Output is at most [limit]
     * - If [selected] exists and is not already in [top], it replaces the lowest-priority slot
     *   (end of list) that is not [otherName]. The list never grows to [limit]+1.
     * - If [selected] is [otherName], no replacement is performed.
     */
    fun buildFixedSlots(
        top: List<Category>,
        selected: Category?,
        limit: Int,
        otherName: String = "Diğer"
    ): List<Category> {
        val base = top.distinctBy { it.id }.take(limit).toMutableList()
        val sel = selected ?: return base
        if (sel.name.equals(otherName, ignoreCase = true)) return base
        if (base.any { it.id == sel.id }) return base

        val idxToReplace = base.indexOfLast { !it.name.equals(otherName, ignoreCase = true) }
        if (idxToReplace >= 0) {
            base[idxToReplace] = sel
        } else if (base.size < limit) {
            base.add(sel)
        }
        return base.distinctBy { it.id }.take(limit)
    }
}

