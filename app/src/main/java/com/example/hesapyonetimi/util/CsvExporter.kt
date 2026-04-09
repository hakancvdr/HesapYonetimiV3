package com.example.hesapyonetimi.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.hesapyonetimi.domain.model.Transaction
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("tr"))

    fun export(context: Context, transactions: List<Transaction>): Intent {
        val csvFile = File(context.cacheDir, "hesap_yonetimi_export.csv")
        FileWriter(csvFile).use { writer ->
            // BOM for Excel UTF-8 recognition
            writer.write("\uFEFF")
            writer.write("Tarih,Tür,Kategori,Açıklama,Tutar (TL),Cüzdan\n")
            transactions.forEach { t ->
                val date     = dateFmt.format(Date(t.date))
                val type     = if (t.isIncome) "Gelir" else "Gider"
                val category = escape(t.categoryName)
                val desc     = escape(t.description)
                val amount   = "%.2f".format(t.amount)
                val wallet   = escape(t.walletName ?: "")
                writer.write("$date,$type,$category,$desc,$amount,$wallet\n")
            }
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hesap Yönetimi - İşlem Raporu")
            putExtra(Intent.EXTRA_TEXT, "Hesap Yönetimi uygulamasından aktarılan işlem listesi.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\""
        else value
    }
}
