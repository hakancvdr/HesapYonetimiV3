package com.example.hesapyonetimi.presentation.goals

import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hesapyonetimi.MainActivity
import com.example.hesapyonetimi.R
import com.example.hesapyonetimi.ui.IconPickerHelper
import com.example.hesapyonetimi.ui.EmojiPickerSheet
import com.example.hesapyonetimi.data.local.entity.GoalEntity
import com.example.hesapyonetimi.presentation.common.CurrencyFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class GoalSort { DEADLINE, PROGRESS }

@AndroidEntryPoint
class GoalFragment : Fragment() {

    private val viewModel: GoalViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("tr"))
    private var goalSort = GoalSort.DEADLINE
    private lateinit var goalAdapter: GoalAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_goals, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.goals_header)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }
            v.setPadding(dp(20), sb + dp(12), dp(20), dp(16))
            insets
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvGoals)
        val emptyView = view.findViewById<View>(R.id.emptyGoalsView)
        rv.layoutManager = LinearLayoutManager(requireContext())

        goalAdapter = GoalAdapter(
            onContribution = { goal -> showContributionDialog(goal) },
            onRowClick = { goal ->
                GoalHistorySheet.show(childFragmentManager, goal.id, "${goal.icon} ${goal.title}")
            },
            onEdit = { goal ->
                showEditGoalDialog(goal)
            }
        )
        rv.adapter = goalAdapter
        setupGoalSwipe(view, rv)

        view.findViewById<View>(R.id.fabAddGoal).setOnClickListener { showAddGoalDialog() }
        view.findViewById<FloatingActionButton>(R.id.fabEmptyAddGoal)?.setOnClickListener { showAddGoalDialog() }

        view.findViewById<TextView>(R.id.btnSortDeadline).setOnClickListener { setGoalSort(view, GoalSort.DEADLINE) }
        view.findViewById<TextView>(R.id.btnSortProgress).setOnClickListener { setGoalSort(view, GoalSort.PROGRESS) }

        view.findViewById<android.widget.TextView>(R.id.iv_profile_goals)?.apply {
            val name = requireContext().getSharedPreferences("HesapPrefs", android.content.Context.MODE_PRIVATE)
                .getString("user_display_name", "K") ?: "K"
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "K"
            setOnClickListener { (activity as? MainActivity)?.gosterProfil() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.goals.collect { goals ->
                    updateGoalsHeader(view, goals)
                    if (goals.isEmpty()) {
                        rv.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    } else {
                        rv.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                        goalAdapter.submit(sortedGoals(goals))
                    }
                }
            }
        }
    }

    private fun setGoalSort(view: View, sort: GoalSort) {
        goalSort = sort
        val d = view.findViewById<TextView>(R.id.btnSortDeadline)
        val p = view.findViewById<TextView>(R.id.btnSortProgress)
        val grn = ContextCompat.getColor(requireContext(), R.color.green_primary)
        val sec = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val selD = sort == GoalSort.DEADLINE
        d.setBackgroundResource(if (selD) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
        d.setTextColor(if (selD) grn else sec)
        p.setBackgroundResource(if (!selD) R.drawable.kategori_item_selected_bg else R.drawable.kategori_item_bg)
        p.setTextColor(if (!selD) grn else sec)
        goalAdapter.submit(sortedGoals(viewModel.goals.value))
    }

    private fun sortedGoals(goals: List<GoalEntity>): List<GoalEntity> = when (goalSort) {
        GoalSort.DEADLINE -> goals.sortedWith(
            compareBy<GoalEntity> { it.deadline == null }.thenBy { it.deadline ?: Long.MAX_VALUE }
        )
        GoalSort.PROGRESS -> goals.sortedByDescending {
            if (it.targetAmount > 0) it.currentAmount / it.targetAmount else 0.0
        }
    }

    private fun updateGoalsHeader(view: View, goals: List<GoalEntity>) {
        val active = goals.count { it.currentAmount < it.targetAmount }
        val totalSaved = goals.sumOf { it.currentAmount }
        view.findViewById<TextView>(R.id.tvGoalsSummary).text =
            "$active / ${goals.size} hedef aktif · Toplam birikim: ${CurrencyFormatter.format(totalSaved)}"
    }

    private fun setupGoalSwipe(rootView: View, rv: RecyclerView) {
        val deleteBackground = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.expense_red))
        val deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val goal = goalAdapter.getGoalAt(pos)
                viewModel.deleteGoal(goal)
                Snackbar.make(rootView, "${goal.title} silindi", Snackbar.LENGTH_LONG).show()
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2
                if (dX < 0) {
                    deleteBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteBackground.draw(c)
                    val iconLeft = itemView.right - iconMargin - (deleteIcon?.intrinsicWidth ?: 0)
                    val iconRight = itemView.right - iconMargin
                    deleteIcon?.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.bottom - iconMargin)
                    deleteIcon?.draw(c)
                } else if (dX > 0) {
                    deleteBackground.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    deleteBackground.draw(c)
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + (deleteIcon?.intrinsicWidth ?: 0)
                    deleteIcon?.setBounds(iconLeft, itemView.top + iconMargin, iconRight, itemView.bottom - iconMargin)
                    deleteIcon?.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }

    private fun showAddGoalDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_add_goal)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etTitle = dialog.findViewById<TextInputEditText>(R.id.etGoalTitle)
        val etTarget = dialog.findViewById<TextInputEditText>(R.id.etGoalTarget)
        val tvDeadline = dialog.findViewById<TextView>(R.id.tvGoalDeadlineValue)
        var selectedDeadline: Long? = null
        var selectedIcon = "🎯"

        parentFragmentManager.setFragmentResultListener(EmojiPickerSheet.RESULT_KEY, viewLifecycleOwner) { _, b ->
            val e = b.getString(EmojiPickerSheet.BUNDLE_EMOJI) ?: return@setFragmentResultListener
            selectedIcon = e
            dialog.findViewById<TextView>(R.id.tvSelectedGoalIcon)?.text = e
        }

        tvDeadline.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val cal = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                selectedDeadline = cal.timeInMillis
                tvDeadline.text = dateFormat.format(cal.time)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        val icons = arrayListOf(
            "🎯", "🏠", "✈️", "💰", "🚗", "🎓", "💍", "📱", "🌴", "🛡️", "⭐",
            "🎁", "🏋️", "💳", "🔐", "🌊", "☕", "🎮", "📚", "🧳", "💼", "🌟"
        )
        val tvSelectedIcon = dialog.findViewById<TextView>(R.id.tvSelectedGoalIcon)
        tvSelectedIcon.setOnClickListener {
            EmojiPickerSheet.show(parentFragmentManager, selectedIcon, icons)
        }

        dialog.findViewById<View>(R.id.btnCancelGoal).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSaveGoal).setOnClickListener {
            val title = etTitle.text?.toString()?.trim() ?: ""
            val target = etTarget.text?.toString()?.toDoubleOrNull()
            if (title.isBlank()) { etTitle.error = "Hedef adı girin"; return@setOnClickListener }
            if (target == null || target <= 0) { etTarget.error = "Geçerli tutar girin"; return@setOnClickListener }
            viewModel.addGoal(title, selectedIcon, target, selectedDeadline)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showContributionDialog(goal: GoalEntity) {
        val dialog = android.app.Dialog(requireContext())
        val dlgView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goal_contribution, null)
        dialog.setContentView(dlgView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dlgView.findViewById<TextView>(R.id.tvContributionGoalTitle).text = "${goal.icon} ${goal.title}"
        val etAmount = dlgView.findViewById<TextInputEditText>(R.id.etContributionAmount)

        dlgView.findViewById<View>(R.id.btnCancelContribution).setOnClickListener { dialog.dismiss() }
        dlgView.findViewById<View>(R.id.btnSaveContribution).setOnClickListener {
            val amount = etAmount.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0) { etAmount.error = "Geçerli tutar girin"; return@setOnClickListener }
            viewModel.addContribution(goal, amount)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditGoalDialog(goal: GoalEntity) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_add_goal)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.tvSelectedGoalIcon)?.text = goal.icon
        dialog.findViewById<TextInputEditText>(R.id.etGoalTitle)?.setText(goal.title)
        dialog.findViewById<TextInputEditText>(R.id.etGoalTarget)?.setText(
            goal.targetAmount.toBigDecimal().stripTrailingZeros().toPlainString()
        )
        val tvDeadline = dialog.findViewById<TextView>(R.id.tvGoalDeadlineValue)
        var selectedDeadline: Long? = goal.deadline
        if (selectedDeadline != null) tvDeadline.text = dateFormat.format(Date(selectedDeadline!!))

        var selectedIcon = goal.icon
        parentFragmentManager.setFragmentResultListener(EmojiPickerSheet.RESULT_KEY, viewLifecycleOwner) { _, b ->
            val e = b.getString(EmojiPickerSheet.BUNDLE_EMOJI) ?: return@setFragmentResultListener
            selectedIcon = e
            dialog.findViewById<TextView>(R.id.tvSelectedGoalIcon)?.text = e
        }
        val icons = arrayListOf(
            "🎯", "🏠", "✈️", "💰", "🚗", "🎓", "💍", "📱", "🌴", "🛡️", "⭐",
            "🎁", "🏋️", "💳", "🔐", "🌊", "☕", "🎮", "📚", "🧳", "💼", "🌟"
        )
        dialog.findViewById<TextView>(R.id.tvSelectedGoalIcon)?.setOnClickListener {
            EmojiPickerSheet.show(parentFragmentManager, selectedIcon, icons)
        }

        tvDeadline.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val cal = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                selectedDeadline = cal.timeInMillis
                tvDeadline.text = dateFormat.format(cal.time)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialog.findViewById<View>(R.id.btnCancelGoal).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSaveGoal).setOnClickListener {
            val title = dialog.findViewById<TextInputEditText>(R.id.etGoalTitle)?.text?.toString()?.trim().orEmpty()
            val target = dialog.findViewById<TextInputEditText>(R.id.etGoalTarget)?.text?.toString()?.toDoubleOrNull()
            if (title.isBlank()) { dialog.findViewById<TextInputEditText>(R.id.etGoalTitle)?.error = "Hedef adı girin"; return@setOnClickListener }
            if (target == null || target <= 0) { dialog.findViewById<TextInputEditText>(R.id.etGoalTarget)?.error = "Geçerli tutar girin"; return@setOnClickListener }
            viewModel.updateGoal(goal.copy(title = title, icon = selectedIcon, targetAmount = target, deadline = selectedDeadline))
            dialog.dismiss()
        }
        dialog.show()
    }
}

