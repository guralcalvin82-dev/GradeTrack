package com.example.gradetrack

import android.app.Activity
import android.content.Intent
import android.view.View

/**
 * Shared bottom-navigation click handling for the faculty screens.
 * Call FacultyBottomNavHelper.setup(this, facultyId, Tab.X) once in onCreate of each screen,
 * after setContentView().
 *
 * On the faculty dashboard (which has the bar but no active tab), pass null as `current`.
 */
object FacultyBottomNavHelper {

    enum class Tab { STUDENTS, SUBJECTS, GRADES, SCHEDULE, PROFILE }

    fun setup(activity: Activity, facultyId: String?, current: Tab?) {
        bind(activity, R.id.btnManageStudents, Tab.STUDENTS, ManageStudentsActivity::class.java, facultyId, current)
        bind(activity, R.id.btnManageSubjects, Tab.SUBJECTS, ManageSubjectsActivity::class.java, facultyId, current)
        bind(activity, R.id.btnManageGrade, Tab.GRADES, GradeManagementActivity::class.java, facultyId, current)
        bind(activity, R.id.btnManageSchedule, Tab.SCHEDULE, ManageScheduleActivity::class.java, facultyId, current)
        bind(activity, R.id.btnFacultyProfile, Tab.PROFILE, FacultyProfileActivity::class.java, facultyId, current)
    }

    private fun bind(
        activity: Activity,
        viewId: Int,
        tab: Tab,
        target: Class<*>,
        facultyId: String?,
        current: Tab?
    ) {
        val view = activity.findViewById<View>(viewId) ?: return
        view.setOnClickListener {
            if (tab == current) return@setOnClickListener   // already on this screen

            val intent = Intent(activity, target).apply {
                putExtra("FACULTY_ID", facultyId)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)

            // Dashboard stays in the back stack; the tab screens replace each other
            if (current != null) activity.finish()
        }
    }
}