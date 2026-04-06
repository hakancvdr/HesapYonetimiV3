package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.databinding.ItemCalendarDayBinding
import com.example.hesapyonetimi.model.CalendarModel

class CalendarAdapter(
    private var days: List<CalendarModel>,
    private var selectedPosition: Int = -1,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    class CalendarViewHolder(val binding: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = days[position]
        holder.binding.tvDayName.text = day.dayName
        holder.binding.tvDayNumber.text = day.dayNumber

        val isSelected = position == selectedPosition
        val hasData = day.hasData ?: false
        
        when {
            // Bugün (seçili) - yeşil dolu circle, beyaz text
            isSelected -> {
                holder.binding.bgCircle.visibility = View.VISIBLE
                holder.binding.borderCircle.visibility = View.GONE
                
                // Gün adı (Pzt, Sal...) sabit renkte kalır, sadece sayı vurgulanır
                holder.binding.tvDayName.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
                )
                holder.binding.tvDayNumber.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_white)
                )
            }
            // Veri olan gün - açık yeşil border circle
            hasData -> {
                holder.binding.bgCircle.visibility = View.GONE
                holder.binding.borderCircle.visibility = View.VISIBLE
                
                holder.binding.tvDayName.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
                )
                holder.binding.tvDayNumber.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.green_primary)
                )
            }
            // Normal gün - border yok
            else -> {
                holder.binding.bgCircle.visibility = View.GONE
                holder.binding.borderCircle.visibility = View.GONE
                
                holder.binding.tvDayName.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
                )
                holder.binding.tvDayNumber.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_primary)
                )
            }
        }

        holder.itemView.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onItemClick(position)
        }
    }
    fun updateDaysAndSelection(newDays: List<CalendarModel>, newPosition: Int) {
        days = newDays
        val old = selectedPosition
        selectedPosition = newPosition
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(newPosition)
    }

    override fun getItemCount(): Int = days.size
}