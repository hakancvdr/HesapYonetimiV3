package com.example.hesapyonetimi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.hesapyonetimi.presentation.goals.GoalFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class PlanFragment : Fragment() {

    private var tabLayoutMediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_plan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tabs = view.findViewById<TabLayout>(R.id.plan_tabs)
        val pager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.plan_pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> YaklasanFragment()
                1 -> GoalFragment()
                else -> YaklasanFragment()
            }
        }
        tabLayoutMediator?.detach()
        tabLayoutMediator = TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.tab_upcoming)
                else -> getString(R.string.tab_goals)
            }
        }.also { it.attach() }

        val initial = arguments?.getInt(ARG_INITIAL_TAB, 0) ?: 0
        pager.setCurrentItem(initial.coerceIn(0, 1), false)
    }

    fun selectTab(index: Int) {
        view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.plan_pager)
            ?.setCurrentItem(index.coerceIn(0, 1), true)
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_INITIAL_TAB = "initialTab"
    }
}
