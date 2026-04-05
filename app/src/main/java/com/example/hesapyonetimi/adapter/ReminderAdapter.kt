package com.example.hesapyonetimi.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.databinding.ItemReminderRowBinding
import com.example.hesapyonetimi.model.ReminderModel

// onItemClick parametresini ekledik
class ReminderAdapter(
    private val reminders: List<ReminderModel>,
    private val onItemClick: () -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    class ReminderViewHolder(val binding: ItemReminderRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        holder.binding.tvReminderTitle.text = reminder.title
        holder.binding.tvReminderAmount.text = reminder.amount
        holder.binding.tvReminderDate.text = reminder.dueDate

        // TÜM SATIRA TIKLAMA OLAYI
        holder.itemView.setOnClickListener {
            onItemClick() // Fragment'a "Hadi git" diyoruz
        }
    }

    override fun getItemCount(): Int = reminders.size
}