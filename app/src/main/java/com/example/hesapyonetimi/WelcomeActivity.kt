package com.example.hesapyonetimi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hesapyonetimi.auth.AuthPrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import com.example.hesapyonetimi.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WelcomeActivity : AppCompatActivity() {

    private lateinit var googleClient: GoogleSignInClient

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

        if (AuthPrefs.prefs(this).getBoolean("is_registered", false)) {
            startActivity(Intent(this, PinActivity::class.java))
            finish()
            return
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
            startActivity(Intent(this, RegistrationActivity::class.java))
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
        val email = account.email?.trim().orEmpty()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { Tasks.await(googleClient.signOut()) }
            }
            startActivity(Intent(this@WelcomeActivity, RegistrationActivity::class.java).apply {
                putExtra(RegistrationActivity.EXTRA_GOOGLE_SETUP, true)
                putExtra(RegistrationActivity.EXTRA_GOOGLE_DISPLAY_NAME, display)
                putExtra(RegistrationActivity.EXTRA_GOOGLE_EMAIL, email)
            })
        }
    }
}
