package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hesapyonetimi.auth.AuthPrefs
import com.example.hesapyonetimi.data.local.dao.UserProfileDao
import com.example.hesapyonetimi.data.local.entity.UserProfileEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import com.example.hesapyonetimi.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LINK_ACCOUNT = "extra_link_account"
    }

    @Inject
    lateinit var userProfileDao: UserProfileDao

    private lateinit var googleClient: GoogleSignInClient

    private val linkAccount: Boolean
        get() = intent.getBooleanExtra(EXTRA_LINK_ACCOUNT, false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                onGoogleSignedIn(account)
            } catch (_: ApiException) {
                Toast.makeText(this, "Google ile giriş tamamlanmadı.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!linkAccount && AuthPrefs.prefs(this).getBoolean("is_registered", false)) {
            startActivity(Intent(this, PinActivity::class.java))
            finish()
            return
        }

        if (linkAccount) {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        finish()
                    }
                }
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_welcome)

        val webId = getString(R.string.default_web_client_id).trim()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webId)
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.welcomeRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.btnWelcomeGoogle).setOnClickListener {
            if (!isWebClientConfigured(webId)) {
                Toast.makeText(
                    this,
                    "Google Web İstemci Kimliğini strings.xml → default_web_client_id olarak girin.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            googleLauncher.launch(googleClient.signInIntent)
        }
        findViewById<View>(R.id.btnWelcomeQuick).setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java).apply {
                if (linkAccount) putExtra(RegistrationActivity.EXTRA_LINK_ACCOUNT, true)
            })
            finish()
        }
        val guestBtn = findViewById<View>(R.id.btnWelcomeGuest)
        if (linkAccount) {
            guestBtn.visibility = View.GONE
        } else {
            guestBtn.setOnClickListener { completeGuestAndOpenMain() }
        }
    }

    private fun isWebClientConfigured(webId: String): Boolean {
        if (webId.isBlank()) return false
        val lower = webId.lowercase()
        if (!lower.endsWith(".apps.googleusercontent.com")) return false
        if (lower.contains("your_") || lower.contains("replace")) return false
        return true
    }

    private fun onGoogleSignedIn(account: GoogleSignInAccount) {
        val display = account.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: account.email?.substringBefore("@")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Kullanıcı"

        lifecycleScope.launch {
            val prefs = AuthPrefs.prefs(this@WelcomeActivity)
            val email = account.email?.trim().orEmpty()
            prefs.edit()
                .putBoolean("is_registered", true)
                .putString("auth_method", AuthPrefs.AUTH_METHOD_GMAIL)
                .putBoolean("pin_enabled", false)
                .remove("kullanici_pin")
                .remove("security_answer")
                .remove("security_question_index")
                .putLong("pin_lock_timeout_ms", AuthPrefs.DEFAULT_PIN_LOCK_TIMEOUT_MS)
                .putLong("son_giris_zamani", System.currentTimeMillis())
                .putString("user_display_name", display)
                .putBoolean("biometric_enabled", false)
                .apply {
                    if (email.isNotEmpty()) putString(AuthPrefs.KEY_LINKED_GOOGLE_EMAIL, email)
                    else remove(AuthPrefs.KEY_LINKED_GOOGLE_EMAIL)
                }
                .apply()

            val existing = userProfileDao.getProfileOnce()
            if (existing == null) {
                userProfileDao.upsertProfile(UserProfileEntity(displayName = display))
            } else {
                userProfileDao.updateName(display)
            }

            withContext(Dispatchers.IO) {
                runCatching { Tasks.await(googleClient.signOut()) }
            }
            openMainAndFinish()
        }
    }

    private fun completeGuestAndOpenMain() {
        lifecycleScope.launch {
            AuthPrefs.prefs(this@WelcomeActivity).edit()
                .putBoolean("is_registered", true)
                .putString("auth_method", AuthPrefs.AUTH_METHOD_GUEST)
                .putBoolean("pin_enabled", false)
                .remove("kullanici_pin")
                .remove("security_answer")
                .remove("security_question_index")
                .putLong("pin_lock_timeout_ms", AuthPrefs.DEFAULT_PIN_LOCK_TIMEOUT_MS)
                .putLong("son_giris_zamani", System.currentTimeMillis())
                .putString("user_display_name", "Kullanıcı")
                .putBoolean("biometric_enabled", false)
                .apply()

            val existing = userProfileDao.getProfileOnce()
            if (existing == null) {
                userProfileDao.upsertProfile(UserProfileEntity(displayName = "Kullanıcı"))
            } else {
                userProfileDao.updateName("Kullanıcı")
            }
            openMainAndFinish()
        }
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
