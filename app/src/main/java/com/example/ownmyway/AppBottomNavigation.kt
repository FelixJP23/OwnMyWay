package com.example.ownmyway

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object AppBottomNavigation {

    fun setup(
        activity: AppCompatActivity,
        selectedItemId: Int? = null,
        onHomeSelected: (() -> Unit)? = null,
        onFriendsSelected: (() -> Unit)? = null,
        onBudgetSelected: (() -> Unit)? = null
    ) {
        val bottomNav = activity.findViewById<android.view.View>(R.id.bottom_navigation) as? BottomNavigationView
            ?: return

        if (selectedItemId != null) {
            bottomNav.selectedItemId = selectedItemId
        } else {
            clearSelection(bottomNav)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (activity is MainActivity) {
                        onHomeSelected?.invoke()
                    } else {
                        open(activity, MainActivity::class.java)
                    }
                    true
                }
                R.id.nav_friends -> {
                    if (activity is FriendManagerActivity) {
                        onFriendsSelected?.invoke()
                    } else {
                        onFriendsSelected?.invoke()
                        open(activity, FriendManagerActivity::class.java)
                    }
                    true
                }
                R.id.nav_budget -> {
                    if (activity is BudgetActivity) {
                        onBudgetSelected?.invoke()
                    } else {
                        onBudgetSelected?.invoke()
                        open(activity, BudgetActivity::class.java)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun open(activity: AppCompatActivity, target: Class<out AppCompatActivity>) {
        if (activity.javaClass == target) return

        val intent = Intent(activity, target).apply {
            flags = if (target == MainActivity::class.java) {
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        }
        activity.startActivity(intent)
    }

    private fun clearSelection(bottomNav: BottomNavigationView) {
        val menu = bottomNav.menu
        menu.setGroupCheckable(0, false, true)
        for (i in 0 until menu.size()) {
            menu.getItem(i).isChecked = false
        }
        menu.setGroupCheckable(0, true, true)
    }
}
