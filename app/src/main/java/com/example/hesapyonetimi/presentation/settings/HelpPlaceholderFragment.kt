package com.example.hesapyonetimi.presentation.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.hesapyonetimi.R

class HelpPlaceholderFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<android.widget.TextView>(R.id.placeholder_title).setText(R.string.drawer_menu_help)
        view.findViewById<android.widget.TextView>(R.id.placeholder_body).setText(R.string.drawer_placeholder_soon)
    }
}

