package com.example.hesapyonetimi

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment

class OzetFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(android.R.layout.simple_list_item_1, container, false)
    }
}
