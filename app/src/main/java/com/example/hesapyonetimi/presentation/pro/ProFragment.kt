package com.example.hesapyonetimi.presentation.pro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.domain.repository.TransactionRepository
import com.example.hesapyonetimi.util.CsvExporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProFragment : Fragment() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_pro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sw = view.findViewById<MaterialSwitch>(R.id.switch_pro_demo)
        sw?.isChecked = AuthPrefs.isProMember(requireContext())
        sw?.setOnCheckedChangeListener { _, checked ->
            AuthPrefs.setProMember(requireContext(), checked)
        }
        view.findViewById<View>(R.id.cardCsvExport)?.setOnClickListener { promptExportCsv() }
    }

    private fun promptExportCsv() {
        val ctx = requireContext()
        if (!AuthPrefs.canManualExportCsvThisMonth(ctx)) {
            Toast.makeText(ctx, getString(R.string.export_csv_monthly_limit), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.export_csv_warning_title)
            .setMessage(R.string.export_csv_warning_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.export_csv_confirm) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val txList = transactionRepository.getAllTransactions().first()
                        if (txList.isEmpty()) {
                            Toast.makeText(ctx, ctx.getString(R.string.export_csv_empty), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val intent = CsvExporter.export(requireContext(), txList)
                        AuthPrefs.markManualExportCsvDoneForCurrentMonth(requireContext())
                        startActivity(
                            android.content.Intent.createChooser(
                                intent,
                                getString(R.string.export_csv_chooser_title)
                            )
                        )
                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.message ?: "Export error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}
