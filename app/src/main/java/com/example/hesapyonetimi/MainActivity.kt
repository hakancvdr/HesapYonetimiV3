package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Fragment cache — her biri bir kez oluşturulur, show/hide ile geçiş yapılır
    private val dashboardFragment = DashboardFragment()
    private val gunlukFragment    = GunlukFragment()
    private val aylikFragment     = AylikFragment()
    private val yaklasanFragment  = YaklasanFragment()

    private var activeFragment: Fragment = dashboardFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        if (savedInstanceState == null) {
            // İlk açılış: tüm fragment'ları ekle, sadece dashboard'u göster
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragment_container, yaklasanFragment,  "yaklasan").hide(yaklasanFragment)
                add(R.id.fragment_container, aylikFragment,     "aylik").hide(aylikFragment)
                add(R.id.fragment_container, gunlukFragment,    "gunluk").hide(gunlukFragment)
                add(R.id.fragment_container, dashboardFragment, "dashboard")
            }.commit()
        }

        setupNavigation()
    }

    override fun onStart() {
        super.onStart()
        if (pinGerekliMi()) {
            val intent = Intent(this, PinActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
        }
    }

    private fun pinGerekliMi(): Boolean {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val sonGiris = prefs.getLong("son_giris_zamani", 0L)
        val suAn = System.currentTimeMillis()
        val otuzDakikaMs = 30 * 60 * 1000
        if (sonGiris == 0L) return true
        return (suAn - sonGiris) > otuzDakikaMs
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_gunluk   -> gunlukFragment
                R.id.nav_aylik    -> aylikFragment
                R.id.nav_yaklasan -> yaklasanFragment
                else              -> dashboardFragment
            }
            goster(target)
            true
        }
    }

    fun gosterYaklasan() {
        goster(yaklasanFragment)
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            .selectedItemId = R.id.nav_yaklasan
    }

    fun showKategoriDetay(detay: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .add(R.id.fragment_container, detay, "detay")
            .addToBackStack("detay")
            .commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            // popBackStack async, listener ile show yapalım
            supportFragmentManager.addOnBackStackChangedListener(object : androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    if (supportFragmentManager.backStackEntryCount == 0) {
                        supportFragmentManager.beginTransaction().show(activeFragment).commit()
                        supportFragmentManager.removeOnBackStackChangedListener(this)
                    }
                }
            })
        } else {
            super.onBackPressed()
        }
    }

    private fun goster(hedef: Fragment) {
        // Detay fragment varsa temizle
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        if (hedef == activeFragment) {
            supportFragmentManager.beginTransaction().show(hedef).commit()
            return
        }
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(hedef)
            .commit()
        activeFragment = hedef
    }
}