private class GoalAdapter(
    private val onContribution: (GoalEntity) -> Unit,
    private val onRowClick: (GoalEntity) -> Unit,
    private val onEdit: (GoalEntity) -> Unit
) : RecyclerView.Adapter<GoalAdapter.VH>() {

    private val fmt = SimpleDateFormat("d MMM yyyy", Locale("tr"))
    private var goals: List<GoalEntity> = emptyList()
    private val celebratedCompletionIds = mutableSetOf<Long>()

    fun submit(list: List<GoalEntity>) {
        goals = list
        notifyDataSetChanged()
    }

    fun getGoalAt(position: Int): GoalEntity = goals[position]

    inner class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false))

    override fun getItemCount() = goals.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val goal = goals[pos]
        val v = h.itemView
        val ctx = v.context
        val card = v as CardView
        v.findViewById<TextView>(R.id.tvGoalIcon).text = goal.icon
        v.findViewById<TextView>(R.id.tvGoalTitle).text = goal.title
        v.findViewById<TextView>(R.id.tvGoalDeadline).text =
            if (goal.deadline != null) "Son: ${fmt.format(Date(goal.deadline))}" else "Son tarih yok"

        val rawPct = if (goal.targetAmount > 0) ((goal.currentAmount / goal.targetAmount) * 100).toInt() else 0
        val progress = rawPct.coerceIn(0, 100)
        val completed = goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount
        val now = System.currentTimeMillis()
        val overdue = !completed && goal.deadline != null && goal.deadline < now

        v.findViewById<LinearProgressIndicator>(R.id.progressGoal).progress = progress
        v.findViewById<TextView>(R.id.tvGoalPercent).text = "%$rawPct"
        v.findViewById<TextView>(R.id.tvGoalCurrent).text = CurrencyFormatter.format(goal.currentAmount)
        v.findViewById<TextView>(R.id.tvGoalTarget).text = "/ ${CurrencyFormatter.format(goal.targetAmount)}"

        val tvOverdue = v.findViewById<TextView>(R.id.tvGoalOverdueBadge)
        val tvDone = v.findViewById<TextView>(R.id.tvGoalCompletedBadge)
        tvOverdue.visibility = if (overdue) View.VISIBLE else View.GONE
        tvDone.visibility = if (completed) View.VISIBLE else View.GONE

        card.setCardBackgroundColor(
            if (completed) ContextCompat.getColor(ctx, R.color.goal_completed_card_bg)
            else ContextCompat.getColor(ctx, R.color.card_background)
        )

        if (!completed) celebratedCompletionIds.remove(goal.id)
        if (completed && goal.id !in celebratedCompletionIds) {
            celebratedCompletionIds.add(goal.id)
            tvDone.post {
                ObjectAnimator.ofFloat(tvDone, View.SCALE_X, 1f, 1.12f, 1f).setDuration(450).start()
                ObjectAnimator.ofFloat(tvDone, View.SCALE_Y, 1f, 1.12f, 1f).setDuration(450).start()
            }
        }

        v.setOnClickListener { onRowClick(goal) }
        v.findViewById<MaterialButton>(R.id.btnAddContribution).setOnClickListener { onContribution(goal) }
        v.setOnLongClickListener { onEdit(goal); true }
    }
}
