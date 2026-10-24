package com.example.gradetrack

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ManageSubjectsActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var layoutScheduleContainer: LinearLayout

    private lateinit var etScheduleTime: EditText
    private lateinit var etScheduleDay: EditText
    private lateinit var etScheduleSubject: EditText
    private lateinit var etScheduleInstructor: EditText
    private lateinit var etScheduleRoom: EditText

    private lateinit var btnSaveSchedule: Button
    private lateinit var btnCancelEdit: Button
    private lateinit var tvFormTitle: TextView

    // Mga dropdown components
    private lateinit var autoCompleteSemester: AutoCompleteTextView
    private lateinit var autoCompleteSection: AutoCompleteTextView

    private var editingClassId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_subjects)

        dbHelper = DatabaseHelper(this)

        layoutScheduleContainer = findViewById(R.id.layoutScheduleContainer)

        etScheduleTime = findViewById(R.id.etScheduleTime)
        etScheduleDay = findViewById(R.id.etScheduleDay)
        etScheduleSubject = findViewById(R.id.etScheduleSubject)
        etScheduleInstructor = findViewById(R.id.etScheduleInstructor)
        etScheduleRoom = findViewById(R.id.etScheduleRoom)

        btnSaveSchedule = findViewById(R.id.btnSaveSchedule)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)
        tvFormTitle = findViewById(R.id.tvFormTitle)

        autoCompleteSemester = findViewById(R.id.spinnerSemester)
        autoCompleteSection = findViewById(R.id.spinnerYearLevel)

        // Setup Semester Dropdown Options
        val semesterOptions = arrayOf("1st Semester", "2nd Semester", "Summer")
        val semesterAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesterOptions)
        autoCompleteSemester.setAdapter(semesterAdapter)
        autoCompleteSemester.setText(semesterOptions[0], false) // Default value

        // Setup Section Filter Options (para sa Schedule Directory)
        val sectionOptions = arrayOf("BSIS 2A", "BSIS 2B", "BSIS 2C", "BSIS 2D")
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sectionOptions)
        autoCompleteSection.setAdapter(sectionAdapter)
        autoCompleteSection.setText(sectionOptions[0], false) // Default value

        // Listener kapag nagpalit ng Semester
        autoCompleteSemester.setOnItemClickListener { _, _, _, _ ->
            loadSchedules()
        }

        // Listener kapag nagpalit ng Section
        autoCompleteSection.setOnItemClickListener { _, _, _, _ ->
            loadSchedules()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSaveSchedule.setOnClickListener {
            saveScheduleEntry()
        }

        btnCancelEdit.setOnClickListener {
            resetForm()
        }

        resetForm()
        loadSchedules()
    }

    private fun resetForm() {
        editingClassId = -1L

        tvFormTitle.text = "ADD SCHEDULE ENTRY"
        btnSaveSchedule.text = "Save Schedule Entry"
        btnCancelEdit.visibility = View.GONE

        etScheduleTime.text.clear()
        etScheduleDay.text.clear()
        etScheduleSubject.text.clear()
        etScheduleInstructor.text.clear()
        etScheduleRoom.text.clear()
    }

    private fun saveScheduleEntry() {
        val time = etScheduleTime.text.toString().trim()
        val day = etScheduleDay.text.toString().trim()
        val subjectCode = etScheduleSubject.text.toString().trim()
        val instructorName = etScheduleInstructor.text.toString().trim()
        val room = etScheduleRoom.text.toString().trim()

        val targetCourse = autoCompleteSection.text.toString().trim()

        // Kunin ang Semester integer value (1 para sa 1st, 2 para sa 2nd, etc.)
        val selectedSemesterStr = autoCompleteSemester.text.toString()
        val semesterValue = when {
            selectedSemesterStr.contains("2nd") -> 2
            selectedSemesterStr.contains("Summer") -> 3
            else -> 1
        }

        if (time.isEmpty() || day.isEmpty() || subjectCode.isEmpty() || instructorName.isEmpty() || room.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val facultyId = "FAC2026-001" // Default fallback or query lookup

        val result = if (editingClassId == -1L) {
            dbHelper.addClass(
                facultyId = facultyId,
                subjectCode = subjectCode,
                course = targetCourse,
                day = day,
                time = time,
                room = room,
                semester = semesterValue
            )
        } else {
            dbHelper.updateClass(
                classId = editingClassId,
                facultyId = facultyId,
                subjectCode = subjectCode,
                course = targetCourse,
                day = day,
                time = time,
                room = room,
                semester = semesterValue
            )
        }

        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()

        if (result.success) {
            resetForm()
            loadSchedules()
        }
    }

    private fun loadSchedules() {
        layoutScheduleContainer.removeAllViews()

        val semester = getSelectedSemester()
        val selectedSection = autoCompleteSection.text.toString().trim()
        val schedules = dbHelper.getClassesBySection(selectedSection, semester)

        if (schedules.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No schedules found for $selectedSection (${autoCompleteSemester.text})."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
                setTextColor(getColor(R.color.text_secondary))
            }
            layoutScheduleContainer.addView(emptyView)
            return
        }

        for (item in schedules) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 16, 12, 16)
            }

            fun addCell(
                parent: LinearLayout,
                value: String,
                weight: Float,
                bold: Boolean = false,
                colorHex: String = "#1E293B",
                gravity: Int = Gravity.START
            ) {
                val tv = TextView(this).apply {
                    text = value
                    textSize = 12f
                    setTextColor(Color.parseColor(colorHex))
                    this.gravity = gravity
                    if (bold) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        weight
                    )
                }
                parent.addView(tv)
            }

            // TIME
            addCell(row, "${item.day}\n${item.time}", 1.1f)
            // SUBJECT
            addCell(row, "${item.subjectCode}\n${item.subjectName}", 1.1f, true)
            // INSTRUCTOR
            addCell(row, item.instructor.ifBlank { "Unassigned" }, 1.2f)
            // ROOM
            addCell(row, item.room, 0.7f, bold = true, colorHex = "#2563EB")

            // ACTION (Inline Edit & Del links)
            val actionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.7f
                )
            }

            val tvEdit = TextView(this).apply {
                text = "Edit"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 0, 10, 0)
                setOnClickListener { editScheduleItem(item) }
            }

            val tvDel = TextView(this).apply {
                text = "Del"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E53935"))
                setOnClickListener { showDeleteDialog(item.id, item.subjectCode) }
            }

            actionLayout.addView(tvEdit)
            actionLayout.addView(tvDel)
            row.addView(actionLayout)

            layoutScheduleContainer.addView(row)

            // Divider Line
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(Color.parseColor("#E2E8F0"))
            }
            layoutScheduleContainer.addView(divider)
        }
    }

    private fun editScheduleItem(item: ScheduleItem) {
        editingClassId = item.id

        etScheduleTime.setText(item.time)
        etScheduleDay.setText(item.day)
        etScheduleSubject.setText(item.subjectCode)
        etScheduleInstructor.setText(item.instructor)
        etScheduleRoom.setText(item.room)

        val semText = when (item.semester) {
            2 -> "2nd Semester"
            3 -> "Summer"
            else -> "1st Semester"
        }
        autoCompleteSemester.setText(semText, false)

        tvFormTitle.text = "EDIT SCHEDULE ENTRY"
        btnSaveSchedule.text = "Update Schedule Entry"
        btnCancelEdit.visibility = View.VISIBLE
        etScheduleTime.requestFocus()
    }

    private fun getSelectedSemester(): Int {
        return when (autoCompleteSemester.text.toString().trim()) {
            "2nd Semester" -> 2
            "Summer" -> 3
            else -> 1
        }
    }

    private fun showDeleteDialog(id: Long, subjectCode: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete the schedule for $subjectCode?")
            .setPositiveButton("Delete") { _, _ ->
                val success = dbHelper.deleteClass(id)
                if (success) {
                    if (editingClassId == id) resetForm()
                    Toast.makeText(this, "Schedule deleted successfully.", Toast.LENGTH_SHORT).show()
                    loadSchedules()
                } else {
                    Toast.makeText(this, "Failed to delete schedule.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}