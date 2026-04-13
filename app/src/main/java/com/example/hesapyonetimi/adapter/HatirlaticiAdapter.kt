package com.example.hesapyonetimi.adapter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.example.hesapyonetimi.ui.MaterialCategoryIcon
import java.text.SimpleDateFormat
import java.util.*

class HatirlaticiAdapter(
    private var liste: List<Reminder>,
    private val onOdendi: (Long) -> Unit,
    private val onDuzenle: (Reminder) -> Unit,
    private val onSil: (Long) -> Unit,
    private val onSilWithUndo: (Reminder) -> Unit
) : RecyclerView.Adapter<HatirlaticiAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cizgi: View = view.findViewById(R.id.view_durum_cizgi)
        val baslik: TextView = view.findViewById(R.id.tv_hatirlatici_baslik)
        val tarih: TextView = view.findViewById(R.id.tv_hatirlatici_tarih)
        val kalanGun: TextView = view.findViewById(R.id.tv_kalan_gun)
        val tekrar: TextView = view.findViewById(R.id.tv_tekrar_tipi)
        val tutar: TextView = view.findViewById(R.id.tv_hatirlatici_tutar)
        val odendi: TextView = view.findViewById(R.id.btn_odendi)
        val icon: TextView = view.findViewById(R.id.tv_hatirlatici_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hatirlatici, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = liste[position]
        val ctx = holder.itemView.context
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))

        MaterialCategoryIcon.bind(holder.icon, r.categoryIcon.ifBlank { "event" }, 16f)
        holder.baslik.text = r.title
        holder.tarih.text = dateFormat.format(Date(r.dueDate))
        holder.tutar.text = CurrencyFormatter.format(r.amount)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val daysLeft = ((r.dueDate - today) / (1000 * 60 * 60 * 24)).toInt()

        when {
            r.isOverdue -> {
                holder.kalanGun.text = "${-daysLeft} gün geçti"
                holder.kalanGun.setTextColor(Color.parseColor("#D32F2F"))
                holder.cizgi.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            daysLeft == 0 -> {
                holder.kalanGun.text = "Bugün!"
                holder.kalanGun.setTextColor(Color.parseColor("#F57C00"))
                holder.cizgi.setBackgroundColor(Color.parseColor("#F57C00"))
            }
            daysLeft <= 3 -> {
                holder.kalanGun.text = "$daysLeft gün kaldı"
                holder.kalanGun.setTextColor(Color.parseColor("#F57C00"))
                holder.cizgi.setBackgroundColor(Color.parseColor("#F57C00"))
            }
            else -> {
                holder.kalanGun.text = "$daysLeft gün kaldı"
                holder.kalanGun.setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
                holder.cizgi.setBackgroundColor(ContextCompat.getColor(ctx, R.color.green_primary))
            }
        }

        if (r.isRecurring && r.recurringType != null) {
            holder.tekrar.visibility = View.VISIBLE
            holder.tekrar.text = when (r.recurringType.name) {
                "MONTHLY" -> "🔁 Aylık"
                "YEARLY" -> "🔁 Yıllık"
                "WEEKLY" -> "🔁 Haftalık"
                else -> ""
            }
        } else {
            holder.tekrar.visibility = View.GONE
        }

        holder.odendi.setOnClickListener { onOdendi(r.id) }
        holder.itemView.setOnClickListener { onDuzenle(r) }

        // Ödendi ise soluk + üstü çizili, buton gizle
        if (r.isPaid) {
            holder.itemView.alpha = 0.52f
            holder.baslik.paintFlags = holder.baslik.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.odendi.visibility = View.GONE
            holder.kalanGun.text = "✓ Ödendi"
            holder.kalanGun.setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
            holder.cizgi.setBackgroundColor(ContextCompat.getColor(ctx, R.color.green_primary))
        } else {
            holder.itemView.alpha = 1f
            holder.baslik.paintFlags = holder.baslik.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.odendi.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = liste.size

    fun update(yeni: List<Reminder>) {
        liste = yeni
        notifyDataSetChanged()
    }

    fun getReminderAt(position: Int): Reminder = liste[position]

    // SwipeCallback — RecyclerView'a attachSwipe() ile bağla
    fun createSwipeCallback(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            private val silRenk = ColorDrawable(Color.parseColor("#EF5350"))
            private val duzenleRenk = ColorDrawable(Color.parseColor("#1976D2"))
            private val paint = Paint().apply {
                color = Color.WHITE
                textSize = 42f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val reminder = liste[pos]
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Kartı geri döndür, Snackbar ile geri al seçeneği sun
                        notifyItemChanged(pos)
                        onSilWithUndo(reminder)
                    }
                    ItemTouchHelper.RIGHT -> {
                        notifyItemChanged(pos)
                        onDuzenle(reminder)
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                val cornerRadius = 28f

                val swipeWidth = 220f // maksimum swipe genişliği
                if (dX < 0) {
                    val left = (itemView.right + dX).coerceAtLeast(itemView.right - swipeWidth)
                    val rect = RectF(left, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    paint.color = Color.parseColor("#EF5350")
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 32f
                    val textX = (left + itemView.right) / 2f
                    val textY = itemView.top + itemHeight / 2f + 10f
                    c.drawText("🗑", textX, textY, paint)
                } else if (dX > 0) {
                    val right = (itemView.left + dX).coerceAtMost(itemView.left + swipeWidth)
                    val rect = RectF(itemView.left.toFloat(), itemView.top.toFloat(), right, itemView.bottom.toFloat())
                    paint.color = Color.parseColor("#2C3E8C")
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 32f
                    val textX = (itemView.left + right) / 2f
                    val textY = itemView.top + itemHeight / 2f + 10f
                    c.drawText("✏", textX, textY, paint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
    }
}
