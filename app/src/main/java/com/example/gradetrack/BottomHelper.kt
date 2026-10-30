package com.example.gradetrack

import android.app.Activity
import android.content.Intent
import android.view.View

/**
 * Shared bottom-navigation click handling for the 4 student screens.
 * Call BottomNavHelper.setup(...) once in onCreate of each screen.
 */
object BottomNavHelper {

    enum class Tab { HOME, GRADES, SCHEDULE, PROFILE }

    fun setup(activity: Activity, studentId: String?, current: Tab) {
        bind(activity, R.id.navHome, Tab.HOME, StudentDashboardActivity::class.java, studentId, current)
        bind(activity, R.id.btnMyGrades, Tab.GRADES, StudentGradesActivity::class.java, studentId, current)
        bind(activity, R.id.btnSchedule, Tab.SCHEDULE, StudentScheduleActivity::class.java, studentId, current)
        bind(activity, R.id.btnProfile, Tab.PROFILE, StudentProfileActivity::class.java, studentId, current)
    }

    private fun bind(
        activity: Activity,
        viewId: Int,
        tab: Tab,
        target: Class<*>,
        studentId: String?,
        current: Tab
    ) {
        activity.findViewById<View>(viewId).setOnClickListener {
            if (tab == current) return@setOnClickListener   // already on this screen

            val intent = Intent(activity, target).apply {
                putExtra("STUDENT_ID", studentId)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (tab == Tab.HOME) {
                    // go back to the existing dashboard instead of stacking a new one
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)

            // Dashboard stays in the back stack; the other tabs replace each other
            if (current != Tab.HOME) activity.finish()
        }
    }
}