package com.example.gradetrack

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        dbHelper = DatabaseHelper(this)
        val studentId = intent.getStringExtra("STUDENT_ID") ?: ""

        // Use MaterialButton cast since it's defined as a MaterialButton in XML
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE user_id = ?",
            arrayOf(studentId)
        )

        if (cursor.moveToFirst()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""
            val userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")) ?: ""
            val course = cursor.getString(cursor.getColumnIndexOrThrow("course")) ?: "N/A"
            val birthdate = cursor.getString(cursor.getColumnIndexOrThrow("birthdate")) ?: "N/A"
            val address = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "N/A"
            val email = cursor.getString(cursor.getColumnIndexOrThrow("email")) ?: "N/A"
            val campus = cursor.getString(cursor.getColumnIndexOrThrow("campus")) ?: "N/A"

            // 1. Populate Header Overview Card
            findViewById<TextView>(R.id.tvHeaderName).text = name
            findViewById<TextView>(R.id.tvHeaderCourse).text = course

            // 2. Populate Detailed Profile Information Card (relying on XML labels)
            findViewById<TextView>(R.id.tvName).text = name
            findViewById<TextView>(R.id.tvStudentId).text = userId
            findViewById<TextView>(R.id.tvCourse).text = course
            findViewById<TextView>(R.id.tvBirthdate).text = birthdate
            findViewById<TextView>(R.id.tvAddress).text = address
            findViewById<TextView>(R.id.tvEmail).text = email
            findViewById<TextView>(R.id.tvCampus).text = campus
        }
        cursor.close()
    }
}