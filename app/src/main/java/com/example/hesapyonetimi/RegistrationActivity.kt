package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.view.ViewTreeObserver
import android.util.TypedValue
import android.view.Gravity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.example.hesapyonetimi.util.AnalyticsHelper
import com.example.hesapyonetimi.util.LocaleHelper
import com.example.hesapyonetimi.util.PayPeriodLabelFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class RegistrationActivity : AppCompatActivity() {

    @Inject
    lateinit var userProfileDao: UserProfileDao

    private var currentStep = 1
    private var selectedQuestionIndex = 0
    private var regPin: String = ""
    private var savedName = ""
    private var savedAnswer = ""
    private var regPayUseCalendar: Boolean = true
    private var regSalaryDay: Int = 1
    private var salaryDialogYear: Int = 0
    private var salaryDialogMonth: Int = 0
    private var salaryPickedFromCalendar: Boolean = false
    private lateinit var securityQuestionTexts: List<String>
    private var googleSetup: Boolean = false
    private var googleEmail: String = ""
    private lateinit var googleClient: GoogleSignInClient
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    companion object {
        const val EXTRA_GOOGLE_SETUP = "extra_google_setup"
        const val EXTRA_GOOGLE_EMAIL = "extra_google_email"
        const val EXTRA_GOOGLE_DISPLAY_NAME = "extra_google_display_name"
    }

    private enum class FinishKind {
        PIN_DAILY,
        PIN_EXPLORE,
        NO_PIN_EXPLORE,
        NO_PIN_DAILY
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_registration)

        googleSetup = intent.getBooleanExtra(EXTRA_GOOGLE_SETUP, false)
        googleEmail = intent.getStringExtra(EXTRA_GOOGLE_EMAIL)?.trim().orEmpty()
        val googleDisplay = intent.getStringExtra(EXTRA_GOOGLE_DISPLAY_NAME)?.trim().orEmpty()

        val webId = getString(R.string.default_web_client_id).trim()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webId)
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        val dp16 = (16 * resources.displayMetrics.density).toInt()

        val header = findViewById<View>(R.id.regHeader)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(dp16 + dp16 / 2, top + dp16, dp16 + dp16 / 2, dp16 + dp16 / 2)
            insets
        }

        val bottomBar = findViewById<View>(R.id.bottomButtonBar)
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(dp16, dp16, dp16, navBottom + dp16)
            insets
        }

        securityQuestionTexts = listOf(
            getString(R.string.reg_security_q0),
            getString(R.string.reg_security_q1),
            getString(R.string.reg_security_q2),
            getString(R.string.reg_security_q3)
        )

        if (googleDisplay.isNotEmpty()) {
            findViewById<TextInputEditText>(R.id.etName).setText(googleDisplay)
        }

        setupSecurityQuestionDropdown()
        setupPayPeriodUi()
        setupPinChoiceUi()
        setupNavigation()
        setupAnswerField()
        setupPinField()
        setupKeyboardAutoScroll()
        installKeyboardVisibilityScroll()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentStep > 1) {
                        findViewById<TextInputEditText>(R.id.etPin).text = null
                        regPin = ""
                        showStep(1)
                    } else {
                        finish()
                    }
                }
            }
        )

        val now = Calendar.getInstance()
        salaryDialogYear = now.get(Calendar.YEAR)
        salaryDialogMonth = now.get(Calendar.MONTH)
        regSalaryDay = AuthPrefs.getSalaryDayOfMonth(this).coerceIn(1, 31)

        showStep(1)
    }

    override fun onDestroy() {
        val root = findViewById<View>(R.id.regRoot)
        keyboardLayoutListener?.let { listener ->
            root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        keyboardLayoutListener = null
        super.onDestroy()
    }

    /** Odaklanan alanın, kayıtlı ScrollView içeriğindeki dikey konumu için uygun ata (TIL veya alan). */
    private fun findFocusAnchorInRegScroll(focused: View): View {
        val scrollContent = findViewById<ScrollView>(R.id.regScrollView).getChildAt(0) ?: return focused
        var v: View? = focused
        while (v != null && v.parent is View) {
            val p = v.parent as View
            if (p === scrollContent) return v
            v = p
        }
        return focused
    }

    private fun installKeyboardVisibilityScroll() {
        val root = findViewById<View>(R.id.regRoot)
        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val focused = currentFocus ?: return@OnGlobalLayoutListener
            if (focused !is TextInputEditText && focused !is MaterialAutoCompleteTextView) {
                return@OnGlobalLayoutListener
            }
            scrollFieldIntoView(findFocusAnchorInRegScroll(focused))
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    private fun setupSecurityQuestionDropdown() {
        val act = findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, securityQuestionTexts)
        act.setAdapter(adapter)
        act.setText(securityQuestionTexts[0], false)
        selectedQuestionIndex = 0
        act.setOnItemClickListener { _, _, position, _ ->
            selectedQuestionIndex = position
            updateContinueActionsVisibility()
        }
    }

    private fun setupPayPeriodUi() {
        val rbFirst = findViewById<RadioButton>(R.id.rb_reg_pay_from_first)
        val btnSalary = findViewById<MaterialButton>(R.id.btn_reg_pick_salary_day)
        rbFirst.isChecked = true
        regPayUseCalendar = true
        salaryPickedFromCalendar = false
        updatePaySummaryUi()

        rbFirst.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                regPayUseCalendar = true
                salaryPickedFromCalendar = false
                updatePaySummaryUi()
            }
        }

        btnSalary.setOnClickListener {
            rbFirst.isChecked = false
            showSalaryDayGridDialog()
        }
    }

    private fun showSalaryDayGridDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_salary_day_grid, null, false)
        val grid = dialogView.findViewById<GridLayout>(R.id.grid_salary_days)
        val density = resources.displayMetrics.density
        val minCell = (40 * density).toInt()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reg_pay_pick_salary_day_button)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnDismissListener {
            if (!salaryPickedFromCalendar) {
                findViewById<RadioButton>(R.id.rb_reg_pay_from_first).isChecked = true
                regPayUseCalendar = true
                updatePaySummaryUi()
            }
        }

        val dayLabelColor = ContextCompat.getColor(this, R.color.text_primary)

        for (day in 1..31) {
            val row = (day - 1) / 7
            val col = (day - 1) % 7
            // MaterialButton outlined bazı cihaz/tema kombinasyonlarında diyalog içinde etiketi çizmiyor;
            // TextView + şekilli arka plan her zaman okunur.
            val pad = (6 * density).toInt()
            val cell = TextView(this).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(dayLabelColor)
                setBackgroundResource(R.drawable.bg_reg_salary_day)
                setPadding(pad, pad, pad, pad)
                minHeight = minCell
                minWidth = minCell
                isClickable = true
                isFocusable = true
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(col, 1f)
                rowSpec = GridLayout.spec(row)
                setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            }
            cell.setOnClickListener {
                val cal = Calendar.getInstance()
                salaryDialogYear = cal.get(Calendar.YEAR)
                salaryDialogMonth = cal.get(Calendar.MONTH)
                regSalaryDay = day
                regPayUseCalendar = false
                salaryPickedFromCalendar = true
                updatePaySummaryUi()
                dialog.dismiss()
            }
            grid.addView(cell, lp)
        }
        dialog.show()
    }

    private fun currentUiLocale(): Locale = resources.configuration.locales.get(0)

    private fun updatePaySummaryUi() {
        val tv = findViewById<TextView>(R.id.tv_reg_pay_period_summary)
        if (regPayUseCalendar && !salaryPickedFromCalendar) {
            tv.visibility = View.VISIBLE
            tv.text = getString(R.string.reg_pay_period_summary_calendar)
            return
        }
        if (salaryPickedFromCalendar && !regPayUseCalendar) {
            tv.visibility = View.VISIBLE
            val label = PayPeriodLabelFormatter.salaryWindowLabel(
                currentUiLocale(),
                salaryDialogYear,
                salaryDialogMonth,
                regSalaryDay
            )
            val parts = label.split(" → ", limit = 2)
            tv.text = if (parts.size == 2) {
                getString(R.string.reg_pay_period_summary_salary_range, parts[0].trim(), parts[1].trim())
            } else {
                label
            }
            return
        }
        tv.visibility = View.GONE
    }

    private fun setupPinChoiceUi() {
        val rg = findViewById<RadioGroup>(R.id.rg_pin_choice)
        val groupYes = findViewById<View>(R.id.group_pin_yes_setup)
        rg.setOnCheckedChangeListener { _, checkedId ->
            findViewById<TextInputEditText>(R.id.etPin).text = null
            regPin = ""
            when (checkedId) {
                R.id.rb_pin_yes -> {
                    groupYes.visibility = View.VISIBLE
                    findViewById<ScrollView>(R.id.regScrollView).post {
                        scrollFieldIntoView(findViewById(R.id.tilPin))
                    }
                }
                R.id.rb_pin_no -> {
                    groupYes.visibility = View.GONE
                }
            }
            updateContinueActionsVisibility()
        }
        findViewById<MaterialButton>(R.id.btn_reg_first_expense).setOnClickListener {
            when {
                isPinYesPath() -> finishRegistration(FinishKind.PIN_DAILY)
                else -> finishRegistration(FinishKind.NO_PIN_DAILY)
            }
        }
        findViewById<MaterialButton>(R.id.btn_reg_explore).setOnClickListener {
            when {
                isPinYesPath() -> finishRegistration(FinishKind.PIN_EXPLORE)
                else -> finishRegistration(FinishKind.NO_PIN_EXPLORE)
            }
        }
    }

    private fun updateContinueActionsVisibility() {
        val grp = findViewById<View>(R.id.group_continue_actions)
        val btnBack = findViewById<Button>(R.id.btnBack)
        regPin = findViewById<TextInputEditText>(R.id.etPin).text?.toString().orEmpty()
        val pinNo = findViewById<RadioButton>(R.id.rb_pin_no).isChecked
        val pinYes = findViewById<RadioButton>(R.id.rb_pin_yes).isChecked
        val actionsVisible = when {
            pinNo -> View.VISIBLE
            pinYes && isSecurityReady() && regPin.length == 4 -> View.VISIBLE
            else -> View.GONE
        }
        grp.visibility = actionsVisible

        // Step-2'de aynı anda 3 CTA görünmesin:
        // “İlk Harcama Ekle / Keşfet” görünürken alttaki “Geri” butonunu gizle.
        if (currentStep == 2) {
            btnBack.visibility = if (actionsVisible == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        findViewById<View>(R.id.step1Container).visibility = if (step == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.step2Container).visibility = if (step == 2) View.VISIBLE else View.GONE
        if (step == 2) {
            findViewById<ScrollView>(R.id.regScrollView).post {
                findViewById<ScrollView>(R.id.regScrollView).scrollTo(0, 0)
            }
        }
        updateStepIndicator(step)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnNext = findViewById<Button>(R.id.btnNext)
        btnBack.visibility = if (step == 1) View.GONE else View.VISIBLE
        if (step == 1) {
            btnNext.visibility = View.VISIBLE
            btnNext.text = getString(R.string.reg_next)
        } else {
            btnNext.visibility = View.GONE
        }
    }

    private fun updateStepIndicator(step: Int) {
        val d1 = findViewById<View>(R.id.dot1)
        val d2 = findViewById<View>(R.id.dot2)
        d1.alpha = if (step >= 1) 1.0f else 0.33f
        d2.alpha = if (step >= 2) 1.0f else 0.33f
        val t1 = getString(R.string.reg_step_profile)
        val t2 = getString(R.string.reg_step_security)
        findViewById<TextView>(R.id.tvStepTitle).text =
            getString(R.string.reg_step_title_format, step, if (step == 1) t1 else t2)
    }

    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNext).setOnClickListener { goNext() }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (currentStep > 1) showStep(1)
        }
    }

    private fun goNext() {
        when (currentStep) {
            1 -> {
                findViewById<TextInputLayout>(R.id.tilName).error = null
                val name = findViewById<TextInputEditText>(R.id.etName).text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    findViewById<TextInputLayout>(R.id.tilName).error = getString(R.string.reg_error_name_required)
                    return
                }
                if (!findViewById<RadioButton>(R.id.rb_reg_pay_from_first).isChecked && !salaryPickedFromCalendar) {
                    Toast.makeText(this, R.string.reg_pay_period_required, Toast.LENGTH_SHORT).show()
                    return
                }
                savedName = name
                if (findViewById<RadioButton>(R.id.rb_reg_pay_from_first).isChecked) {
                    regPayUseCalendar = true
                    salaryPickedFromCalendar = false
                }
                findViewById<RadioButton>(R.id.rb_pin_no).isChecked = true
                findViewById<View>(R.id.group_pin_yes_setup).visibility = View.GONE
                findViewById<TextInputEditText>(R.id.etPin).text = null
                regPin = ""
                updateContinueActionsVisibility()
                showStep(2)
            }
        }
    }

    private fun setupAnswerField() {
        val et = findViewById<TextInputEditText>(R.id.etAnswer)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                findViewById<TextInputLayout>(R.id.tilAnswer).error = null
                updateContinueActionsVisibility()
            }
        })
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(et.windowToken, 0)
                et.clearFocus()
                true
            } else false
        }
    }

    private fun setupPinField() {
        val et = findViewById<TextInputEditText>(R.id.etPin)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                regPin = s?.toString().orEmpty()
                findViewById<TextInputLayout>(R.id.tilPin).error = null
                updateContinueActionsVisibility()
            }
        })
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question).requestFocus()
                scrollFieldIntoView(findViewById(R.id.tilSecurityQuestion))
                true
            } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(et.windowToken, 0)
                et.clearFocus()
                true
            } else false
        }
    }

    private fun topRelativeToAncestor(view: View, ancestor: View): Int {
        var y = 0
        var v: View? = view
        while (v != null && v !== ancestor) {
            y += v.top
            v = v.parent as? View
        }
        return y
    }

    /**
     * Klavye açılınca odaklanan kutunun ScrollView içinde görünür kalması için kaydırır.
     * (requestRectangleOnScreen ScrollView ile güvenilir değil; içerik koordinatı kullanılır.)
     */
    private fun scrollFieldIntoView(anchor: View) {
        val scrollView = findViewById<ScrollView>(R.id.regScrollView)
        val content = scrollView.getChildAt(0) ?: return
        val slop = (56 * resources.displayMetrics.density).toInt()
        fun applyScroll() {
            val innerH = scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
            val topInContent = topRelativeToAncestor(anchor, content)
            val bottomInContent = topInContent + anchor.height
            val y = scrollView.scrollY
            val maxScroll = (content.height - innerH).coerceAtLeast(0)
            when {
                bottomInContent > y + innerH - slop -> {
                    val target = (bottomInContent + slop - innerH).coerceIn(0, maxScroll)
                    scrollView.smoothScrollTo(0, target)
                }
                topInContent < y + slop -> {
                    scrollView.smoothScrollTo(0, (topInContent - slop).coerceAtLeast(0))
                }
            }
        }
        scrollView.post { applyScroll() }
        scrollView.postDelayed({ applyScroll() }, 60)
        scrollView.postDelayed({ applyScroll() }, 200)
        scrollView.postDelayed({ applyScroll() }, 450)
    }

    private fun setupKeyboardAutoScroll() {
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etAnswer = findViewById<TextInputEditText>(R.id.etAnswer)
        val etPin = findViewById<TextInputEditText>(R.id.etPin)
        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilAnswer = findViewById<TextInputLayout>(R.id.tilAnswer)
        val tilPin = findViewById<TextInputLayout>(R.id.tilPin)
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(tilName)
        }
        etAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(tilAnswer)
        }
        etPin.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(tilPin)
        }
        findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question).setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(findViewById(R.id.tilSecurityQuestion))
        }
    }

    private fun isPinYesPath(): Boolean =
        findViewById<RadioButton>(R.id.rb_pin_yes).isChecked

    private fun isSecurityReady(): Boolean {
        val act = findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question)
        val q = act.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return false
        val idx = securityQuestionTexts.indexOf(q)
        if (idx >= 0) selectedQuestionIndex = idx
        val answer = findViewById<TextInputEditText>(R.id.etAnswer).text?.toString()?.trim().orEmpty()
        return answer.isNotBlank()
    }

    private fun cacheSecurityFromForm() {
        val answer = findViewById<TextInputEditText>(R.id.etAnswer).text?.toString()?.trim().orEmpty()
        savedAnswer = answer.lowercase()
        val act = findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question)
        val q = act.text?.toString()?.trim().orEmpty()
        val idx = securityQuestionTexts.indexOf(q)
        if (idx >= 0) selectedQuestionIndex = idx
    }

    private fun syncRegPinFromField() {
        regPin = findViewById<TextInputEditText>(R.id.etPin).text?.toString().orEmpty()
    }

    private fun finishRegistration(kind: FinishKind) {
        val wantsPin = when (kind) {
            FinishKind.PIN_DAILY, FinishKind.PIN_EXPLORE -> true
            else -> false
        }
        if (wantsPin) {
            if (!isSecurityReady()) {
                findViewById<TextInputLayout>(R.id.tilAnswer).error =
                    getString(R.string.reg_error_answer_required)
                return
            }
            syncRegPinFromField()
            if (regPin.length != 4) {
                findViewById<TextInputLayout>(R.id.tilPin).error = getString(R.string.reg_error_pin_length)
                Toast.makeText(this, R.string.reg_error_pin_length, Toast.LENGTH_SHORT).show()
                return
            }
            cacheSecurityFromForm()
            val q = findViewById<MaterialAutoCompleteTextView>(R.id.act_security_question).text?.toString()?.trim().orEmpty()
            if (securityQuestionTexts.indexOf(q) < 0) {
                Toast.makeText(this, R.string.reg_error_pick_question, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val openDaily = when (kind) {
            FinishKind.PIN_DAILY, FinishKind.NO_PIN_DAILY -> true
            else -> false
        }
        val showCoach = kind == FinishKind.NO_PIN_DAILY || kind == FinishKind.PIN_DAILY

        persistUserAndOpenMain(
            wantsPin = wantsPin,
            openDaily = openDaily,
            showDailyCoachmark = showCoach && openDaily
        )
    }

    private fun persistUserAndOpenMain(
        wantsPin: Boolean,
        openDaily: Boolean,
        showDailyCoachmark: Boolean
    ) {
        val name = savedName.ifBlank {
            findViewById<TextInputEditText>(R.id.etName).text?.toString()?.trim().orEmpty()
        }.ifBlank { "Kullanıcı" }

        if (wantsPin) {
            syncRegPinFromField()
        }

        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val ed = prefs.edit()
            .putBoolean("is_registered", true)
            .putLong("pin_lock_timeout_ms", AuthPrefs.DEFAULT_PIN_LOCK_TIMEOUT_MS)
            .putLong("son_giris_zamani", System.currentTimeMillis())
            .putString("user_display_name", name)
            .putBoolean("biometric_enabled", false)

        if (googleSetup) {
            ed.putString("auth_method", AuthPrefs.AUTH_METHOD_GMAIL)
            if (googleEmail.isNotEmpty()) ed.putString(AuthPrefs.KEY_LINKED_GOOGLE_EMAIL, googleEmail)
            else ed.remove(AuthPrefs.KEY_LINKED_GOOGLE_EMAIL)
        } else {
            ed.putString("auth_method", AuthPrefs.AUTH_METHOD_LOCAL)
            ed.remove(AuthPrefs.KEY_LINKED_GOOGLE_EMAIL)
        }

        if (wantsPin) {
            ed.putString("kullanici_pin", regPin)
                .putInt("security_question_index", selectedQuestionIndex)
                .putString("security_answer", savedAnswer.ifBlank {
                    findViewById<TextInputEditText>(R.id.etAnswer).text?.toString()?.trim()
                        ?.lowercase().orEmpty()
                })
                .putBoolean("pin_enabled", true)
        } else {
            ed.remove("kullanici_pin")
                .remove("security_answer")
                .remove("security_question_index")
                .putBoolean("pin_enabled", false)
        }
        ed.apply()

        if (regPayUseCalendar) {
            AuthPrefs.setPayPeriodMode(this, AuthPrefs.PAY_PERIOD_MODE_CALENDAR)
        } else {
            AuthPrefs.setPayPeriodMode(this, AuthPrefs.PAY_PERIOD_MODE_SALARY)
            AuthPrefs.setSalaryDayOfMonth(this, regSalaryDay)
        }

        val event = if (googleSetup) "onboarding_google_complete" else "onboarding_local_complete"
        AnalyticsHelper.logEvent(
            this,
            event,
            mapOf("pay_period" to if (regPayUseCalendar) "calendar" else "salary", "pin" to wantsPin.toString())
        )

        lifecycleScope.launch {
            val existing = userProfileDao.getProfileOnce()
            if (existing == null) {
                userProfileDao.upsertProfile(UserProfileEntity(displayName = name))
            } else {
                userProfileDao.updateName(name)
            }
            withContext(Dispatchers.IO) {
                runCatching { Tasks.await(googleClient.signOut()) }
            }
            val mainIntent = Intent(this@RegistrationActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(
                    MainActivity.EXTRA_START_TAB,
                    if (openDaily) MainActivity.TAB_GUNLUK else MainActivity.TAB_OZET
                )
                putExtra(MainActivity.EXTRA_SHOW_DAILY_COACHMARK, showDailyCoachmark && openDaily)
            }
            startActivity(mainIntent)
            finish()
        }
    }
}
