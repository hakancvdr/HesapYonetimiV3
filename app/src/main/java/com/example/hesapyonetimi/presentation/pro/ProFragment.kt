package com.example.hesapyonetimi.presentation.pro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.auth.AuthPrefs
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_pro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sw = view.findViewById<MaterialSwitch>(R.id.switch_pro_demo)
        sw?.isChecked = AuthPrefs.isProMember(requireContext())
        sw?.setOnCheckedChangeListener { _, checked ->
            AuthPrefs.setProMember(requireContext(), checked)
        }
    }
}

