package com.example.gradetrack

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ManageScheduleActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tlManageSchedule: TableLayout
    private lateinit var spinnerFilterYear: Spinner

    private var facultyId: String = ""
    private var selectedSectionFilter: String = "All Years / Sections"

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
        tlManageSchedule = findViewById(R.id.tlManageSchedule)
        spinnerFilterYear = findViewById(R.id.spinnerFilterYear)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddSchedule).setOnClickListener { showScheduleDialog(null) }

        // Filter by section
        val years = arrayOf("All Years / Sections", "BSIS 2A", "BSIS 2B", "BSIS 2C", "BSIS 2D")
        spinnerFilterYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerFilterYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedSectionFilter = years[position]
                loadSchedules()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadSchedules() {
        tlManageSchedule.removeAllViews()

        // Schedule na tinuturo ng faculty na naka-login
        val all = dbHelper.getClassesByFaculty(facultyId)
        val schedules = if (selectedSectionFilter == "All Years / Sections") {
            all
        } else {
            all.filter { it.course.trim().equals(selectedSectionFilter.trim(), ignoreCase = true) }
        }

        if (schedules.isEmpty()) {
            tlManageSchedule.addView(TextView(this).apply {
                text = "No schedule yet. Tap \"Add Schedule Entry\" to add one."
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
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f)
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
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.1f)
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

            // Column 3: Course / Section
            val tvCourse = TextView(this).apply {
                text = item.course
                setTextColor(getColor(R.color.text_primary))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
            row.addView(tvCourse)

            // Column 4: Room
            val tvRoom = TextView(this).apply {
                text = item.room
                setTextColor(getColor(R.color.primary_dark))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.6f).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            row.addView(tvRoom)

            // Column 5: Action (Inline Edit & Del links)
            val actionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.7f).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
            }

            val tvEdit = TextView(this).apply {
                text = "Edit"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 0, 10, 0)
                setOnClickListener { showScheduleDialog(item) }
            }

            val tvDel = TextView(this).apply {
                text = "Del"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E53935"))
                setPadding(0, 0, 0, 0)
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

        val etCourse = EditText(this).apply {
            hint = "Course / Section (e.g. BSIS 2A)"
            setText(item?.course ?: "")
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
        layout.addView(etCourse)
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
                val course = etCourse.text.toString().trim()
                val time = etTime.text.toString().trim()
                val room = etRoom.text.toString().trim()

                val result = if (item != null) {
                    dbHelper.updateClass(item.id, facultyId, code, course, day, time, room)
                } else {
                    dbHelper.addClass(facultyId, code, course, day, time, room)
                }

                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                if (result.success) {
                    dialog.dismiss()
                    loadSchedules()
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
}