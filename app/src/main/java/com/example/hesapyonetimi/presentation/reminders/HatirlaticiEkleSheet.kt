package com.example.hesapyonetimi.presentation.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.data.local.entity.RecurringType
import com.example.hesapyonetimi.domain.model.Category
import com.example.hesapyonetimi.domain.model.Reminder
import com.example.hesapyonetimi.adapter.CategoryChipAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HatirlaticiEkleSheet : BottomSheetDialogFragment() {

    private val viewModel: ReminderViewModel by viewModels()
    private var selectedDateMillis: Long = 0L
    private var selectedHour: Int = 9
    private var selectedMinute: Int = 0
    private var selectedRecurring: RecurringType? = null
    private var selectedDonem: Int = 1
    private var selectedCategoryId: Long = 0L
    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))

    // Düzenleme modu için (Reminder Parcelable olmadığından instance alanı)
    var editReminder: Reminder? = null

    companion object {
        private const val ARG_Q_TITLE = "quick_title"
        private const val ARG_Q_AMOUNT = "quick_amount"
        private const val ARG_Q_CATEGORY = "quick_category_id"

        fun newInstance(reminder: Reminder? = null): HatirlaticiEkleSheet {
            return HatirlaticiEkleSheet().apply { editReminder = reminder }
        }

        /** Hızlı öneri kartından: açıklama, tutar ve kategori önceden doldurulur */
        fun newInstanceFromQuickFill(title: String, amount: Double, categoryId: Long): HatirlaticiEkleSheet {
            return HatirlaticiEkleSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_Q_TITLE, title)
                    putDouble(ARG_Q_AMOUNT, amount)
                    putLong(ARG_Q_CATEGORY, categoryId)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(
            requireView().parent as android.view.View
        )
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        // Klavye açılınca layout kaymasını engelle
        dialog?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_hatirlatici_ekle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAciklama = view.findViewById<TextInputEditText>(R.id.et_aciklama)
        val etTutar = view.findViewById<TextInputEditText>(R.id.et_tutar)
        val etTarih = view.findViewById<TextInputEditText>(R.id.et_tarih)
        val tilTarih = view.findViewById<TextInputLayout>(R.id.til_tarih)
        val btnKaydet = view.findViewById<View>(R.id.btn_kaydet)
        val tvBaslik = view.findViewById<TextView>(R.id.tv_sheet_baslik)

        // Düzenleme modunda mevcut değerleri doldur
        editReminder?.let { r ->
            tvBaslik.text = "Hatırlatıcıyı Düzenle"
            etAciklama.setText(r.title)
            etTutar.setText(r.amount.toBigDecimal().stripTrailingZeros().toPlainString())
            selectedDateMillis = r.dueDate
            etTarih.setText(dateFormat.format(Date(r.dueDate)))
            selectedCategoryId = r.categoryId
            selectedRecurring = r.recurringType
        } ?: run {
            // Varsayılan: 1 ay sonra
            selectedDateMillis = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.timeInMillis
            etTarih.setText(dateFormat.format(Date(selectedDateMillis)))
            arguments?.getString(ARG_Q_TITLE)?.takeIf { it.isNotBlank() }?.let { etAciklama.setText(it) }
            arguments?.let { b ->
                if (b.containsKey(ARG_Q_AMOUNT)) {
                    val a = b.getDouble(ARG_Q_AMOUNT, 0.0)
                    if (a > 0) etTutar.setText(a.toBigDecimal().stripTrailingZeros().toPlainString())
                }
                val cid = b.getLong(ARG_Q_CATEGORY, 0L)
                if (cid > 0) selectedCategoryId = cid
            }
        }

        // Kategori seçim
        val rvKategori = view.findViewById<RecyclerView>(R.id.rv_kategori_secim)
        rvKategori.layoutManager = GridLayoutManager(requireContext(), 2)

        val categoryAdapter = CategoryChipAdapter(emptyList()) { cat ->
            selectedCategoryId = cat.id
        }
        rvKategori.adapter = categoryAdapter

        viewModel.loadCategories { categories ->
            val filtered = categories.filter { !it.isIncome }
            categoryAdapter.setCategories(filtered, "")
            editReminder?.let { categoryAdapter.setSelected(it.categoryId) }
                ?: arguments?.getLong(ARG_Q_CATEGORY, 0L)?.takeIf { it > 0 }
                    ?.let { categoryAdapter.setSelected(it) }
        }

        // Saat seçici
        val tvSelectedTime = view.findViewById<TextView>(R.id.tvSelectedTime)
        fun updateTimeLabelAndDate() {
            tvSelectedTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            // dueDate içindeki saati güncelle
            val c = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            c.set(Calendar.HOUR_OF_DAY, selectedHour)
            c.set(Calendar.MINUTE, selectedMinute)
            c.set(Calendar.SECOND, 0)
            selectedDateMillis = c.timeInMillis
        }
        // Düzenleme modunda mevcut saati yükle
        editReminder?.let {
            val c = Calendar.getInstance().apply { timeInMillis = it.dueDate }
            selectedHour = c.get(Calendar.HOUR_OF_DAY)
            selectedMinute = c.get(Calendar.MINUTE)
            updateTimeLabelAndDate()
        }
        tvSelectedTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, min ->
                selectedHour = h; selectedMinute = min
                updateTimeLabelAndDate()
            }, selectedHour, selectedMinute, true).show()
        }

        // Tarih seçici
        val tarihClick = View.OnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                c.set(y, m, d)
                c.set(Calendar.HOUR_OF_DAY, selectedHour)
                c.set(Calendar.MINUTE, selectedMinute)
                c.set(Calendar.SECOND, 0)
                selectedDateMillis = c.timeInMillis
                etTarih.setText(dateFormat.format(c.time))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        etTarih.setOnClickListener(tarihClick)
        tilTarih.setEndIconOnClickListener(tarihClick)

        // Tekrar butonları
        setupTekrar(view)

        // Kaydet
        btnKaydet.setOnClickListener {
            val aciklama = etAciklama.text?.toString()?.trim() ?: ""
            val tutarStr = etTutar.text?.toString()?.trim() ?: ""

            if (aciklama.isEmpty()) { etAciklama.error = "Açıklama gerekli"; return@setOnClickListener }
            if (tutarStr.isEmpty()) { etTutar.error = "Tutar gerekli"; return@setOnClickListener }
            if (selectedCategoryId == 0L) {
                Toast.makeText(requireContext(), "Kategori seçin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tutar = tutarStr.toDoubleOrNull() ?: run {
                etTutar.error = "Geçersiz tutar"; return@setOnClickListener
            }

            if (editReminder != null) {
                viewModel.updateReminder(editReminder!!.copy(
                    title = aciklama, amount = tutar,
                    dueDate = selectedDateMillis, categoryId = selectedCategoryId,
                    isRecurring = selectedRecurring != null, recurringType = selectedRecurring
                ))
                Toast.makeText(requireContext(), "✅ Güncellendi", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addReminder(
                    title = aciklama, amount = tutar,
                    dueDate = selectedDateMillis, categoryId = selectedCategoryId,
                    recurringType = selectedRecurring, donemSayisi = selectedDonem
                )
                Toast.makeText(requireContext(), "✅ Hatırlatıcı eklendi", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    private fun setupTekrar(view: View) {
        val btnTek = view.findViewById<TextView>(R.id.btn_tek_sefer)
        val btnAylik = view.findViewById<TextView>(R.id.btn_aylik)
        val donemContainer = view.findViewById<View>(R.id.donem_container)
        val tekrarButonlari = listOf(btnTek, btnAylik)

        fun secTekrar(secili: TextView, recurring: RecurringType?) {
            selectedRecurring = recurring
            tekrarButonlari.forEach { btn ->
                val s = btn == secili
                btn.setBackgroundResource(if (s) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(if (s) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
                    else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            donemContainer.visibility = if (recurring != null) View.VISIBLE else View.GONE
        }

        secTekrar(btnTek, null)
        btnTek.setOnClickListener { secTekrar(btnTek, null) }
        btnAylik.setOnClickListener { secTekrar(btnAylik, RecurringType.MONTHLY) }

        // Dönem sayısı
        val donemButonlari = listOf(
            view.findViewById<TextView>(R.id.btn_donem_1) to 1,
            view.findViewById<TextView>(R.id.btn_donem_3) to 3,
            view.findViewById<TextView>(R.id.btn_donem_6) to 6,
            view.findViewById<TextView>(R.id.btn_donem_12) to 12
        )

        fun secDonem(sayi: Int) {
            selectedDonem = sayi
            donemButonlari.forEach { (btn, n) ->
                val s = n == sayi
                btn.setBackgroundResource(if (s) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(if (s) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_primary)
                    else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
        }

        secDonem(1)
        donemButonlari.forEach { (btn, n) -> btn.setOnClickListener { secDonem(n) } }
    }
}
