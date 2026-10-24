package com.example.gradetrack

import android.app.AlertDialog
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
import androidx.core.content.ContextCompat
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

    // Bilang ng rows sa taas ng tlGradeEntry na galing sa XML header.
    // 1 kung may header row (Name / Midterm / Final / Grade), 0 kung wala.
    private val headerRowCount = 1

    private data class GradeRowData(
        val studentId: String,
        val studentName: String,
        val etMidterm: EditText,
        val etFinal: EditText,
        val tvGrade: TextView
    )

    private val gradeRowList = mutableListOf<GradeRowData>()

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_management)

        dbHelper = DatabaseHelper(this)

        initViews()
        tlGradeEntry.isStretchAllColumns = true
        clearStudentRows()
        setupListeners()
        populateSections(allSections)
        showStep(1)
    }

    private fun clearStudentRows() {
        while (tlGradeEntry.childCount > headerRowCount) {
            tlGradeEntry.removeViewAt(headerRowCount)
        }
        gradeRowList.clear()
    }

    private fun initViews() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            when {
                step4Layout.visibility == View.VISIBLE -> showStep(3)
                step3Layout.visibility == View.VISIBLE -> showStep(2)
                step2Layout.visibility == View.VISIBLE -> showStep(1)
                else -> finish()
            }
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
                val filtered = allSections.filter {
                    it.lowercase(Locale.getDefault()).contains(query)
                }
                populateSections(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Step 2: Select semester
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

        // Step 4: Save grades
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
                setTextColor(color(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            card.addView(tvSection)
            layoutSectionsContainer.addView(card)
        }
    }

    /** Subjects na naka-schedule para sa section + semester. Kung wala, lahat ng subject. */
    private fun loadSubjectList(): List<Pair<String, String>> {
        val db = dbHelper.readableDatabase
        val result = mutableListOf<Pair<String, String>>()

        db.rawQuery(
            """
            SELECT DISTINCT s.code, s.subject_name
            FROM ${DatabaseHelper.TABLE_SUBJECTS} s
            JOIN ${DatabaseHelper.TABLE_CLASS_SCHEDULE} c ON c.subject_code = s.code
            WHERE c.course = ? AND c.semester = ?
            ORDER BY s.code
            """.trimIndent(),
            arrayOf(selectedSection.trim().uppercase(Locale.ROOT), selectedSemester.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Pair(cursor.getString(0), cursor.getString(1)))
            }
        }

        if (result.isEmpty()) {
            db.rawQuery(
                "SELECT code, subject_name FROM ${DatabaseHelper.TABLE_SUBJECTS} ORDER BY code",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(Pair(cursor.getString(0), cursor.getString(1)))
                }
            }
        }
        return result
    }

    private fun populateSubjects() {
        layoutSubjectsContainer.removeAllViews()

        for ((code, name) in loadSubjectList()) {
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
                    tvGradeHeader.text =
                        "Section: $selectedSection\n" +
                                "Semester: ${if (selectedSemester == 1) "1st" else "2nd"} Semester\n" +
                                "Subject: $code – $name"
                    loadGradeEntryRows(selectedSemester, code)
                    showStep(4)
                }
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val tvCode = TextView(this).apply {
                text = code
                textSize = 16f
                setTextColor(color(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            val tvName = TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(color(R.color.text_secondary))
            }

            layout.addView(tvCode)
            layout.addView(tvName)
            card.addView(layout)
            layoutSubjectsContainer.addView(card)
        }
    }

    private fun loadGradeEntryRows(semester: Int, subjectCode: String) {
        clearStudentRows()

        val db = dbHelper.readableDatabase
        val fullSection = selectedSection.trim().uppercase(Locale.ROOT)

        val students = mutableListOf<Pair<String, String>>()
        db.rawQuery(
            """
            SELECT user_id, name FROM ${DatabaseHelper.TABLE_USERS}
            WHERE role = 'STUDENT'
              AND UPPER(TRIM(COALESCE(course,'') || ' ' || COALESCE(section,''))) = ?
            ORDER BY name
            """.trimIndent(),
            arrayOf(fullSection)
        ).use { c ->
            while (c.moveToNext()) students.add(Pair(c.getString(0), c.getString(1)))
        }

        if (students.isEmpty()) {
            Toast.makeText(this, "No students found in $selectedSection", Toast.LENGTH_LONG).show()
        }

        // ID lang ang basehan ng pagiging unique. Hindi na tinatanggal ang magkapareho ng pangalan,
        // para hindi mawala ang tamang account.
        val uniqueStudents = students.distinctBy { it.first }

        for ((studentId, studentName) in uniqueStudents) {
            val (savedMidterm, savedFinal) = dbHelper.getGradeValues(studentId, subjectCode, semester)
            val midterm = savedMidterm?.let { formatGrade(it) } ?: ""
            val final = savedFinal?.let { formatGrade(it) } ?: ""

            val tvName = TextView(this).apply {
                text = "$studentName\n($studentId)"
                setTextColor(color(R.color.text_primary))
                textSize = 13f
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.5f)
            }

            val etMidterm = createGradeEditText(midterm)
            val etFinal = createGradeEditText(final)

            val tvGrade = TextView(this).apply {
                text = calculateAverage(midterm, final)
                setTextColor(color(R.color.text_primary))
                textSize = 13f
                gravity = Gravity.CENTER
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            }

            val row = TableRow(this).apply {
                tag = "student_row"
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
                isLongClickable = true
                setOnLongClickListener {
                    AlertDialog.Builder(this@GradeManagementActivity)
                        .setTitle("Delete Grade Record")
                        .setMessage("Are you sure you want to delete grades for $studentName?")
                        .setPositiveButton("Delete") { _, _ ->
                            dbHelper.deleteGrade(studentId, subjectCode, semester)
                            etMidterm.setText("")
                            etFinal.setText("")
                            Toast.makeText(
                                this@GradeManagementActivity,
                                "Grade Record Deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    tvGrade.text = calculateAverage(
                        etMidterm.text.toString(),
                        etFinal.text.toString()
                    )
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etMidterm.addTextChangedListener(watcher)
            etFinal.addTextChangedListener(watcher)

            row.addView(tvName)
            row.addView(etMidterm)
            row.addView(etFinal)
            row.addView(tvGrade)
            tlGradeEntry.addView(row)

            gradeRowList.add(GradeRowData(studentId, studentName, etMidterm, etFinal, tvGrade))
        }
    }

    private fun formatGrade(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else v.toString()

    private fun createGradeEditText(initialValue: String): EditText {
        return EditText(this).apply {
            text = Editable.Factory.getInstance().newEditable(initialValue)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.text_primary))
            setHintTextColor(color(R.color.text_secondary))
            hint = "0-100"
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun calculateAverage(mStr: String, fStr: String): String {
        val grades = listOfNotNull(
            mStr.trim().toDoubleOrNull(),
            fStr.trim().toDoubleOrNull()
        )
        if (grades.isEmpty()) return "-"

        val average = grades.sum() / grades.size
        return String.format(Locale.US, "%.2f", average)
    }

    private fun saveAllGrades() {
        if (gradeRowList.isEmpty()) {
            Toast.makeText(this, "No students to save", Toast.LENGTH_SHORT).show()
            return
        }

        // I-validate muna lahat para walang half-saved
        for (row in gradeRowList) {
            val m = row.etMidterm.text.toString().trim()
            val f = row.etFinal.text.toString().trim()

            for ((label, value, field) in listOf(
                Triple("Midterm", m, row.etMidterm),
                Triple("Final", f, row.etFinal)
            )) {
                if (value.isEmpty()) continue
                val num = value.toDoubleOrNull()
                if (num == null || num < 0.0 || num > 100.0) {
                    field.error = "0 - 100 only"
                    field.requestFocus()
                    Toast.makeText(
                        this,
                        "Invalid $label grade for ${row.studentName}",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
        }

        val db = dbHelper.writableDatabase
        var failed = 0

        db.beginTransaction()
        try {
            for (row in gradeRowList) {
                val midterm = row.etMidterm.text.toString().trim().toDoubleOrNull()
                val final = row.etFinal.text.toString().trim().toDoubleOrNull()

                val ok = dbHelper.saveGrade(
                    studentId = row.studentId,
                    subjectCode = selectedSubjectCode,
                    subjectName = selectedSubjectName,
                    midterm = midterm,
                    final = final,
                    semester = selectedSemester
                )
                if (!ok) failed++
            }
            if (failed == 0) db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        if (failed == 0) {
            Toast.makeText(this, "Grades Saved & Submitted Successfully!", Toast.LENGTH_SHORT).show()

            // Debug: ilista sa Logcat (filter: GRADES) ang laman ng grades table
            dbHelper.debugDumpGrades()

            // I-reload mula sa database para makita agad na talagang na-save
            loadGradeEntryRows(selectedSemester, selectedSubjectCode)
        } else {
            Toast.makeText(this, "Failed to save $failed record(s). Nothing was saved.", Toast.LENGTH_LONG).show()
        }
    }
}