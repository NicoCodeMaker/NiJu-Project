package com.example.niju_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.Challenge
import com.example.niju_project.data.model.Rewards
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.UiState
import com.example.niju_project.ui.rewards.RewardsData
import com.example.niju_project.ui.rewards.RewardsViewModel
import com.example.niju_project.ui.rewards.badgeEmoji
import com.example.niju_project.ui.widget.ConfettiView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Sistema visual de progresión: nivel y título, racha con hitos, retos diarios,
 * metas semanales, insignias con avance y últimos premios. Todo sale del servidor.
 */
class RewardsActivity : AppCompatActivity() {

    private val viewModel: RewardsViewModel by viewModels()
    private val badges = BadgesAdapter { showBadge(it) }
    private var celebrated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rewards)
        findViewById<RecyclerView>(R.id.rvBadges).apply {
            layoutManager = GridLayoutManager(this@RewardsActivity, 3)
            adapter = badges
        }
        findViewById<Button>(R.id.btnEmptyAction).setOnClickListener { viewModel.load() }
        setupBottomNav(R.id.navRewards)
        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    findViewById<View>(R.id.progressBar).visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.emptyState).visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.content).visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Error -> {
                            if (state.sessionExpired) goToLogin()
                            findViewById<TextView>(R.id.tvEmptyMessage).text = state.message
                            findViewById<View>(R.id.btnEmptyAction).visibility = View.VISIBLE
                        }
                        UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun render(data: RewardsData) {
        val r = data.rewards
        renderLevel(r)
        renderChallenges(findViewById(R.id.dailyList), r.daily, R.string.rewards_no_daily)
        renderChallenges(findViewById(R.id.weeklyList), r.weekly, R.string.rewards_no_weekly)
        renderStreak(r)
        badges.submit(data.achievements)
        findViewById<TextView>(R.id.tvBadgesCount).text =
            getString(R.string.rewards_badges_count, data.achievements.count { it.unlocked }, data.achievements.size)
        renderRecent(r)

        val claimed = data.justClaimed
        val count = (claimed?.challengesCompleted?.size ?: 0) + (claimed?.newAchievements?.size ?: 0)
        if (count > 0 && !celebrated) {
            celebrated = true
            findViewById<ConfettiView>(R.id.confetti).burst()
            val xp = (claimed?.challengesCompleted?.sumOf { it.xpReward } ?: 0) + (claimed?.newAchievements?.sumOf { it.xpReward } ?: 0)
            Snackbar.make(findViewById(R.id.content), getString(R.string.rewards_claimed, xp), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun renderLevel(r: Rewards) {
        findViewById<TextView>(R.id.tvLevelNumber).apply {
            text = r.level.toString()
            contentDescription = getString(R.string.level_value, r.level)
        }
        findViewById<TextView>(R.id.tvLevelTitle).text = r.levelTitle
        val missing = (r.nextLevelXp - r.xpTotal).coerceAtLeast(0)
        findViewById<TextView>(R.id.tvLevelXp).text = getString(R.string.rewards_level_xp, r.xpTotal, missing, r.level + 1)
        findViewById<LinearProgressIndicator>(R.id.progressLevel).setProgressCompat(r.levelPercent, true)
        findViewById<TextView>(R.id.tvNextTitle).apply {
            visibility = if (r.nextLevelTitle == null) View.GONE else View.VISIBLE
            text = getString(R.string.rewards_next_title, r.nextLevelTitle.orEmpty())
        }
    }

    private fun renderChallenges(list: LinearLayout, items: List<Challenge>, emptyText: Int) {
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(emptyText)
                setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary))
                setPadding(0, 24, 0, 24)
            })
            return
        }
        items.forEach { c ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_challenge, list, false)
            v.findViewById<TextView>(R.id.tvChIcon).apply {
                text = if (c.completed) "✅" else if (c.isWeekly) "📅" else "🎯"
                setBackgroundResource(if (c.completed) R.drawable.bg_circle_success else R.drawable.bg_circle_accent_soft)
            }
            v.findViewById<TextView>(R.id.tvChTitle).text = c.title
            v.findViewById<TextView>(R.id.tvChDescription).text = c.description
            v.findViewById<TextView>(R.id.tvChReward).apply {
                text = if (c.claimed) getString(R.string.rewards_claimed_badge, c.xpReward) else getString(R.string.xp_plus, c.xpReward)
                setBackgroundResource(if (c.claimed) R.drawable.bg_badge_success else R.drawable.bg_badge_xp)
                setTextColor(ContextCompat.getColor(context, if (c.claimed) R.color.color_success else R.color.color_xp))
            }
            v.findViewById<LinearProgressIndicator>(R.id.progressCh).apply {
                setProgressCompat(c.percent, true)
                setIndicatorColor(ContextCompat.getColor(context, if (c.completed) R.color.color_success else R.color.color_accent))
            }
            v.findViewById<TextView>(R.id.tvChProgress).text = getString(R.string.fraction_value, c.progress, c.target)
            v.contentDescription = "${c.title}. ${c.description}. " +
                if (c.completed) getString(R.string.rewards_done) else getString(R.string.rewards_progress_desc, c.progress, c.target, c.xpReward)
            list.addView(v)
        }
    }

    private fun renderStreak(r: Rewards) {
        findViewById<TextView>(R.id.tvStreakNow).text = getString(R.string.rewards_streak_now, Formatters.days(r.currentStreak))
        val next = r.nextMilestone
        findViewById<TextView>(R.id.tvStreakNext).text = if (next == null)
            getString(R.string.rewards_streak_max, r.longestStreak)
        else getString(R.string.rewards_streak_next, Formatters.days(next.days - r.currentStreak), next.days, next.xp, r.longestStreak)

        val row = findViewById<LinearLayout>(R.id.milestonesRow)
        row.removeAllViews()
        val d = resources.displayMetrics.density
        r.streakMilestones.forEach { m ->
            val reachedNow = r.currentStreak >= m.days
            row.addView(TextView(this).apply {
                text = getString(R.string.rewards_milestone, m.days)
                gravity = android.view.Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, if (reachedNow || m.reached) R.color.color_streak else R.color.color_text_hint))
                setBackgroundResource(if (reachedNow || m.reached) R.drawable.bg_badge_streak else R.drawable.bg_badge_neutral)
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (4 * d).toInt()
                }
                contentDescription = getString(R.string.rewards_milestone_desc, m.days, m.xp,
                    getString(if (m.reached) R.string.rewards_reached else R.string.rewards_pending))
            })
        }
    }

    private fun renderRecent(r: Rewards) {
        val list = findViewById<LinearLayout>(R.id.recentList)
        list.removeAllViews()
        if (r.recentRewards.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(R.string.rewards_no_recent)
                setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary))
                setPadding(0, 24, 0, 24)
            })
            return
        }
        r.recentRewards.forEach { rw ->
            val (icon, label) = Formatters.rewardReason(rw.reason)
            val v = LayoutInflater.from(this).inflate(R.layout.item_reward_row, list, false)
            v.findViewById<TextView>(R.id.tvRowIcon).text = icon
            v.findViewById<TextView>(R.id.tvRowTitle).text = label
            v.findViewById<TextView>(R.id.tvRowSubtitle).text = Formatters.shortDate(rw.createdAt)
            v.findViewById<TextView>(R.id.tvRowBadge).text = getString(R.string.xp_plus, rw.amount)
            v.contentDescription = "$label, ${Formatters.shortDate(rw.createdAt)}, ${getString(R.string.xp_plus, rw.amount)}"
            list.addView(v)
        }
    }

    private fun showBadge(a: AchievementStatus) {
        MaterialAlertDialogBuilder(this)
            .setTitle("${badgeEmoji(a.metric)}  ${a.title}")
            .setMessage(buildString {
                append(a.description)
                append("\n\n")
                append(if (a.unlocked) getString(R.string.rewards_badge_unlocked, Formatters.shortDate(a.unlockedAt))
                       else getString(R.string.rewards_badge_progress, a.currentValue, a.threshold))
                if (a.xpReward > 0) append("\n").append(getString(R.string.rewards_badge_reward, a.xpReward))
            })
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}

