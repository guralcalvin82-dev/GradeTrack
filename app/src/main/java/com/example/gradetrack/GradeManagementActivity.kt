package com.example.gradetrack

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.util.Locale

class GradeManagementActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    // Step Layouts
    private lateinit var step1Layout: LinearLayout
    private lateinit var step2Layout: LinearLayout
    private lateinit var step3Layout: LinearLayout
    private lateinit var step4Layout: LinearLayout

    // Step 1
    private lateinit var etSearchSection: EditText
    private lateinit var layoutSectionsContainer: LinearLayout

    // Step 2
    private lateinit var tvSectionInfo: TextView
    private lateinit var btnSem1: Button
    private lateinit var btnSem2: Button

    // Step 3
    private lateinit var tvSemesterHeader: TextView
    private lateinit var layoutSubjectsContainer: LinearLayout

    // Step 4
    private lateinit var tvGradeHeader: TextView
    private lateinit var tlGradeEntry: TableLayout
    private lateinit var btnSaveGrades: Button

    // State
    private var selectedSection = "BSIS 2C"
    private var selectedSemester = 1
    private var selectedSubjectCode = "CT-MAD 213"
    private var selectedSubjectName = "Mobile Application"

    private val allSections = listOf("BSIS 2A", "BSIS 2B", "BSIS 2C", "BSIS 2D")

    // Data holder for student grade rows in step 4
    private data class GradeRowData(
        val studentId: String,
        val studentName: String,
        val etMidterm: EditText,
        val etFinal: EditText,
        val tvGrade: TextView
    )
    private val gradeRowList = mutableListOf<GradeRowData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_management)

        dbHelper = DatabaseHelper(this)

        initViews()
        setupListeners()
        populateSections(allSections)
        showStep(1)
    }

    private fun initViews() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (step4Layout.visibility == View.VISIBLE) showStep(3)
            else if (step3Layout.visibility == View.VISIBLE) showStep(2)
            else if (step2Layout.visibility == View.VISIBLE) showStep(1)
            else finish()
        }

        step1Layout = findViewById(R.id.step1Layout)
        step2Layout = findViewById(R.id.step2Layout)
        step3Layout = findViewById(R.id.step3Layout)
        step4Layout = findViewById(R.id.step4Layout)

        etSearchSection = findViewById(R.id.etSearchSection)
        layoutSectionsContainer = findViewById(R.id.layoutSectionsContainer)

        tvSectionInfo = findViewById(R.id.tvSectionInfo)
        btnSem1 = findViewById(R.id.btnSem1)
        btnSem2 = findViewById(R.id.btnSem2)

        tvSemesterHeader = findViewById(R.id.tvSemesterHeader)
        layoutSubjectsContainer = findViewById(R.id.layoutSubjectsContainer)

        tvGradeHeader = findViewById(R.id.tvGradeHeader)
        tlGradeEntry = findViewById(R.id.tlGradeEntry)
        btnSaveGrades = findViewById(R.id.btnSaveGrades)
    }

    private fun setupListeners() {
        // Step 1: Search section
        etSearchSection.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase(Locale.getDefault())
                val filtered = allSections.filter { it.lowercase(Locale.getDefault()).contains(query) }
                populateSections(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Step 2: Select Semester
        btnSem1.setOnClickListener {
            selectedSemester = 1
            tvSemesterHeader.text = "$selectedSection\n1st Semester\n\nSELECT SUBJECT"
            populateSubjects()
            showStep(3)
        }

        btnSem2.setOnClickListener {
            selectedSemester = 2
            tvSemesterHeader.text = "$selectedSection\n2nd Semester\n\nSELECT SUBJECT"
            populateSubjects()
            showStep(3)
        }

        // Step 4: Save Grades Button
        btnSaveGrades.setOnClickListener {
            saveAllGrades()
        }
    }

    private fun showStep(step: Int) {
        step1Layout.visibility = if (step == 1) View.VISIBLE else View.GONE
        step2Layout.visibility = if (step == 2) View.VISIBLE else View.GONE
        step3Layout.visibility = if (step == 3) View.VISIBLE else View.GONE
        step4Layout.visibility = if (step == 4) View.VISIBLE else View.GONE
    }

    private fun populateSections(sections: List<String>) {
        layoutSectionsContainer.removeAllViews()

        for (section in sections) {
            val card = CardView(this).apply {
                radius = 12f
                cardElevation = 2f
                setContentPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    selectedSection = section
                    tvSectionInfo.text = section
                    showStep(2)
                }
            }

            val tvSection = TextView(this).apply {
                text = section
                textSize = 16f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            card.addView(tvSection)
            layoutSectionsContainer.addView(card)
        }
    }

    private fun populateSubjects() {
        layoutSubjectsContainer.removeAllViews()

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT code, subject_name FROM ${DatabaseHelper.TABLE_SUBJECTS}", null)

        while (cursor.moveToNext()) {
            val code = cursor.getString(0)
            val name = cursor.getString(1)

            val card = CardView(this).apply {
                radius = 12f
                cardElevation = 2f
                setContentPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    selectedSubjectCode = code
                    selectedSubjectName = name
                    tvGradeHeader.text = "Section: $selectedSection\nSemester: ${if (selectedSemester == 1) "1st" else "2nd"} Semester\nSubject: $code – $name"
                    loadGradeEntryRows(selectedSemester, code, name)
                    showStep(4)
                }
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val tvCode = TextView(this).apply {
                text = code
                textSize = 16f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            val tvName = TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
            }

            layout.addView(tvCode)
            layout.addView(tvName)
            card.addView(layout)

            layoutSubjectsContainer.addView(card)
        }
        cursor.close()
    }

    private fun loadGradeEntryRows(semester: Int, subjectCode: String, subjectName: String) {
        if (tlGradeEntry.childCount > 1) {
            tlGradeEntry.removeViews(1, tlGradeEntry.childCount - 1)
        }
        gradeRowList.clear()

        val db = dbHelper.readableDatabase
        val sectionNameOnly = selectedSection.replace("BSIS ", "").trim()
        val studentCursor = db.rawQuery(
            "SELECT user_id, name FROM ${DatabaseHelper.TABLE_USERS} WHERE role = 'STUDENT' AND section = ?",
            arrayOf(sectionNameOnly)
        )

        while (studentCursor.moveToNext()) {
            val studentId = studentCursor.getString(0)
            val studentName = studentCursor.getString(1)

            var midterm = ""
            var final = ""

            val gradeCursor = db.rawQuery(
                "SELECT midterm, final FROM ${DatabaseHelper.TABLE_GRADES} WHERE student_id = ? AND subject_code = ? AND semester = ?",
                arrayOf(studentId, subjectCode, semester.toString())
            )
            if (gradeCursor.moveToFirst()) {
                midterm = if (gradeCursor.getDouble(0) > 0.0) gradeCursor.getDouble(0).toString() else ""
                final = if (gradeCursor.getDouble(1) > 0.0) gradeCursor.getDouble(1).toString() else ""
            }
            gradeCursor.close()

            val tvName = TextView(this).apply {
                text = studentName
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.5f)
            }

            val etMidterm = createGradeEditText(midterm)
            val etFinal = createGradeEditText(final)

            val row = TableRow(this).apply {
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
                isLongClickable = true
                setOnLongClickListener {
                    AlertDialog.Builder(this@GradeManagementActivity)
                        .setTitle("Delete Grade Record")
                        .setMessage("Are you sure you want to delete grades for $studentName?")
                        .setPositiveButton("Delete") { _, _ ->
                            val writeDb = dbHelper.writableDatabase
                            writeDb.delete(
                                DatabaseHelper.TABLE_GRADES,
                                "student_id = ? AND subject_code = ? AND semester = ?",
                                arrayOf(studentId, subjectCode, semester.toString())
                            )
                            etMidterm.setText("")
                            etFinal.setText("")
                            Toast.makeText(this@GradeManagementActivity, "Grade Record Deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }

            val tvGrade = TextView(this).apply {
                text = calculateAverage(midterm, final)
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                gravity = Gravity.CENTER
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val m = etMidterm.text.toString()
                    val f = etFinal.text.toString()
                    tvGrade.text = calculateAverage(m, f)
                }
                override fun afterTextChanged(s: Editable?) {}
            }

            etMidterm.addTextChangedListener(textWatcher)
            etFinal.addTextChangedListener(textWatcher)

            row.addView(tvName)
            row.addView(etMidterm)
            row.addView(etFinal)
            row.addView(tvGrade)

            tlGradeEntry.addView(row)
            gradeRowList.add(GradeRowData(studentId, studentName, etMidterm, etFinal, tvGrade))
        }
        studentCursor.close()
    }

    private fun createGradeEditText(initialValue: String): EditText {
        return EditText(this).apply {
            text = Editable.Factory.getInstance().newEditable(initialValue)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun calculateAverage(mStr: String, fStr: String): String {
        val grades = listOfNotNull(
            mStr.toDoubleOrNull(),
            fStr.toDoubleOrNull()
        )

        if (grades.isEmpty()) {
            return "-"
        }

        val average = grades.sum() / grades.size
        return String.format(Locale.getDefault(), "%.2f", average)
    }

    private fun saveAllGrades() {
        val db = dbHelper.writableDatabase
        for (rowData in gradeRowList) {
            val midterm = rowData.etMidterm.text.toString().toDoubleOrNull() ?: 0.0
            val final = rowData.etFinal.text.toString().toDoubleOrNull() ?: 0.0

            val cursor = db.rawQuery(
                "SELECT id FROM ${DatabaseHelper.TABLE_GRADES} WHERE student_id = ? AND subject_code = ? AND semester = ?",
                arrayOf(rowData.studentId, selectedSubjectCode, selectedSemester.toString())
            )

            val values = ContentValues().apply {
                put("student_id", rowData.studentId)
                put("subject_code", selectedSubjectCode)
                put("subject_name", selectedSubjectName)
                put("prelim", 0.0)
                put("midterm", midterm)
                put("final", final)
                put("semester", selectedSemester)
            }

            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                db.update(DatabaseHelper.TABLE_GRADES, values, "id = ?", arrayOf(id.toString()))
            } else {
                db.insert(DatabaseHelper.TABLE_GRADES, null, values)
            }
            cursor.close()
        }

        Toast.makeText(this, "Grades Saved & Submitted Successfully!", Toast.LENGTH_SHORT).show()
        showStep(1)
    }
}