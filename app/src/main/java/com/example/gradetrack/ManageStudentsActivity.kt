package com.example.gradetrack

import android.app.AlertDialog
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ManageStudentsActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tlStudents: TableLayout
    private var facultyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_students)

        facultyId = intent.getStringExtra("FACULTY_ID")?.trim().orEmpty()
        FacultyBottomNavHelper.setup(this, facultyId, FacultyBottomNavHelper.Tab.STUDENTS)

        dbHelper = DatabaseHelper(this)
        tlStudents = findViewById(R.id.tlStudents)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnAddStudent).setOnClickListener {
            showStudentDialog(null)
        }

        loadStudents()
    }

    private fun loadStudents() {
        // Corrected syntax for removing dynamic rows while keeping header row
        if (tlStudents.childCount > 1) {
            tlStudents.removeViews(1, tlStudents.childCount - 1)
        }

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT MIN(user_id) AS user_id, name, MAX(course) AS course FROM ${DatabaseHelper.TABLE_USERS} WHERE role = 'STUDENT' GROUP BY LOWER(name) ORDER BY name ASC",
            null
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("user_id"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val course = cursor.getString(cursor.getColumnIndexOrThrow("course")) ?: "N/A"

            val row = TableRow(this).apply {
                setPadding(0, 8, 0, 8)
                isClickable = true
                setOnClickListener {
                    showStudentOptionsDialog(id, name, course)
                }
            }

            val tvId = TextView(this).apply {
                text = id
                setTextColor(getColor(R.color.text_primary))
                setPadding(4, 4, 4, 4)
            }
            val tvName = TextView(this).apply {
                text = name
                setTextColor(getColor(R.color.text_primary))
                setPadding(4, 4, 4, 4)
            }
            val tvCourse = TextView(this).apply {
                text = course
                setTextColor(getColor(R.color.text_primary))
                setPadding(4, 4, 4, 4)
            }

            row.addView(tvId)
            row.addView(tvName)
            row.addView(tvCourse)

            tlStudents.addView(row)
        }
        cursor.close()
    }

    private fun showStudentOptionsDialog(userId: String, currentName: String, currentCourse: String) {
        val options = arrayOf("Edit (Update)", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Manage Student: $currentName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showStudentDialog(userId, currentName, currentCourse)
                    1 -> deleteStudent(userId)
                }
            }
            .show()
    }

    private fun showStudentDialog(userId: String?, currentName: String = "", currentCourse: String = "") {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etId = EditText(this).apply {
            hint = "Student ID (e.g. 2024-002)"
            setText(userId ?: "")
            isEnabled = userId == null
        }
        val etName = EditText(this).apply {
            hint = "Full Name"
            setText(currentName)
        }
        val etCourse = EditText(this).apply {
            hint = "Course (e.g. BSIT)"
            setText(currentCourse)
        }

        layout.addView(etId)
        layout.addView(etName)
        layout.addView(etCourse)

        val isEdit = userId != null

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Student" else "Add New Student")
            .setView(layout)
            .setPositiveButton(if (isEdit) "Update" else "Add") { _, _ ->
                val id = etId.text.toString().trim()
                val name = etName.text.toString().trim()
                val course = etCourse.text.toString().trim()

                if (id.isEmpty() || name.isEmpty()) {
                    Toast.makeText(this, "ID and Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val db = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put("user_id", id)
                    put("name", name)
                    put("role", "STUDENT")
                    put("course", course)
                    put("password", "1234")
                }

                if (isEdit) {
                    val rowsUpdated = db.update(DatabaseHelper.TABLE_USERS, values, "user_id = ?", arrayOf(id))
                    if (rowsUpdated > 0) {
                        Toast.makeText(this, "Student Updated Successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val checkCursor = db.rawQuery(
                        "SELECT id FROM ${DatabaseHelper.TABLE_USERS} WHERE (user_id = ? OR LOWER(name) = LOWER(?)) AND role = 'STUDENT'",
                        arrayOf(id, name)
                    )
                    if (checkCursor.moveToFirst()) {
                        checkCursor.close()
                        Toast.makeText(this, "Student with this ID or Name already exists", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    checkCursor.close()

                    val res = db.insertWithOnConflict(
                        DatabaseHelper.TABLE_USERS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (res != -1L) {
                        Toast.makeText(this, "Student Added Successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Student ID already exists", Toast.LENGTH_SHORT).show()
                    }
                }
                loadStudents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteStudent(userId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to delete student $userId?")
            .setPositiveButton("Delete") { _, _ ->
                val db = dbHelper.writableDatabase
                db.delete(DatabaseHelper.TABLE_USERS, "user_id = ?", arrayOf(userId))
                Toast.makeText(this, "Student Deleted", Toast.LENGTH_SHORT).show()
                loadStudents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}