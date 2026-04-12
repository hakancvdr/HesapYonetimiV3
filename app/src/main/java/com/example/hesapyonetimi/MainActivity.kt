package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.hesapyonetimi.BuildConfig
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.auth.AuthSignOut
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.domain.repository.ReminderRepository
import com.example.hesapyonetimi.util.LocaleHelper
import com.example.hesapyonetimi.presentation.profile.ProfileCategoriesDialogFragment
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
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
        drawerHeader.findViewById<TextView>(R.id.drawer_header_version).text =
            "v${BuildConfig.VERSION_NAME}"
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userProfileDao.getProfile().collect { profile ->
                    val tvProfile = drawerHeader.findViewById<TextView>(R.id.drawer_header_profile)
                    val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
                    val name = profile?.displayName
                        ?.takeIf { it.isNotBlank() && it != "Kullanıcı" }
                        ?: prefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() }
                        ?: "Kullanıcı"
                    tvProfile.text = name
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

        navDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_profile -> {
                    val opts = NavOptions.Builder().setLaunchSingleTop(true).build()
                    navController.navigate(R.id.nav_profil, null, opts)
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_categories -> {
                    ProfileCategoriesDialogFragment.newInstance()
                        .show(supportFragmentManager, "ProfileCategoriesDialogFragment")
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_pro -> {
                    navController.navigate(R.id.proFragment)
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
                R.id.drawer_dashboard_modules -> {
                    drawerLayout.closeDrawers()
                    showDashboardModulesDialog()
                }
                R.id.drawer_theme -> {
                    showThemePicker()
                    drawerLayout.closeDrawers()
                }
                R.id.drawer_language -> {
                    showLanguagePicker()
                    drawerLayout.closeDrawers()
                }
            }
            true
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            navController.popBackStack(R.id.nav_profil, true)
            NavigationUI.onNavDestinationSelected(item, navController)
        }

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
            if (destination.id !in appBarConfiguration.topLevelDestinations) {
                val back = AppCompatResources.getDrawable(this, R.drawable.ic_chevron_left)?.mutate()
                back?.setTint(ContextCompat.getColor(this, android.R.color.white))
                toolbar.navigationIcon = back
                toolbar.navigationContentDescription = getString(R.string.toolbar_cd_back)
            }
            val topLevelIds = setOf(
                R.id.nav_ozet, R.id.nav_gunluk, R.id.nav_aylik,
                R.id.nav_plan, R.id.nav_profil, R.id.nav_security
            )
            bottomNav.visibility = if (destination.id in topLevelIds)
                android.view.View.VISIBLE else android.view.View.GONE

            if (destination.id == R.id.nav_ozet && !AuthPrefs.isDashboardSummaryCoachmarkShown(this)) {
                bottomNav.post {
                    Snackbar.make(
                        bottomNav,
                        getString(R.string.coachmark_dashboard_summary),
                        Snackbar.LENGTH_LONG
                    )
                        .setAnchorView(bottomNav)
                        .setAction(R.string.close) {
                            AuthPrefs.setDashboardSummaryCoachmarkShown(this, true)
                        }
                        .addCallback(object : Snackbar.Callback() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    AuthPrefs.setDashboardSummaryCoachmarkShown(this@MainActivity, true)
                                }
                            }
                        })
                        .show()
                }
            }
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

    private fun showThemePicker() {
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

    private fun showLanguagePicker() {
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
