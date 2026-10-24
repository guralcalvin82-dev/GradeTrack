package com.example.gradetrack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    // Views for Academic Summary
    private lateinit var tvFirstSem: TextView
    private lateinit var tvSecondSem: TextView
    private lateinit var tvOverallAverage: TextView
    private lateinit var tvStatus: TextView

    // Action Buttons
    private lateinit var btnMyGrades: MaterialButton
    private lateinit var btnSchedule: MaterialButton
    private lateinit var btnProfile: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var studentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        tvFirstSem = findViewById(R.id.tvFirstSem)
        tvSecondSem = findViewById(R.id.tvSecondSem)
        tvOverallAverage = findViewById(R.id.tvOverallAverage)
        tvStatus = findViewById(R.id.tvStatus)

        dbHelper = DatabaseHelper(this)
        studentId = intent.getStringExtra("STUDENT_ID") ?: ""

        initViews()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        showAcademicSummary(studentId)
    }

    private fun initViews() {
        tvFirstSem = findViewById(R.id.tvFirstSem)
        tvSecondSem = findViewById(R.id.tvSecondSem)
        tvOverallAverage = findViewById(R.id.tvOverallAverage)
        tvStatus = findViewById(R.id.tvStatus)

        btnMyGrades = findViewById(R.id.btnMyGrades)
        btnSchedule = findViewById(R.id.btnSchedule)
        btnProfile = findViewById(R.id.btnProfile)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupNavigation() {
        btnMyGrades.setOnClickListener {
            val intent = Intent(this, StudentGradesActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        btnSchedule.setOnClickListener {
            val intent = Intent(this, StudentScheduleActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        btnProfile.setOnClickListener {
            val intent = Intent(this, StudentProfileActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        // Handle Logout
        btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun calculateAndDisplayAverages() {
        if (studentId.isEmpty()) return

        val sem1Average = calculateSemesterAverage(1)
        val sem2Average = calculateSemesterAverage(2)

        tvFirstSem.text = if (sem1Average != null) String.format(Locale.US, "%.2f", sem1Average) else "--"
        tvSecondSem.text = if (sem2Average != null) String.format(Locale.US, "%.2f", sem2Average) else "--"

        val availableSemesters = listOfNotNull(sem1Average, sem2Average)

        if (availableSemesters.isNotEmpty()) {
            val overallAverage = availableSemesters.average()
            tvOverallAverage.text = String.format(Locale.US, "Overall Average: %.2f", overallAverage)

            if (overallAverage >= 75.0) {
                tvStatus.text = "Passed"
                tvStatus.setBackgroundColor(Color.parseColor("#DEF7EC"))
                tvStatus.setTextColor(Color.parseColor("#03543F"))
            } else {
                tvStatus.text = "Failed"
                tvStatus.setBackgroundColor(Color.parseColor("#FDE8E8"))
                tvStatus.setTextColor(Color.parseColor("#9B1C1C"))
            }
        } else {
            tvOverallAverage.text = "Overall Average: --"
            tvStatus.text = "NO GRADES"
            tvStatus.setBackgroundColor(Color.parseColor("#E2E8F0"))
            tvStatus.setTextColor(Color.parseColor("#4A5568"))
        }
    }

    private fun showAcademicSummary(studentId: String) {
        val sem1 = dbHelper.getSemesterAverage(studentId, 1)
        val sem2 = dbHelper.getSemesterAverage(studentId, 2)
        val overall = dbHelper.getOverallAverage(studentId)

        fun fmt(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "--"

        tvFirstSem.text = fmt(sem1)
        tvSecondSem.text = fmt(sem2)
        tvOverallAverage.text = "Overall Average: ${fmt(overall)}"

        when (dbHelper.getAcademicStatus(overall)) {
            "Passed" -> {
                tvStatus.text = "PASSED"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            }
            "Failed" -> {
                tvStatus.text = "FAILED"
                tvStatus.setTextColor(android.graphics.Color.RED)
            }
            else -> {
                tvStatus.text = "NO GRADES"
                tvStatus.setTextColor(getColor(R.color.primary_dark))
            }
        }
    }

    private fun calculateSemesterAverage(semester: Int): Double? {
        val grades = dbHelper.getStudentGrades(studentId, semester)
        if (grades.isEmpty()) return null

        var totalAverage = 0.0
        var subjectCount = 0

        for (grade in grades) {
            val validGrades = mutableListOf<Double>()
            if (grade.midterm > 0.0) validGrades.add(grade.midterm)
            if (grade.final > 0.0) validGrades.add(grade.final)

            if (validGrades.isNotEmpty()) {
                val subjectAverage = validGrades.sum() / validGrades.size
                totalAverage += subjectAverage
                subjectCount++
            }
        }

        return if (subjectCount > 0) totalAverage / subjectCount else null
    }
}