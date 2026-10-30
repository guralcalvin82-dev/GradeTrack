package com.example.gradetrack

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class StudentScheduleActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tlSchedule: TableLayout
    private lateinit var btnSem1: MaterialButton
    private lateinit var btnSem2: MaterialButton

    private var studentId: String = ""
    private var selectedSemester: Int = 1

    // Must match the header weights in activity_student_schedule.xml
    private val weights = floatArrayOf(1.1f, 1.0f, 1.1f, 0.6f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_schedule)

        dbHelper = DatabaseHelper(this)

        tlSchedule = findViewById(R.id.tlSchedule)
        btnSem1 = findViewById(R.id.btnSem1)
        btnSem2 = findViewById(R.id.btnSem2)

        studentId = intent.getStringExtra("STUDENT_ID")
            ?: intent.getStringExtra("user_id")
                    ?: ""

        btnSem1.setOnClickListener { selectSemester(1) }
        btnSem2.setOnClickListener { selectSemester(2) }

        // Bottom navigation bar (Home / Grades / Schedule / Profile)
        BottomNavHelper.setup(this, studentId, BottomNavHelper.Tab.SCHEDULE)

        selectSemester(1)
    }

    private fun selectSemester(semester: Int) {
        selectedSemester = semester
        updateSemesterButtons()
        loadSchedule()
    }

    private fun updateSemesterButtons() {
        val primary = getColor(R.color.primary_dark)
        styleButton(btnSem1, selectedSemester == 1, primary)
        styleButton(btnSem2, selectedSemester == 2, primary)
    }

    private fun styleButton(button: MaterialButton, selected: Boolean, primary: Int) {
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(primary)
            button.setTextColor(Color.WHITE)
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(primary)
        }
    }

    override fun onResume() {
        super.onResume()
        loadSchedule()
    }

    private fun loadSchedule() {
        tlSchedule.removeAllViews()

        if (studentId.isBlank()) {
            addMessageRow("Student account not found.")
            return
        }

        val schedules = dbHelper.getStudentSchedule(studentId.trim(), selectedSemester)

        if (schedules.isEmpty()) {
            addMessageRow("No schedule available for Semester $selectedSemester.")
            return
        }

        for (item in schedules) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(createCell("${item.day}\n${item.time}", weights[0], Gravity.START))

            val units = dbHelper.getSubjectUnits(item.subjectCode)
            val subjectText = if (item.subjectName.isNotBlank()) {
                "${item.subjectCode}\n${item.subjectName} ($units Units)"
            } else "${item.subjectCode} ($units Units)"
            row.addView(createCell(subjectText, weights[1], Gravity.START))

            row.addView(createCell(item.instructor.ifBlank { "Not assigned" }, weights[2], Gravity.START))
            row.addView(createCell(item.room, weights[3], Gravity.END))

            tlSchedule.addView(row)
        }
    }

    private fun createCell(value: String, weight: Float, align: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(getColor(R.color.text_primary))
            gravity = align or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    private fun addMessageRow(message: String) {
        val textView = TextView(this).apply {
            text = message
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tlSchedule.addView(textView)
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}