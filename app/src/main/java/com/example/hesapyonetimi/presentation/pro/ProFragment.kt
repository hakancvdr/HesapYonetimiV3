package com.example.hesapyonetimi.presentation.pro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hesapyonetimi.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_pro, container, false)
}

