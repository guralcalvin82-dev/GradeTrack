package com.example.gradetrack

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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

    private var currentStudentId = ""
    private var selectedSemester = 1

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_grades)

        dbHelper = DatabaseHelper(this)

        initializeViews()

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

        currentStudentId = resolveStudentId()

        // Bottom navigation bar (Home / Grades / Schedule / Profile)
        BottomNavHelper.setup(this, currentStudentId, BottomNavHelper.Tab.GRADES)

        if (currentStudentId.isEmpty()) {
            Toast.makeText(this, "Student account not found.", Toast.LENGTH_LONG).show()
            tvStatus.text = "NO ACCOUNT"
            return
        }

        loadStudentInfo()
        selectedSemester = pickSemesterWithGrades()
        updateSemesterButtons()
        loadGrades()
    }

    override fun onResume() {
        super.onResume()
        // I-refresh tuwing babalik sa screen para laging updated ang grades
        if (currentStudentId.isNotEmpty()) {
            loadGrades()
        }
    }

    private fun initializeViews() {
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentSection = findViewById(R.id.tvStudentSection)
        btnSem1 = findViewById(R.id.btnSem1)
        btnSem2 = findViewById(R.id.btnSem2)
        layoutGradesContainer = findViewById(R.id.layoutGradesContainer)
        tvSemesterAverage = findViewById(R.id.tvSemesterAverage)
        tvStatus = findViewById(R.id.tvStatus)
    }

    /**
     * Kinukuha ang student ID mula sa intent (sinusubukan ang iba't ibang key),
     * tapos kino-convert sa tamang user_id sa database kahit name o numeric id ang naipasa.
     */
    private fun resolveStudentId(): String {
        val keys = listOf("STUDENT_ID", "USER_ID", "user_id", "studentId", "student_id", "userId", "ID")
        var raw = ""
        for (k in keys) {
            val v = intent.getStringExtra(k)
            if (!v.isNullOrBlank()) {
                raw = v.trim()
                break
            }
        }
        if (raw.isEmpty()) return ""

        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT user_id FROM ${DatabaseHelper.TABLE_USERS}
            WHERE role = 'STUDENT'
              AND (user_id = ? COLLATE NOCASE
                   OR name = ? COLLATE NOCASE
                   OR CAST(id AS TEXT) = ?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(raw, raw, raw)
        ).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return ""
    }

    /** Pipiliin ang semester na may naipasok nang grade; Sem 1 kung wala pa sa dalawa. */
    private fun pickSemesterWithGrades(): Int {
        for (sem in 1..2) {
            if (dbHelper.getStudentGrades(currentStudentId, sem).any { it.hasAnyGrade }) {
                return sem
            }
        }
        return 1
    }

    private fun loadStudentInfo() {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT name, course, section
            FROM ${DatabaseHelper.TABLE_USERS}
            WHERE user_id = ? AND role = 'STUDENT'
            """.trimIndent(),
            arrayOf(currentStudentId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: ""
                val course = cursor.getString(1) ?: ""
                val section = cursor.getString(2) ?: ""

                tvStudentName.text = name
                tvStudentSection.text = listOf(course, section)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
            }
        }
    }

    private fun updateSemesterButtons() {
        btnSem1.alpha = if (selectedSemester == 1) 1f else 0.6f
        btnSem2.alpha = if (selectedSemester == 2) 1f else 0.6f
    }

    /** Nilalagyan ng text at kulay ang status TextView base sa average. */
    private fun applyStatus(tv: TextView, average: Double?) {
        when (dbHelper.getAcademicStatus(average)) {
            "Passed" -> {
                tv.text = "PASSED"
                tv.setTextColor(Color.parseColor("#2E7D32"))
            }
            "Failed" -> {
                tv.text = "FAILED"
                tv.setTextColor(Color.RED)
            }
            else -> {
                tv.text = "NO GRADES"
                tv.setTextColor(Color.GRAY)
            }
        }
    }

    private fun loadGrades() {
        layoutGradesContainer.removeAllViews()

        val grades = dbHelper.getStudentGrades(currentStudentId, selectedSemester)

        android.util.Log.d(
            "GRADES",
            "id=$currentStudentId sem=$selectedSemester count=${grades.size}"
        )

        if (grades.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No subjects available for Semester $selectedSemester."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(color(R.color.text_secondary))
                setPadding(20, 50, 20, 50)
            }
            layoutGradesContainer.addView(emptyText)
            tvSemesterAverage.text = "--"
            tvStatus.text = "NO GRADES"
            tvStatus.setTextColor(Color.GRAY)
            return
        }

        var totalSemesterAverage = 0.0
        var evaluatedSubjectsCount = 0

        for (grade in grades) {
            android.util.Log.d(
                "GRADES",
                "${grade.subjectCode} mid=${grade.midterm}(${grade.midtermEntered}) " +
                        "fin=${grade.final}(${grade.finalEntered})"
            )

            val subjectAverage: Double? = if (grade.hasAnyGrade) grade.average else null

            if (subjectAverage != null) {
                totalSemesterAverage += subjectAverage
                evaluatedSubjectsCount++
            }

            addGradeCard(grade, subjectAverage)
        }

        if (evaluatedSubjectsCount > 0) {
            val finalSemesterAverage = totalSemesterAverage / evaluatedSubjectsCount
            tvSemesterAverage.text = String.format(Locale.US, "%.2f", finalSemesterAverage)
            applyStatus(tvStatus, finalSemesterAverage)
        } else {
            tvSemesterAverage.text = "--"
            applyStatus(tvStatus, null)
        }
    }

    private fun addGradeCard(grade: StudentGrade, average: Double?) {
        val card = CardView(this).apply {
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

        val units = dbHelper.getSubjectUnits(grade.subjectCode)
        val subjectName = TextView(this).apply {
            text = "${grade.subjectName} ($units Units)"
            textSize = 15f
            setTextColor(color(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subjectCode = TextView(this).apply {
            text = grade.subjectCode
            textSize = 13f
            setTextColor(color(R.color.text_secondary))
            gravity = Gravity.END
            setTypeface(null, Typeface.BOLD)
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

        val midtermStr =
            if (grade.midtermEntered) String.format(Locale.US, "%.2f", grade.midterm) else "-"
        val finalStr =
            if (grade.finalEntered) String.format(Locale.US, "%.2f", grade.final) else "-"

        gradesRow.addView(createGradeColumn("MIDTERM", midtermStr))
        gradesRow.addView(createGradeColumn("FINAL", finalStr))

        // Subject Average
        val averageStr = if (average != null) {
            String.format(Locale.US, "Final Grade: %.2f", average)
        } else {
            "Final Grade: --"
        }

        val averageText = TextView(this).apply {
            text = averageStr
            textSize = 17f
            setTextColor(color(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
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
            setTextColor(color(R.color.text_secondary))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val gradeText = TextView(this).apply {
            text = grade
            textSize = 16f
            setTextColor(color(R.color.text_primary))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 5, 0, 0)
        }

        column.addView(labelText)
        column.addView(gradeText)

        return column
    }
}