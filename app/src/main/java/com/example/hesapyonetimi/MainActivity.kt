package com.example.hesapyonetimi

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Bottom nav dışındaki destinasyonlarda (KategoriDetay, Wallet) BottomNav gizle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevelIds = setOf(
                R.id.nav_ozet, R.id.nav_gunluk, R.id.nav_aylik,
                R.id.nav_yaklasan, R.id.nav_hedefler, R.id.nav_profil
            )
            bottomNav.visibility = if (destination.id in topLevelIds)
                android.view.View.VISIBLE else android.view.View.GONE
            // Profil sayfasında hiçbir tab seçili görünmesin
            if (destination.id == R.id.nav_profil) {
                bottomNav.menu.setGroupCheckable(0, true, false)
                for (i in 0 until bottomNav.menu.size()) bottomNav.menu.getItem(i).isChecked = false
                bottomNav.menu.setGroupCheckable(0, true, true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (pinGerekliMi()) {
            val intent = android.content.Intent(this, PinActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
                .edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
        }
    }

    private fun pinGerekliMi(): Boolean {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val sonGiris = prefs.getLong("son_giris_zamani", 0L)
        if (sonGiris == 0L) return true
        return (System.currentTimeMillis() - sonGiris) > 30 * 60 * 1000
    }

    fun applyTheme(mode: String) {
        getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
        val nightMode = when (mode) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun applyThemeFromPrefs() {
        val mode = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            .getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        val nightMode = when (mode) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    // setupWithNavController zaten navigasyonu yönetiyor.
    // selectedItemId set etmek YETERLİ — navController.navigate() ayrıca çağrılırsa
    // back stack iki kez işlenir ve home butonu çalışmaz.
    private fun bottomNav() = findViewById<BottomNavigationView>(R.id.bottom_navigation)

    fun gosterYaklasan() { bottomNav().selectedItemId = R.id.nav_yaklasan }

    fun gosterAylik() { bottomNav().selectedItemId = R.id.nav_aylik }

    fun gosterGunluk() { bottomNav().selectedItemId = R.id.nav_gunluk }

    fun gosterHedefler() { bottomNav().selectedItemId = R.id.nav_hedefler }

    fun gosterProfil() {
        // nav_profil menüde yok, doğrudan navigate
        val opts = NavOptions.Builder()
            .setPopUpTo(R.id.nav_ozet, false)
            .setLaunchSingleTop(true)
            .build()
        navController.navigate(R.id.nav_profil, null, opts)
    }
}