private class BadgesAdapter(
    private val onClick: (AchievementStatus) -> Unit
) : RecyclerView.Adapter<BadgesAdapter.VH>() {

    private var items: List<AchievementStatus> = emptyList()

    fun submit(list: List<AchievementStatus>) { items = list; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvBadgeIcon)
        val lock: TextView = v.findViewById(R.id.tvBadgeLock)
        val title: TextView = v.findViewById(R.id.tvBadgeTitle)
        val progress: LinearProgressIndicator = v.findViewById(R.id.progressBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = items[position]
        val ctx = h.itemView.context
        h.icon.text = badgeEmoji(a.metric)
        h.icon.alpha = if (a.unlocked) 1f else 0.35f
        h.icon.setBackgroundResource(if (a.unlocked) R.drawable.bg_circle_xp else R.drawable.bg_badge_neutral)
        h.lock.visibility = if (a.unlocked) View.GONE else View.VISIBLE
        h.title.text = a.title
        h.progress.setProgressCompat(a.percent, false)
        h.progress.setIndicatorColor(ContextCompat.getColor(ctx, if (a.unlocked) R.color.color_success else R.color.color_accent))
        h.itemView.contentDescription = "${a.title}. " + if (a.unlocked) ctx.getString(R.string.stats_unlocked)
            else ctx.getString(R.string.rewards_badge_progress, a.currentValue, a.threshold)
        h.itemView.setOnClickListener { onClick(a) }
    }

    override fun getItemCount() = items.size
}
