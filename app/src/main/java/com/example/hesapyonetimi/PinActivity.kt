package com.example.hesapyonetimi

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var girilenSifre = ""
    private lateinit var dots: Array<View>
    private var kayitliPin: String? = null

    private val sorular = listOf(
        "İlk evcil hayvanınızın adı nedir?",
        "Annenizin kız soyadı nedir?",
        "Doğduğunuz şehir neresidir?",
        "En sevdiğiniz film nedir?"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)

        // İlk kez kullanıyorsa → kurulum seçimi
        val isRegistered = prefs.getBoolean("is_registered", false)
        if (!isRegistered) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // PIN kapalıysa veya süre dolmadıysa ana ekran
        if (!AuthPrefs.shouldEnforceAppPinLock(this)) {
            prefs.edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val sonGiris = prefs.getLong("son_giris_zamani", 0L)
        val lockWindow = AuthPrefs.getPinLockTimeoutMs(this)
        if (System.currentTimeMillis() - sonGiris < lockWindow) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_pin)

        // Root layout'a navigasyon barı padding'i uygula
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        dots = arrayOf(
            findViewById(R.id.dot1), findViewById(R.id.dot2),
            findViewById(R.id.dot3), findViewById(R.id.dot4)
        )

        kayitliPin = prefs.getString("kullanici_pin", null)
        val tvBaslik = findViewById<TextView>(R.id.tv_pin_baslik)
        tvBaslik.text = if (kayitliPin == null) "Yeni PIN Belirleyin" else "PIN Girin"

        // "Şifremi Unuttum" butonu
        val tvForgot = findViewById<TextView>(R.id.tv_forgot_pin)
        val canRecovery = AuthPrefs.hasSecurityRecovery(this)
        if (kayitliPin != null && canRecovery) {
            tvForgot?.visibility = View.VISIBLE
            tvForgot?.setOnClickListener { showForgotPinDialog() }
        } else {
            tvForgot?.visibility = View.GONE
        }

        // Biyometrik giriş (PIN kayıtlıysa ve cihaz destekliyorsa)
        if (kayitliPin != null) {
            tryBiometricLogin()
        }
    }

    private fun tryBiometricLogin() {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("biometric_enabled", false)) return

        val bioMgr = BiometricManager.from(this)
        if (bioMgr.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            != BiometricManager.BIOMETRIC_SUCCESS) return

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                oturumuBaslat()
            }
        }
        val prompt = BiometricPrompt(this, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biyometrik Giriş")
            .setSubtitle("Hesap Yönetimi'ne giriş yapmak için dokunun")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        if (AuthPrefs.shouldEnforceAppPinLock(this) && prefs.getString("kullanici_pin", null) != null) {
            prefs.edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
        }
    }

    fun onNumberClick(view: View) {
        if (girilenSifre.length < 4) {
            girilenSifre += (view as Button).text.toString()
            updateDots()
            if (girilenSifre.length == 4) onaylamaIslemi()
        }
    }

    fun onDeleteClick(view: View) {
        if (girilenSifre.isNotEmpty()) {
            girilenSifre = girilenSifre.dropLast(1)
            updateDots()
        }
    }

    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i < girilenSifre.length) R.drawable.pin_dot_on else R.drawable.pin_dot_off)
        }
    }

    private fun onaylamaIslemi() {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        when {
            kayitliPin == null -> {
                prefs.edit().putString("kullanici_pin", girilenSifre).apply()
                Toast.makeText(this, "PIN Kaydedildi!", Toast.LENGTH_SHORT).show()
                oturumuBaslat()
            }
            girilenSifre == kayitliPin -> oturumuBaslat()
            else -> {
                Toast.makeText(this, "Hatalı PIN!", Toast.LENGTH_SHORT).show()
                girilenSifre = ""
                updateDots()
            }
        }
    }

    private fun oturumuBaslat() {
        getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
            .edit().putLong("son_giris_zamani", System.currentTimeMillis()).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── PIN sıfırlama ─────────────────────────────────────────────────────────

    private fun showForgotPinDialog() {
        val prefs = getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
        val soruIndex = prefs.getInt("security_question_index", 0)
        val kayitliCevap = prefs.getString("security_answer", "") ?: ""

        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_pin, null)
        val tvSoru   = dialogView.findViewById<TextView>(R.id.tvSecurityQuestion)
        val etCevap  = dialogView.findViewById<TextInputEditText>(R.id.etSecurityAnswer)
        val tilCevap = dialogView.findViewById<TextInputLayout>(R.id.tilSecurityAnswer)

        tvSoru.text = sorular.getOrElse(soruIndex) { sorular[0] }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Doğrula", null)
            .setNegativeButton("İptal", null)
            .create()

        // Done tuşu klavyeyi kapatıp doğrulama yapar
        etCevap.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(etCevap.windowToken, 0)
                true
            } else false
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val girilen = etCevap.text?.toString()?.trim()?.lowercase() ?: ""
                if (girilen == kayitliCevap) {
                    dialog.dismiss()
                    showSetNewPinDialog()
                } else {
                    tilCevap.error = "Cevap yanlış"
                    Toast.makeText(this, "Güvenlik sorusu cevabı yanlış.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun showSetNewPinDialog() {
        var yeniPin = ""
        val dots4 = arrayOfNulls<View>(4)

        val dialogView = layoutInflater.inflate(R.layout.dialog_set_new_pin, null)
        for (i in 0..3) dots4[i] = dialogView.findViewById<View>(
            resources.getIdentifier("new_dot${i + 1}", "id", packageName)
        )

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        fun updateNewDots() {
            dots4.forEachIndexed { i, dot ->
                dot?.setBackgroundResource(
                    if (i < yeniPin.length) R.drawable.pin_dot_on else R.drawable.pin_dot_off
                )
            }
        }

        val numIds = listOf(
            R.id.new_btn0, R.id.new_btn1, R.id.new_btn2, R.id.new_btn3,
            R.id.new_btn4, R.id.new_btn5, R.id.new_btn6, R.id.new_btn7,
            R.id.new_btn8, R.id.new_btn9
        )
        numIds.forEachIndexed { _, id ->
            dialogView.findViewById<Button>(id)?.setOnClickListener { v ->
                val digit = (v as Button).text.toString()
                if (yeniPin.length < 4) {
                    yeniPin += digit
                    updateNewDots()
                    if (yeniPin.length == 4) {
                        getSharedPreferences("HesapPrefs", Context.MODE_PRIVATE)
                            .edit().putString("kullanici_pin", yeniPin).apply()
                        dialog.dismiss()
                        Toast.makeText(this, "✅ Yeni PIN kaydedildi!", Toast.LENGTH_SHORT).show()
                        kayitliPin = yeniPin
                        girilenSifre = ""
                        updateDots()
                    }
                }
            }
        }
        dialogView.findViewById<ImageButton>(R.id.new_btn_del)?.setOnClickListener {
            if (yeniPin.isNotEmpty()) { yeniPin = yeniPin.dropLast(1); updateNewDots() }
        }
        dialog.show()
    }
}
