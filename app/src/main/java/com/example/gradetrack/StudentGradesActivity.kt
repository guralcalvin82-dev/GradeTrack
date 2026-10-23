package com.example.gradetrack

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class StudentGradesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentSection: TextView

    private lateinit var btnSem1: Button
    private lateinit var btnSem2: Button

    private lateinit var layoutGradesContainer: LinearLayout

    private lateinit var tvSemesterAverage: TextView
    private lateinit var tvStatus: TextView

    private lateinit var btnBack: Button

    private var currentStudentId = ""
    private var selectedSemester = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_grades)

        dbHelper = DatabaseHelper(this)

        currentStudentId = intent.getStringExtra("STUDENT_ID") ?: ""

        initializeViews()

        btnBack.setOnClickListener { finish() }

        btnSem1.setOnClickListener {
            selectedSemester = 1
            updateSemesterButtons()
            loadGrades()
        }

        btnSem2.setOnClickListener {
            selectedSemester = 2
            updateSemesterButtons()
            loadGrades()
        }

        if (currentStudentId.isEmpty()) {
            Toast.makeText(this, "Student account not found.", Toast.LENGTH_LONG).show()
            return
        }

        loadStudentInfo()
        updateSemesterButtons()
        loadGrades()
    }

    private fun initializeViews() {
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentSection = findViewById(R.id.tvStudentSection)
        btnSem1 = findViewById(R.id.btnSem1)
        btnSem2 = findViewById(R.id.btnSem2)
        layoutGradesContainer = findViewById(R.id.layoutGradesContainer)
        tvSemesterAverage = findViewById(R.id.tvSemesterAverage)
        tvStatus = findViewById(R.id.tvStatus)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun loadStudentInfo() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT name, course, section
            FROM users
            WHERE user_id = ?
            AND role = 'STUDENT'
            """.trimIndent(),
            arrayOf(currentStudentId)
        )

        if (cursor.moveToFirst()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val course = cursor.getString(cursor.getColumnIndexOrThrow("course"))
            val section = cursor.getString(cursor.getColumnIndexOrThrow("section"))

            tvStudentName.text = name
            tvStudentSection.text = "$course • $section"
        }
        cursor.close()
    }

    private fun updateSemesterButtons() {
        btnSem1.alpha = if (selectedSemester == 1) 1f else 0.6f
        btnSem2.alpha = if (selectedSemester == 2) 1f else 0.6f
    }

    private fun loadGrades() {
        layoutGradesContainer.removeAllViews()

        val grades = dbHelper.getStudentGrades(currentStudentId, selectedSemester)

        if (grades.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No grades available for Semester $selectedSemester."
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(20, 50, 20, 50)
            }
            layoutGradesContainer.addView(emptyText)
            tvSemesterAverage.text = "--"
            tvStatus.text = "NO GRADES"
            return
        }

        var totalSemesterAverage = 0.0
        var evaluatedSubjectsCount = 0

        for (grade in grades) {
            val validGrades = mutableListOf<Double>()
            if (grade.midterm > 0.0) validGrades.add(grade.midterm)
            if (grade.final > 0.0) validGrades.add(grade.final)

            val subjectAverage = if (validGrades.isNotEmpty()) {
                validGrades.sum() / validGrades.size
            } else {
                null
            }

            if (subjectAverage != null) {
                totalSemesterAverage += subjectAverage
                evaluatedSubjectsCount++
            }

            addGradeCard(grade, subjectAverage)
        }

        if (evaluatedSubjectsCount > 0) {
            val finalSemesterAverage = totalSemesterAverage / evaluatedSubjectsCount
            tvSemesterAverage.text = String.format(Locale.US, "%.2f", finalSemesterAverage)

            if (finalSemesterAverage >= 75.0) {
                tvStatus.text = "PASSED"
                tvStatus.setTextColor(Color.parseColor("#2E7D32")) // Green
            } else {
                tvStatus.text = "FAILED"
                tvStatus.setTextColor(Color.RED)
            }
        } else {
            tvSemesterAverage.text = "--"
            tvStatus.text = "NO GRADES"
            tvStatus.setTextColor(Color.GRAY)
        }
    }

    private fun addGradeCard(grade: StudentGrade, average: Double?) {
        val card = androidx.cardview.widget.CardView(this).apply {
            radius = 16f
            cardElevation = 3f
            setContentPadding(20, 18, 20, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Top Row: Subject Name & Code
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val subjectName = TextView(this).apply {
            text = grade.subjectName
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subjectCode = TextView(this).apply {
            text = grade.subjectCode
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        topRow.addView(subjectName)
        topRow.addView(subjectCode)

        // Grades Row: Midterm, Final
        val gradesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 12)
        }

        val midtermStr = if (grade.midterm > 0.0) String.format(Locale.US, "%.2f", grade.midterm) else "-"
        val finalStr = if (grade.final > 0.0) String.format(Locale.US, "%.2f", grade.final) else "-"

        val midtermLayout = createGradeColumn("MIDTERM", midtermStr)
        val finalLayout = createGradeColumn("FINAL", finalStr)

        gradesRow.addView(midtermLayout)
        gradesRow.addView(finalLayout)

        // Subject Average Text
        val averageStr = if (average != null) {
            String.format(Locale.US, "Final Grade: %.2f", average)
        } else {
            "Final Grade: --"
        }

        val averageText = TextView(this).apply {
            text = averageStr
            textSize = 17f
            setTextColor(Color.DKGRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        }

        mainLayout.addView(topRow)
        mainLayout.addView(gradesRow)
        mainLayout.addView(averageText)

        card.addView(mainLayout)
        layoutGradesContainer.addView(card)
    }

    private fun createGradeColumn(label: String, grade: String): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val labelText = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val gradeText = TextView(this).apply {
            text = grade
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 5, 0, 0)
        }

        column.addView(labelText)
        column.addView(gradeText)

        return column
    }
}