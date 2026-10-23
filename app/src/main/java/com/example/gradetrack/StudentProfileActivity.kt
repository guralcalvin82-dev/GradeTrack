package com.example.gradetrack

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        dbHelper = DatabaseHelper(this)
        val studentId = intent.getStringExtra("STUDENT_ID") ?: ""

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE user_id = ?", arrayOf(studentId))

        if (cursor.moveToFirst()) {
            findViewById<TextView>(R.id.tvName).text = "Name: " + cursor.getString(cursor.getColumnIndexOrThrow("name"))

            findViewById<TextView>(R.id.tvStudentId).text = "Student ID: " + cursor.getString(cursor.getColumnIndexOrThrow("user_id"))

            findViewById<TextView>(R.id.tvCourse).text = "Course: " + (cursor.getString(cursor.getColumnIndexOrThrow("course")) ?: "N/A")

            findViewById<TextView>(R.id.tvBirthdate).text = "Birthdate: " + (cursor.getString(cursor.getColumnIndexOrThrow("birthdate")) ?: "N/A")

            findViewById<TextView>(R.id.tvAddress).text = "Address: " + (cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "N/A")

            findViewById<TextView>(R.id.tvEmail).text = "Email: " + (cursor.getString(cursor.getColumnIndexOrThrow("email")) ?: "N/A")

            findViewById<TextView>(R.id.tvCampus).text = "Campus: " + (cursor.getString(cursor.getColumnIndexOrThrow("campus")) ?: "N/A")
        }
        cursor.close()
    }
}