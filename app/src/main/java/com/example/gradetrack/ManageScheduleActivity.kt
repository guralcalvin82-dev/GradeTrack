package com.example.gradetrack

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ManageScheduleActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tlManageSchedule: TableLayout
    private lateinit var spinnerFilterYear: Spinner
    private lateinit var btnSem1: MaterialButton
    private lateinit var btnSem2: MaterialButton

    private var facultyId: String = ""
    private var facultyName: String = ""

    // Listahan ng sections (ginagamit sa filter at sa add/edit dialog)
    private val sections = arrayOf("BSIS 2A", "BSIS 2B", "BSIS 2C", "BSIS 2D")
    private var selectedSectionFilter: String = sections[0]

    // Semester labels (index 0 = 1st, index 1 = 2nd). Int ang semester sa database: 1 o 2.
    private val semesters = arrayOf("1st Semester", "2nd Semester")
    private var selectedSemester: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_schedule)

        // Galing sa FacultyDashboardActivity: intent.putExtra("FACULTY_ID", facultyId)
        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        if (facultyId.isEmpty()) {
            Toast.makeText(this, "Faculty ID missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        dbHelper = DatabaseHelper(this)
        facultyName = dbHelper.getUserName(facultyId)
        tlManageSchedule = findViewById(R.id.tlManageSchedule)
        spinnerFilterYear = findViewById(R.id.spinnerFilterYear)
        btnSem1 = findViewById(R.id.btnSem1)
        btnSem2 = findViewById(R.id.btnSem2)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddSchedule).setOnClickListener { showScheduleDialog(null) }

        // Semester buttons (1ST SEMESTER / 2ND SEMESTER)
        btnSem1.setOnClickListener { selectSemester(0) }
        btnSem2.setOnClickListener { selectSemester(1) }
        styleSemesterButtons()

        // Filter by section
        spinnerFilterYear.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        spinnerFilterYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedSectionFilter = sections[position]
                loadSchedules()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // Palitan ang napiling semester at i-reload ang listahan
    private fun selectSemester(index: Int) {
        selectedSemester = index + 1
        styleSemesterButtons()
        loadSchedules()
    }

    // Napili: filled (primary_dark, white text). Hindi napili: outline lang (primary_dark text)
    private fun styleSemesterButtons() {
        val sem1Selected = selectedSemester == 1
        listOf(btnSem1 to sem1Selected, btnSem2 to !sem1Selected).forEach { (btn, selected) ->
            btn.backgroundTintList = ColorStateList.valueOf(
                if (selected) getColor(R.color.primary_dark) else Color.TRANSPARENT
            )
            btn.setTextColor(if (selected) Color.WHITE else getColor(R.color.primary_dark))
        }
    }

    private fun loadSchedules() {
        tlManageSchedule.removeAllViews()

        // Parehong source ng student schedule (section + semester),
        // pero tanging klase ng naka-login na faculty ang lalabas at ma-ma-manage
        val schedules = dbHelper.getClassesBySection(selectedSectionFilter, selectedSemester)
            .filter {
                it.facultyId.equals(facultyId, ignoreCase = true) ||
                        (facultyName.isNotBlank() && it.instructor.equals(facultyName, ignoreCase = true))
            }

        if (schedules.isEmpty()) {
            tlManageSchedule.addView(TextView(this).apply {
                text = "No schedule for $selectedSectionFilter (${semesters[selectedSemester - 1]}) yet. Tap \"Add Schedule Entry\" to add one."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(12, 24, 12, 24)
            })
            return
        }

        for (item in schedules) {
            val row = TableRow(this).apply {
                setPadding(8, 14, 8, 14)
                gravity = Gravity.CENTER_VERTICAL
            }

            // Column 1: Time & Day
            val timeLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            timeLayout.addView(TextView(this).apply {
                text = item.time
                setTextColor(getColor(R.color.text_primary))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            })
            timeLayout.addView(TextView(this).apply {
                text = item.day
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
            })
            row.addView(timeLayout)

            // Column 2: Subject code & name
            val subjectLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.5f)
            }
            subjectLayout.addView(TextView(this).apply {
                text = item.subjectCode
                setTextColor(getColor(R.color.text_primary))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            })
            subjectLayout.addView(TextView(this).apply {
                text = item.subjectName
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
            })
            row.addView(subjectLayout)

            // Column 3: Room
            val tvRoom = TextView(this).apply {
                text = item.room
                setTextColor(getColor(R.color.primary_dark))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.7f).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            row.addView(tvRoom)

            // Column 4: Action (Edit & Del) - sariling klase lang ng faculty
            val actionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
            }

            val tvEdit = TextView(this).apply {
                text = "Edit"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.text_primary))
                setPadding(12, 16, 12, 16)
                setOnClickListener { showScheduleDialog(item) }
            }

            val tvDel = TextView(this).apply {
                text = "Del"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E53935"))
                setPadding(12, 16, 12, 16)
                setOnClickListener { deleteSchedule(item.id) }
            }

            actionLayout.addView(tvEdit)
            actionLayout.addView(tvDel)
            row.addView(actionLayout)

            tlManageSchedule.addView(row)

            // Divider Line
            tlManageSchedule.addView(View(this).apply {
                layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(getColor(R.color.border_color))
            })
        }
    }

    // item == null -> Add, may laman -> Edit
    private fun showScheduleDialog(item: ScheduleItem?) {
        val subjects = dbHelper.getAllSubjects()
        if (subjects.isEmpty()) {
            Toast.makeText(this, "No subjects available. Add a subject first.", Toast.LENGTH_LONG).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Subject
        val spSubject = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ManageScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                subjects.map { "${it.code} - ${it.subjectName}" }
            )
            val index = subjects.indexOfFirst { it.code == item?.subjectCode }
            if (index >= 0) setSelection(index)
        }

        // Course / Section (spinner para laging tugma sa filter)
        val spCourse = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ManageScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                sections
            )
            // Edit: gamitin ang course ng item. Add: gamitin ang kasalukuyang filter.
            val target = (item?.course ?: selectedSectionFilter).trim()
            val index = sections.indexOfFirst { it.equals(target, ignoreCase = true) }
            if (index >= 0) setSelection(index)
        }

        // Semester (Edit: semester ng item. Add: kasalukuyang napiling semester)
        val spSemester = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ManageScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                semesters
            )
            val index = (item?.semester ?: selectedSemester) - 1
            if (index in semesters.indices) setSelection(index)
        }

        // Day
        val spDay = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ManageScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                DatabaseHelper.DAYS
            )
            val index = DatabaseHelper.DAYS.indexOfFirst { it.equals(item?.day, ignoreCase = true) }
            if (index >= 0) setSelection(index)
        }

        val etTime = EditText(this).apply {
            hint = "Time (e.g. 10:00 AM - 12:00 PM)"
            setText(item?.time ?: "")
        }
        val etRoom = EditText(this).apply {
            hint = "Room (e.g. Comlab 3)"
            setText(item?.room ?: "")
        }

        layout.addView(spSubject)
        layout.addView(spCourse)
        layout.addView(spSemester)
        layout.addView(spDay)
        layout.addView(etTime)
        layout.addView(etRoom)

        val isEdit = item != null
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Schedule Entry" else "Add Schedule Entry")
            .setView(layout)
            .setPositiveButton(if (isEdit) "Update" else "Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = subjects[spSubject.selectedItemPosition].code
                val day = spDay.selectedItem as String
                val course = spCourse.selectedItem as String
                val semester = spSemester.selectedItemPosition + 1
                val time = etTime.text.toString().trim()
                val room = etRoom.text.toString().trim()

                val result = if (item != null) {
                    // Gamitin ang faculty ng klase mismo, hindi ng naka-login,
                    // para hindi mapalitan ang instructor ng klase ng iba (hal. Kyle).
                    dbHelper.updateClass(
                        item.id, item.facultyId.ifBlank { facultyId },
                        code, course, day, time, room, semester
                    )
                } else {
                    dbHelper.addClass(facultyId, code, course, day, time, room, semester)
                }

                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                if (result.success) {
                    dialog.dismiss()

                    // Ilipat ang napiling semester sa semester ng na-save na schedule
                    if (semester != selectedSemester) {
                        selectedSemester = semester
                        styleSemesterButtons()
                    }

                    // Ilipat ang filter sa section ng na-save na schedule para agad makita
                    val savedIndex = sections.indexOfFirst { it.equals(course, ignoreCase = true) }
                    if (savedIndex >= 0 && sections[savedIndex] != selectedSectionFilter) {
                        spinnerFilterYear.setSelection(savedIndex) // tatawag ng loadSchedules()
                    } else {
                        loadSchedules()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun deleteSchedule(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete Schedule")
            .setMessage("Delete this schedule entry? It will also be removed from the students' schedules.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteClass(id)
                Toast.makeText(this, "Schedule Deleted", Toast.LENGTH_SHORT).show()
                loadSchedules()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}