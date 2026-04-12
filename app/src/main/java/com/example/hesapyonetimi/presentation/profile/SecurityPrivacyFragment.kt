package com.example.hesapyonetimi.presentation.profile

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SecurityPrivacyFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var switchPinLock: MaterialSwitch
    private lateinit var cardPinLockTimeout: View
    private lateinit var tvPinLockTimeoutSubtitle: TextView
    private lateinit var cardSecurityQ: View
    private lateinit var tvSecurityQSubtitle: TextView
    private lateinit var switchAnonymousAnalytics: MaterialSwitch

    private var suppressPinSwitchCallback = false
    private var suppressAnalyticsSwitch = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_security_privacy, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        switchPinLock = view.findViewById(R.id.switchPinLock)
        cardPinLockTimeout = view.findViewById(R.id.cardPinLockTimeout)
        tvPinLockTimeoutSubtitle = view.findViewById(R.id.tvPinLockTimeoutSubtitle)
        cardSecurityQ = view.findViewById(R.id.cardSecurityQ)
        tvSecurityQSubtitle = view.findViewById(R.id.tvSecurityQSubtitle)
        switchAnonymousAnalytics = view.findViewById(R.id.switchAnonymousAnalytics)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.security_scroll)) { v, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            val extra = (12 * resources.displayMetrics.density).toInt()
            v.updatePadding(bottom = bottom + extra)
            insets
        }

        cardPinLockTimeout.setOnClickListener { showPinLockTimeoutDialog() }
        view.findViewById<View>(R.id.cardChangePin).setOnClickListener { showChangePinDialog() }
        view.findViewById<View>(R.id.cardSecurityQ).setOnClickListener { showChangeSecurityQDialog() }
        view.findViewById<View>(R.id.cardResetSettings).setOnClickListener { showResetSettingsDialog() }

        suppressAnalyticsSwitch = true
        switchAnonymousAnalytics.isChecked = AuthPrefs.isAnonymousAnalyticsEnabled(requireContext())
        suppressAnalyticsSwitch = false
        switchAnonymousAnalytics.setOnCheckedChangeListener { _, checked ->
            if (suppressAnalyticsSwitch) return@setOnCheckedChangeListener
            AuthPrefs.setAnonymousAnalyticsEnabled(requireContext(), checked)
        }

        setupPinLockSwitchListener()
        refreshPinSecurityVisibility()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiEvent.collectLatest { event ->
                        when (event) {
                            is ProfileUiEvent.ThemeChanged ->
                                (activity as? MainActivity)?.applyTheme(event.mode)
                            is ProfileUiEvent.ShowMessage -> showSnack(event.message)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPinSecurityVisibility()
        suppressAnalyticsSwitch = true
        switchAnonymousAnalytics.isChecked = AuthPrefs.isAnonymousAnalyticsEnabled(requireContext())
        suppressAnalyticsSwitch = false
    }

    private fun setupPinLockSwitchListener() {
        switchPinLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressPinSwitchCallback) return@setOnCheckedChangeListener
            val ctx = requireContext()
            val prefs = AuthPrefs.prefs(ctx)
            if (isChecked) {
                val pin = prefs.getString("kullanici_pin", null)
                if (pin.isNullOrBlank() || pin.length != 4) {
                    suppressPinSwitchCallback = true
                    switchPinLock.isChecked = false
                    suppressPinSwitchCallback = false
                    showChangePinDialog {
                        AuthPrefs.setPinEnabled(ctx, true)
                        suppressPinSwitchCallback = true
                        switchPinLock.isChecked = true
                        suppressPinSwitchCallback = false
                        refreshPinSecurityVisibility()
                    }
                } else {
                    AuthPrefs.setPinEnabled(ctx, true)
                    cardPinLockTimeout.visibility = View.VISIBLE
                }
            } else {
                AuthPrefs.setPinEnabled(ctx, false)
                prefs.edit()
                    .remove("kullanici_pin")
                    .putBoolean("biometric_enabled", false)
                    .apply()
                cardPinLockTimeout.visibility = View.GONE
            }
        }
    }

    private fun refreshPinSecurityVisibility() {
        if (!isAdded) return
        val ctx = requireContext()
        suppressPinSwitchCallback = true
        switchPinLock.isChecked = AuthPrefs.isPinEnabled(ctx)
        suppressPinSwitchCallback = false
        tvPinLockTimeoutSubtitle.text = AuthPrefs.labelForTimeoutMs(AuthPrefs.getPinLockTimeoutMs(ctx))
        cardPinLockTimeout.visibility =
            if (AuthPrefs.isPinEnabled(ctx)) View.VISIBLE else View.GONE
        cardSecurityQ.visibility =
            if (AuthPrefs.hasSecurityRecovery(ctx)) View.VISIBLE else View.GONE
    }

    private fun showPinLockTimeoutDialog() {
        if (!AuthPrefs.isPinEnabled(requireContext())) return
        val msChoices = AuthPrefs.PIN_TIMEOUT_CHOICES_MS
        val labels = msChoices.map { AuthPrefs.labelForTimeoutMs(it) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_pin_timeout_title)
            .setItems(labels) { _, which ->
                AuthPrefs.setPinLockTimeoutMs(requireContext(), msChoices[which])
                tvPinLockTimeoutSubtitle.text = labels[which]
            }
            .show()
    }

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_reset_settings_title)
            .setMessage("PIN kaldırılır, biyometrik kapatılır, tema sistem varsayılana ve aylık bütçe limiti sıfırlanır.")
            .setPositiveButton("Sıfırla") { _, _ -> viewModel.resetAppSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangePinDialog(onPinSaved: (() -> Unit)? = null) {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val currentPin = prefs.getString("kullanici_pin", null)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_pin, null)
        val etCurrent = dialogView.findViewById<TextInputEditText>(R.id.etCurrentPin)
        val tilCurrent = dialogView.findViewById<TextInputLayout>(R.id.tilCurrentPin)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.etNewPin)
        val tilNew = dialogView.findViewById<TextInputLayout>(R.id.tilNewPin)
        val etConfirm = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPin)
        val tilConfirm = dialogView.findViewById<TextInputLayout>(R.id.tilConfirmPin)

        if (currentPin == null) tilCurrent.visibility = View.GONE

        val dialog = buildDialog(R.layout.dialog_change_pin)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogView.findViewById<View>(R.id.btnCancel)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSavePin)?.setOnClickListener {
            val current = etCurrent.text?.toString()?.trim() ?: ""
            val new1 = etNew.text?.toString()?.trim() ?: ""
            val new2 = etConfirm.text?.toString()?.trim() ?: ""

            if (currentPin != null && current != currentPin) {
                tilCurrent.error = "Mevcut PIN yanlış"
                return@setOnClickListener
            }
            if (new1.length != 4 || new1.any { !it.isDigit() }) {
                tilNew.error = "PIN 4 rakam olmalı"
                return@setOnClickListener
            }
            if (new1 != new2) {
                tilConfirm.error = "PIN'ler eşleşmiyor"
                return@setOnClickListener
            }
            prefs.edit()
                .putString("kullanici_pin", new1)
                .putBoolean("pin_enabled", true)
                .apply()
            showSnack("✅ PIN güncellendi")
            dialog.dismiss()
            onPinSaved?.invoke()
        }
        dialog.show()
    }

    private fun showChangeSecurityQDialog() {
        val prefs = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
        val sorular = listOf(
            "İlk evcil hayvanınızın adı nedir?",
            "Annenizin kız soyadı nedir?",
            "Doğduğunuz şehir neresidir?",
            "En sevdiğiniz film nedir?"
        )
        val currentIdx = prefs.getInt("security_question_index", 0)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_security_q, null)

        val qButtons = listOf(
            dialogView.findViewById<TextView>(R.id.btnQ0),
            dialogView.findViewById<TextView>(R.id.btnQ1),
            dialogView.findViewById<TextView>(R.id.btnQ2),
            dialogView.findViewById<TextView>(R.id.btnQ3)
        )
        var selectedIdx = currentIdx
        fun refreshQButtons() {
            qButtons.forEachIndexed { i, btn ->
                btn.setBackgroundResource(if (i == selectedIdx) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
                btn.setTextColor(resources.getColor(if (i == selectedIdx) R.color.green_primary else R.color.text_secondary, null))
            }
        }
        qButtons.forEachIndexed { i, btn ->
            btn.text = sorular[i]
            btn.setOnClickListener { selectedIdx = i; refreshQButtons() }
        }
        refreshQButtons()

        val etAnswer = dialogView.findViewById<TextInputEditText>(R.id.etNewSecurityAnswer)
        val tilAnswer = dialogView.findViewById<TextInputLayout>(R.id.tilNewSecurityAnswer)

        val dialog = buildDialog(R.layout.dialog_change_security_q)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogView.findViewById<View>(R.id.btnCancelSecQ)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSaveSecQ)?.setOnClickListener {
            val answer = etAnswer.text?.toString()?.trim() ?: ""
            if (answer.isBlank()) {
                tilAnswer.error = "Cevap boş olamaz"
                return@setOnClickListener
            }
            prefs.edit()
                .putInt("security_question_index", selectedIdx)
                .putString("security_answer", answer.lowercase())
                .apply()
            showSnack("✅ Güvenlik sorusu güncellendi")
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun buildDialog(layoutRes: Int, widthRatio: Double = 0.90): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(layoutRes)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * widthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }
}
