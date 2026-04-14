package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.hesapyonetimi.BuildConfig
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.auth.AuthSignOut
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.util.LocaleHelper
import com.example.hesapyonetimi.worker.ReminderScheduler
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.res.Configuration

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
        const val EXTRA_SHOW_DAILY_COACHMARK = "extra_show_daily_coachmark"
        const val TAB_GUNLUK = "gunluk"
        const val TAB_OZET = "ozet"
    }

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var userProfileDao: UserProfileDao

    private var pendingDailyCoachmark: Boolean = false

    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        val isLightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Light temada status bar ikonları koyu olmalı; dark temada açık.
            isAppearanceLightStatusBars = isLightTheme
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // Toolbar: her temada gradient (kontrast/okunabilirlik için).
        toolbar.setBackgroundResource(R.drawable.gradient_header)
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white))
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, 0)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        drawerLayout = findViewById(R.id.drawer_layout)
        val navDrawer = findViewById<NavigationView>(R.id.nav_drawer)
        val drawerHeader = navDrawer.getHeaderView(0)
        drawerHeader.findViewById<ImageButton>(R.id.drawer_close).setOnClickListener {
            drawerLayout.closeDrawers()
        }
        drawerHeader.findViewById<TextView>(R.id.drawer_header_version).text =
            "v${BuildConfig.VERSION_NAME}"
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfileDao.getProfile().collect { profile ->
                    val tvProfile = drawerHeader.findViewById<TextView>(R.id.drawer_header_profile)
                    val tvAvatarLetter =
                        drawerHeader.findViewById<TextView>(R.id.drawer_header_avatar_letter)
                    val tvMembership =
                        drawerHeader.findViewById<TextView>(R.id.drawer_header_subtitle)
                    val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
                    val name = profile?.displayName
                        ?.takeIf { it.isNotBlank() && it != "Kullanıcı" }
                        ?: prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
                        ?: "Kullanıcı"
                    tvProfile.text = name

                    tvAvatarLetter.text =
                        (name.firstOrNull()?.uppercaseChar()?.toString() ?: "K")

                    tvMembership.text = if (AuthPrefs.isProMember(this@MainActivity)) {
                        getString(R.string.drawer_membership_premium)
                    } else {
                        getString(R.string.drawer_membership_normal)
                    }
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(navDrawer) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val padH = (20 * resources.displayMetrics.density).toInt()
            val padBottom = (20 * resources.displayMetrics.density).toInt()
            drawerHeader.setPadding(padH, top + (20 * resources.displayMetrics.density).toInt(), padH, padBottom)
            insets
        }

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_ozet, R.id.nav_gunluk, R.id.nav_aylik,
                R.id.nav_plan, R.id.nav_profil
            ),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        // Hamburger/back ikonları her temada beyaz (gradient üstünde okunur).
        val navIconWhite = ContextCompat.getColor(this, android.R.color.white)
        toolbar.setNavigationIconTint(navIconWhite)
        (toolbar.navigationIcon as? DrawerArrowDrawable)?.color = navIconWhite
        toolbar.navigationIcon?.mutate()?.setTint(navIconWhite)

        navDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_account -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.nav_profil, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_categories -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.categoryPickerFragment, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_summary_monthly -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.nav_aylik, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_budget -> {
                    val args = Bundle().apply { putBoolean("openBudgetDialog", true) }
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.nav_profil, args, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_sign_in -> {
                    startActivity(Intent(this, WelcomeActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_sign_out -> {
                    drawerLayout.closeDrawers()
                    confirmAndSignOut()
                }
                R.id.drawer_theme_language -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.themeLanguageFragment, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_help -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.helpPlaceholderFragment, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_contact -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.contactPlaceholderFragment, null, opts)
                    drawerLayout.closeDrawers()
                }
            }
            true
        }

        // Drawer footer actions (görseldeki alt iki buton)
        findViewById<View>(R.id.btn_drawer_go_pro).setOnClickListener {
            navController.navigate(R.id.proFragment)
            drawerLayout.closeDrawers()
        }
        findViewById<View>(R.id.btn_drawer_rate_app).setOnClickListener {
            openAppRating()
            drawerLayout.closeDrawers()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        if (savedInstanceState == null) {
            pendingDailyCoachmark = intent.getBooleanExtra(EXTRA_SHOW_DAILY_COACHMARK, false)
            when (intent.getStringExtra(EXTRA_START_TAB)) {
                TAB_GUNLUK -> bottomNav.selectedItemId = R.id.nav_gunluk
                TAB_OZET -> bottomNav.selectedItemId = R.id.nav_ozet
            }
        }

        // Android 13+: bildirim izni runtime olarak istenir; aksi halde hatırlatıcı bildirimleri görünmez.
        requestPostNotificationsIfNeeded()

        // Bottom nav dışındaki destinasyonlarda (KategoriDetay, Wallet) BottomNav gizle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Drawer gibi farklı entry-point'lerden gelince bottom seçimi bazen stale kalabiliyor.
            // Destinasyon bottom menüde varsa checked state'i zorla senkronla.
            val bottomIds = setOf(R.id.nav_ozet, R.id.nav_gunluk, R.id.nav_aylik, R.id.nav_plan)
            if (destination.id in bottomIds) {
                bottomNav.menu.findItem(destination.id)?.isChecked = true
            }

            if (destination.id !in appBarConfiguration.topLevelDestinations) {
                val back = AppCompatResources.getDrawable(this, R.drawable.ic_chevron_left)?.mutate()
                back?.setTint(navIconWhite)
                toolbar.navigationIcon = back
                toolbar.navigationContentDescription = getString(R.string.toolbar_cd_back)
            }
            // NavComponent hamburger/back ikonunu burada tekrar set edebiliyor; tint'i her seferinde garantile.
            toolbar.setNavigationIconTint(navIconWhite)
            (toolbar.navigationIcon as? DrawerArrowDrawable)?.color = navIconWhite
            toolbar.navigationIcon?.mutate()?.setTint(navIconWhite)
            val topLevelIds = setOf(
                R.id.nav_ozet, R.id.nav_gunluk, R.id.nav_aylik,
                R.id.nav_plan, R.id.nav_profil, R.id.nav_security
            )
            bottomNav.visibility = if (destination.id in topLevelIds)
                android.view.View.VISIBLE else android.view.View.GONE

            // Removed: onboarding coachmark/snackbar on app start (too confusing for first-time users).
        }
    }

    /** İlk açılışta günlük coachmark gösterilecekse bir kez true döner. */
    fun consumePendingDailyCoachmarkRequest(): Boolean {
        if (!pendingDailyCoachmark) return false
        pendingDailyCoachmark = false
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    fun showThemePicker() {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val modes = listOf(
            "LIGHT" to getString(R.string.theme_light),
            "DARK" to getString(R.string.theme_dark),
            "SYSTEM" to getString(R.string.theme_system)
        )
        val current = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        val selected = modes.indexOfFirst { it.first == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_theme)
            .setSingleChoiceItems(
                modes.map { it.second }.toTypedArray(),
                selected
            ) { d, which ->
                val mode = modes[which].first
                prefs.edit().putString("theme_mode", mode).apply()
                applyTheme(mode)
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { userProfileDao.updateThemeMode(mode) }
                }
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showLanguagePicker() {
        val options = arrayOf(
            getString(R.string.lang_turkish),
            getString(R.string.lang_english)
        )
        val checked = if (AuthPrefs.getAppLocaleTag(this) == AuthPrefs.LOCALE_EN) 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_language)
            .setSingleChoiceItems(options, checked) { d, which ->
                AuthPrefs.setAppLocaleTag(
                    this,
                    if (which == 1) AuthPrefs.LOCALE_EN else AuthPrefs.LOCALE_TR
                )
                d.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDrawerMenu()
    }

    private fun refreshDrawerMenu() {
        val navDrawer = findViewById<NavigationView>(R.id.nav_drawer)
        val menu = navDrawer.menu
        val method = AuthPrefs.getAuthMethod(this)
        val signedIn = method == AuthPrefs.AUTH_METHOD_GMAIL || method == AuthPrefs.AUTH_METHOD_LOCAL
        menu.findItem(R.id.drawer_sign_in)?.isVisible = method.isEmpty()
        menu.findItem(R.id.drawer_sign_out)?.isVisible = signedIn

        // Sign out satırı (ikon + yazı) kırmızı görünsün.
        menu.findItem(R.id.drawer_sign_out)?.let { item ->
            val red = ContextCompat.getColor(this, R.color.error)
            item.icon?.mutate()?.setTint(red)
            val title = item.title?.toString() ?: getString(R.string.drawer_sign_out)
            val s = SpannableString(title)
            s.setSpan(ForegroundColorSpan(red), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            item.title = s
        }
    }

    private fun openAppRating() {
        val pkg = packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        if (market.resolveActivity(packageManager) != null) startActivity(market) else startActivity(web)
    }

    private fun confirmAndSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_sign_out)
            .setMessage(R.string.sign_out_message)
            .setPositiveButton(R.string.drawer_sign_out) { _, _ ->
                lifecycleScope.launch {
                    val webId = getString(R.string.default_web_client_id).trim()
                    AuthSignOut.signOutGoogleIfNeeded(this@MainActivity, webId)
                    AuthPrefs.applyLogout(this@MainActivity)
                    startActivity(
                        Intent(this@MainActivity, WelcomeActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    )
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            rescheduleUnpaidReminderAlarms()
        }
    }

    /** Ödenmemiş hatırlatıcıların gelecek bildirimlerini yeniden kur (PIN sonrası / arka plandan dönüş). */
    private fun rescheduleUnpaidReminderAlarms() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val unpaid = reminderRepository.getUnpaidReminders().first()
                unpaid.forEach { ReminderScheduler.schedule(applicationContext, it) }
            }
        }
    }

    private fun pinGerekliMi(): Boolean {
        if (!AuthPrefs.shouldEnforceAppPinLock(this)) return false
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val sonGiris = prefs.getLong("son_giris_zamani", 0L)
        if (sonGiris == 0L) return true
        val window = AuthPrefs.getPinLockTimeoutMs(this)
        return (System.currentTimeMillis() - sonGiris) > window
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

    fun gosterYaklasan() {
        bottomNav().selectedItemId = R.id.nav_plan
        window.decorView.post { findPlanFragment()?.selectTab(0) }
    }

    fun gosterAylik() { bottomNav().selectedItemId = R.id.nav_aylik }

    fun gosterGunluk() { bottomNav().selectedItemId = R.id.nav_gunluk }

    fun gosterHedefler() {
        bottomNav().selectedItemId = R.id.nav_plan
        window.decorView.post { findPlanFragment()?.selectTab(1) }
    }

    private fun findPlanFragment(): PlanFragment? {
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return host?.childFragmentManager?.primaryNavigationFragment as? PlanFragment
    }

    private fun showDashboardModulesDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_drawer_dashboard_modules, null)
        bindDashboardModuleToggles(v)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_dashboard_modules)
            .setView(v)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    /** Özet ekranı kartlarının görünürlüğü — yalnızca diyalogdan (çekmece menüsü). */
    private fun bindDashboardModuleToggles(root: View) {
        val fx = root.findViewById<MaterialSwitch>(R.id.switch_drawer_dashboard_fx)
        val reminders = root.findViewById<MaterialSwitch>(R.id.switch_drawer_dashboard_reminders)
        val insights = root.findViewById<MaterialSwitch>(R.id.switch_drawer_dashboard_insights)
        val miniPie = root.findViewById<MaterialSwitch>(R.id.switch_drawer_dashboard_mini_pie)
        fun clearListeners() {
            fx.setOnCheckedChangeListener(null)
            reminders.setOnCheckedChangeListener(null)
            insights.setOnCheckedChangeListener(null)
            miniPie.setOnCheckedChangeListener(null)
        }
        clearListeners()
        fx.isChecked = AuthPrefs.isDashboardFxVisible(this)
        reminders.isChecked = AuthPrefs.isDashboardReminderSectionVisible(this)
        insights.isChecked = AuthPrefs.isDashboardInsightsVisible(this)
        miniPie.isChecked = AuthPrefs.isDashboardMiniPieSectionVisible(this)
        fx.setOnCheckedChangeListener { _, c ->
            AuthPrefs.setDashboardFxVisible(this, c)
            notifyDashboardFragmentPrefsChanged()
        }
        reminders.setOnCheckedChangeListener { _, c ->
            AuthPrefs.setDashboardReminderSectionVisible(this, c)
            notifyDashboardFragmentPrefsChanged()
        }
        insights.setOnCheckedChangeListener { _, c ->
            AuthPrefs.setDashboardInsightsVisible(this, c)
            notifyDashboardFragmentPrefsChanged()
        }
        miniPie.setOnCheckedChangeListener { _, c ->
            AuthPrefs.setDashboardMiniPieSectionVisible(this, c)
            notifyDashboardFragmentPrefsChanged()
        }
    }

    private fun notifyDashboardFragmentPrefsChanged() {
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val dash = host.childFragmentManager.primaryNavigationFragment as? DashboardFragment
        dash?.refreshDashboardModulePrefs()
    }
}
