
package com.example.gradetrack

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class StudentScheduleActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tlSchedule: TableLayout
    private lateinit var spinnerSemester: Spinner

    private var studentId: String = ""
    private var selectedSemester: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_schedule)

        dbHelper = DatabaseHelper(this)

        tlSchedule = findViewById(R.id.tlSchedule)
        spinnerSemester = findViewById(R.id.spinnerSemester)

        // Get the logged-in student's ID.
        studentId = intent.getStringExtra("STUDENT_ID")
            ?: intent.getStringExtra("user_id")
                    ?: ""

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupSemesterSpinner()
    }

    private fun setupSemesterSpinner() {
        val semesters = arrayOf(
            "BSIS - 1st Semester",
            "BSIS - 2nd Semester"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            semesters
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerSemester.adapter = adapter

        spinnerSemester.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedSemester = position + 1
                    loadSchedule()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // No action needed.
                }
            }
    }

    override fun onResume() {
        super.onResume()

        if (::dbHelper.isInitialized && ::tlSchedule.isInitialized) {
            loadSchedule()
        }
    }

    private fun loadSchedule() {
        tlSchedule.removeAllViews()

        if (studentId.isBlank()) {
            addMessageRow("Student account not found.")
            return
        }

        // Retrieve schedule records from DatabaseHelper.kt.
        val schedules = dbHelper.getStudentSchedule(
            studentId.trim(),
            selectedSemester
        )

        if (schedules.isEmpty()) {
            addMessageRow(
                "No schedule available for Semester $selectedSemester."
            )
            return
        }

        for (item in schedules) {
            val row = TableRow(this).apply {
                setPadding(4, 12, 4, 12)
                gravity = Gravity.CENTER_VERTICAL
            }

            // Day and time
            row.addView(
                createCell("${item.day}\n${item.time}")
            )

            // Subject code and subject name
            val subjectText = if (item.subjectName.isNotBlank()) {
                "${item.subjectCode}\n${item.subjectName}"
            } else {
                item.subjectCode
            }

            row.addView(createCell(subjectText))

            // Instructor
            row.addView(
                createCell(
                    item.instructor.ifBlank { "Not assigned" }
                )
            )

            // Classroom
            row.addView(createCell(item.room))

            tlSchedule.addView(row)
        }
    }

    private fun createCell(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 4)

            layoutParams = TableRow.LayoutParams(
                0,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }

    private fun addMessageRow(message: String) {
        val row = TableRow(this)

        val textView = TextView(this).apply {
            text = message
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(12, 24, 12, 24)

            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                span = 4
            }
        }

        row.addView(textView)
        tlSchedule.addView(row)
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}
