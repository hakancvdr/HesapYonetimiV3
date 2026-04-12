package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import com.example.hesapyonetimi.util.LocaleHelper
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import android.widget.ScrollView
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegistrationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LINK_ACCOUNT = "extra_link_account"
    }

    @Inject
    lateinit var userProfileDao: UserProfileDao

    private var currentStep = 1
    private var selectedQuestionIndex = 0
    private var regPin = ""
    private var savedName = ""
    private var savedAnswer = ""
    private lateinit var pinDots: Array<View>

    private val questions = listOf(
        "İlk evcil hayvanınızın adı nedir?",
        "Annenizin kız soyadı nedir?",
        "Doğduğunuz şehir neresidir?",
        "En sevdiğiniz film nedir?"
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // adjustNothing: pencere pan/resize olmaz (siyah boşluk / çift ölçüm yok). IME yüksekliği
        // yalnızca regRoot alt padding ile uygulanır; cevap alanı kaydırılabilir alanda kalır.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_registration)

        val dp16 = (16 * resources.displayMetrics.density).toInt()

        val header = findViewById<View>(R.id.regHeader)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(dp16 + dp16 / 2, top + dp16, dp16 + dp16 / 2, dp16 + dp16 / 2)
            insets
        }

        val regRoot = findViewById<View>(R.id.regRoot)
        ViewCompat.setOnApplyWindowInsetsListener(regRoot) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(0, 0, 0, imeBottom)
            insets
        }

        val bottomBar = findViewById<View>(R.id.bottomButtonBar)
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(dp16, dp16, dp16, navBottom + dp16)
            insets
        }

        pinDots = arrayOf(
            findViewById(R.id.pin_dot1), findViewById(R.id.pin_dot2),
            findViewById(R.id.pin_dot3), findViewById(R.id.pin_dot4)
        )

        setupNumpad()
        setupNavigation()
        setupAnswerField()
        setupKeyboardAutoScroll()
        showStep(1)
    }

    // ── Adım geçişleri ────────────────────────────────────────────────────────

    private fun showStep(step: Int) {
        currentStep = step
        val containers = listOf(
            R.id.step1Container, R.id.step2Container, R.id.step4Container
        )
        containers.forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i + 1 == step) View.VISIBLE else View.GONE
        }
        if (step == 2) {
            findViewById<ScrollView>(R.id.regScrollView).post {
                findViewById<ScrollView>(R.id.regScrollView).scrollTo(0, 0)
            }
        }
        updateStepIndicator(step)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnNext = findViewById<Button>(R.id.btnNext)
        btnBack.visibility = if (step == 1) View.GONE else View.VISIBLE

        when (step) {
            1 -> btnNext.text = "Başlayalım →"
            3 -> btnNext.visibility = View.GONE  // PIN adımında buton gizli (numpad ile ilerler)
            else -> { btnNext.text = "İleri →"; btnNext.visibility = View.VISIBLE }
        }
    }

    private fun updateStepIndicator(step: Int) {
        val dots = listOf(R.id.dot1, R.id.dot2, R.id.dot3)
        dots.forEachIndexed { i, id ->
            findViewById<View>(id).alpha = if (i + 1 <= step) 1.0f else 0.33f
        }
        val titles = listOf("Hoş Geldiniz", "Profiliniz", "PIN Oluştur")
        findViewById<TextView>(R.id.tvStepTitle).text = "Adım $step — ${titles[step - 1]}"
    }

    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNext).setOnClickListener { goNext() }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (currentStep > 1) showStep(currentStep - 1)
        }
    }

    private fun goNext() {
        when (currentStep) {
            1 -> showStep(2)
            2 -> {
                val name = findViewById<TextInputEditText>(R.id.etName).text?.toString()?.trim() ?: ""
                if (name.isBlank()) {
                    findViewById<TextInputLayout>(R.id.tilName).error = "Lütfen adınızı girin"
                    return
                }
                savedName = name   // view GONE olunca kaybolmayacak şekilde sakla
                val answer = findViewById<TextInputEditText>(R.id.etAnswer).text?.toString()?.trim() ?: ""
                if (answer.isBlank()) {
                    findViewById<TextInputLayout>(R.id.tilAnswer).error = "Lütfen cevap girin"
                    return
                }
                val rg = findViewById<RadioGroup>(R.id.radioQuestions)
                val checkedId = rg.checkedRadioButtonId
                selectedQuestionIndex = when (checkedId) {
                    R.id.rq0 -> 0
                    R.id.rq1 -> 1
                    R.id.rq2 -> 2
                    R.id.rq3 -> 3
                    else -> {
                        findViewById<TextInputLayout>(R.id.tilAnswer).error = null
                        Toast.makeText(this, "Lütfen listeden bir güvenlik sorusu seçin.", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                savedAnswer = answer   // step 4'e geçmeden önce sakla
                showStep(3)
            }
        }
    }

    // ── Cevap alanı klavye done ───────────────────────────────────────────────
    private fun setupAnswerField() {
        val et = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAnswer)
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(et.windowToken, 0)
                et.clearFocus()
                true
            } else false
        }
    }

    /** Klavye açıldığında tüm kutunun (TextInputLayout) görünmesi için kaydır. */
    private fun scrollFieldIntoView(anchor: View) {
        val scrollView = findViewById<ScrollView>(R.id.regScrollView)
        val padBottom = (56 * resources.displayMetrics.density).toInt()
        scrollView.post {
            val r = android.graphics.Rect()
            anchor.getDrawingRect(r)
            r.bottom += padBottom
            scrollView.requestChildRectangleOnScreen(anchor, r, false)
        }
    }

    private fun setupKeyboardAutoScroll() {
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etAnswer = findViewById<TextInputEditText>(R.id.etAnswer)
        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilAnswer = findViewById<TextInputLayout>(R.id.tilAnswer)

        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etAnswer.requestFocus()
                scrollFieldIntoView(tilAnswer)
                true
            } else false
        }

        etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(tilName)
        }
        etAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollFieldIntoView(tilAnswer)
        }
    }

    // ── PIN numpad ────────────────────────────────────────────────────────────

    private fun setupNumpad() {
        val numBtns = mapOf(
            R.id.reg_btn0 to "0", R.id.reg_btn1 to "1", R.id.reg_btn2 to "2",
            R.id.reg_btn3 to "3", R.id.reg_btn4 to "4", R.id.reg_btn5 to "5",
            R.id.reg_btn6 to "6", R.id.reg_btn7 to "7", R.id.reg_btn8 to "8",
            R.id.reg_btn9 to "9"
        )
        numBtns.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener { onPinDigit(digit) }
        }
        findViewById<ImageButton>(R.id.reg_btn_del).setOnClickListener { onPinDelete() }
    }

    private fun onPinDigit(digit: String) {
        if (regPin.length < 4) {
            regPin += digit
            updatePinDots()
            if (regPin.length == 4) completeRegistration()
        }
    }

    private fun onPinDelete() {
        if (regPin.isNotEmpty()) {
            regPin = regPin.dropLast(1)
            updatePinDots()
        }
    }

    private fun updatePinDots() {
        pinDots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i < regPin.length) R.drawable.pin_dot_on else R.drawable.pin_dot_off)
        }
    }

    // ── Kaydı tamamla ─────────────────────────────────────────────────────────

    private fun completeRegistration() {
        // savedName/savedAnswer; step geçişinde saklanan değerler (view GONE olduğunda güvenli)
        val name   = savedName.ifBlank { "Kullanıcı" }
        val answer = savedAnswer.lowercase()

        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("kullanici_pin", regPin)
            .putInt("security_question_index", selectedQuestionIndex)
            .putString("security_answer", answer)
            .putBoolean("is_registered", true)
            .putString("auth_method", AuthPrefs.AUTH_METHOD_LOCAL)
            .putBoolean("pin_enabled", true)
            .putLong("pin_lock_timeout_ms", AuthPrefs.DEFAULT_PIN_LOCK_TIMEOUT_MS)
            .putLong("son_giris_zamani", System.currentTimeMillis())
            // SharedPreferences'a da kaydet (Room yavaş gelirse fallback)
            .putString("user_display_name", name)
            .apply()

        lifecycleScope.launch {
            // Var olan profili güncelle, yoksa oluştur
            val existing = userProfileDao.getProfileOnce()
            if (existing == null) {
                userProfileDao.upsertProfile(UserProfileEntity(displayName = name))
            } else {
                userProfileDao.updateName(name)
            }
            startActivity(Intent(this@RegistrationActivity, MainActivity::class.java))
            finish()
        }
    }
}
