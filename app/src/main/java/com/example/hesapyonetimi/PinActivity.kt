package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinActivity : AppCompatActivity() {

    // Değişken adını "girilenSifre" olarak tek bir standartta tutuyoruz
    private var girilenSifre = ""
    private lateinit var dots: Array<View>
    private var kayitliPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ADIM: SharedPreferences'ı aç
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)

        // 2. ADIM: Zaman kontrolü yap
        val sonGirisZamani = prefs.getLong("son_giris_zamani", 0L)
        val suAnkiZaman = System.currentTimeMillis()

        // 30 Dakika = 30 * 60 * 1000 milisaniye
        val otuzDakikaMS = 30 * 60 * 1000L

        // Kayıtlı bir PIN varsa VE henüz 30 dakika dolmadıysa PIN sorma
        val kayitliPinVarMi = prefs.getString("kullanici_pin", null) != null

        if (kayitliPinVarMi && (suAnkiZaman - sonGirisZamani < otuzDakikaMS)) {
            // Süre dolmamış, içeri al
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // 3. ADIM: Eğer yukarıdaki şart sağlanmadıysa (Süre dolduysa veya PIN yoksa)
        // Beyaz-Mavi-Yeşil ekranımızı yükle
        setContentView(R.layout.activity_pin)

        // UI bileşenlerini bağla
        dots = arrayOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        val tvBaslik = findViewById<TextView>(R.id.tv_pin_baslik)
        kayitliPin = prefs.getString("kullanici_pin", null)

        if (kayitliPin == null) {
            tvBaslik.text = "Yeni PIN Belirleyin"
        } else {
            tvBaslik.text = "PIN Girin"
        }
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val kayitliPin = prefs.getString("kullanici_pin", null)

        // Sadece şifresi olan kullanıcılar için süreyi başlat
        if (kayitliPin != null) {
            prefs.edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
        }
    }

    // --- XML'den android:onClick ile çağrılan fonksiyonlar ---

    fun onNumberClick(view: View) {
        if (girilenSifre.length < 4) {
            // Tıklanan butonun üzerindeki rakamı al
            val rakam = (view as Button).text.toString()
            girilenSifre += rakam
            updateDots() // Görünümü güncelle

            // 4 hane dolduysa kontrol et
            if (girilenSifre.length == 4) {
                onaylamaIslemi()
            }
        }
    }

    fun onDeleteClick(view: View) {
        if (girilenSifre.isNotEmpty()) {
            girilenSifre = girilenSifre.dropLast(1)
            updateDots()
        }
    }

    // --- Yardımcı Fonksiyonlar ---

    private fun updateDots() {
        dots.forEachIndexed { index, view ->
            if (index < girilenSifre.length) {
                view.setBackgroundResource(R.drawable.pin_dot_on) // Dolu (Yeşil)
            } else {
                view.setBackgroundResource(R.drawable.pin_dot_off) // Boş (Gri)
            }
        }
    }

    private fun onaylamaIslemi() {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)

        if (kayitliPin == null) {
            // İlk kez PIN oluşturuluyor
            prefs.edit().putString("kullanici_pin", girilenSifre).apply()
            Toast.makeText(this, "PIN Kaydedildi!", Toast.LENGTH_SHORT).show()
            oturumuBaslat()
        } else if (girilenSifre == kayitliPin) {
            // PIN doğru girildi
            oturumuBaslat()
        } else {
            // PIN yanlış
            Toast.makeText(this, "Hatalı PIN!", Toast.LENGTH_SHORT).show()
            girilenSifre = ""
            updateDots()
        }
    }

    private fun oturumuBaslat() {
        getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            .edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()

        // MainActivity'ye git
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}