package com.example.gradetrack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FacultyDashboardActivity : AppCompatActivity() {

    private var facultyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_dashboard)

        // Galing sa LoginActivity (putExtra "FACULTY_ID")
        facultyId = intent.getStringExtra("FACULTY_ID")?.trim().orEmpty()

        if (facultyId.isEmpty()) {
            Toast.makeText(this, "Faculty ID missing. Please log in again.", Toast.LENGTH_LONG).show()
            logout()
            return
        }

        findViewById<Button>(R.id.btnManageStudents).setOnClickListener {
            startActivity(Intent(this, ManageStudentsActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
            })
        }

        findViewById<Button>(R.id.btnManageSubjects).setOnClickListener {
            startActivity(Intent(this, ManageSubjectsActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
            })
        }

        findViewById<Button>(R.id.btnManageGrade).setOnClickListener {
            startActivity(Intent(this, GradeManagementActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
            })
        }

        findViewById<Button>(R.id.btnManageSchedule).setOnClickListener {
            startActivity(Intent(this, ManageScheduleActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
            })
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
    }

    private fun logout() {
        val intent = Intent(this, LoginActivity::class.java)
        // Clear backstack so user can't press back to return to dashboard
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}