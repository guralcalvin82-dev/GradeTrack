package com.example.gradetrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FacultyProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var facultyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_profile)

        dbHelper = DatabaseHelper(this)

        facultyId = intent.getStringExtra("FACULTY_ID")?.trim().orEmpty()
        if (facultyId.isEmpty()) {
            Toast.makeText(this, "Faculty ID missing. Please log in again.", Toast.LENGTH_LONG).show()
            logout()
            return
        }

        FacultyBottomNavHelper.setup(this, facultyId, FacultyBottomNavHelper.Tab.PROFILE)

        findViewById<TextView>(R.id.tvFacultyId).text = facultyId

        loadFacultyProfile()

        findViewById<View>(R.id.btnLogout).setOnClickListener { logout() }
    }

    override fun onResume() {
        super.onResume()
        if (facultyId.isNotEmpty()) {
            loadFacultyProfile()
        }
    }

    private fun loadFacultyProfile() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT name, 
                   COALESCE(email, ''), 
                   COALESCE(department, ''), 
                   COALESCE(course, ''), 
                   COALESCE(position, '') 
            FROM ${DatabaseHelper.TABLE_USERS} 
            WHERE LOWER(user_id) = LOWER(?)
            """.trimIndent(),
            arrayOf(facultyId)
        )
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0) ?: ""
            val email = cursor.getString(1) ?: ""
            val dept = cursor.getString(2) ?: ""
            val course = cursor.getString(3) ?: ""
            val pos = cursor.getString(4) ?: ""

            val finalDepartment = if (dept.isNotBlank()) dept else course.ifBlank { "Information Technology" }
            val finalPosition = if (pos.isNotBlank()) pos else "Instructor"

            if (name.isNotBlank()) {
                findViewById<TextView>(R.id.tvFullName).text = name
                val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(2)
                findViewById<TextView>(R.id.tvAvatar).text = initials.ifBlank { name.take(2).uppercase() }
            }
            findViewById<TextView>(R.id.tvEmail).text = email.ifBlank { "N/A" }
            findViewById<TextView>(R.id.tvDepartment).text = finalDepartment
            findViewById<TextView>(R.id.tvPosition).text = finalPosition

            val roleSubtitle = "$finalPosition • $finalDepartment"
            findViewById<TextView>(R.id.tvFacultyRole).text = roleSubtitle
        }
        cursor.close()
    }

    private fun logout() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}