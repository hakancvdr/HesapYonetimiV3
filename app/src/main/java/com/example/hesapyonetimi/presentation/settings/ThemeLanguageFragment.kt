package com.example.hesapyonetimi.presentation.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.R

class ThemeLanguageFragment : Fragment(R.layout.fragment_theme_language) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.row_theme).setOnClickListener {
            (activity as? MainActivity)?.showThemePicker()
        }
        view.findViewById<View>(R.id.row_language).setOnClickListener {
            (activity as? MainActivity)?.showLanguagePicker()
        }
    }
}

