package com.example.gradetrack

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        dbHelper = DatabaseHelper(this)

        val btnBack = findViewById<MaterialButton>(R.id.btnBack)
        val tvAlreadyHaveAccount = findViewById<TextView>(R.id.tvAlreadyHaveAccount)

        val etId = findViewById<TextInputEditText>(R.id.etId)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etDetail = findViewById<TextInputEditText>(R.id.etDetail)
        val etBirthdate = findViewById<TextInputEditText>(R.id.etBirthdate)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val etCampus = findViewById<TextInputEditText>(R.id.etCampus)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn) // Register button

        // DatePicker for Birthdate Field
        etBirthdate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                    etBirthdate.setText(formattedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnBack.setOnClickListener { finish() }
        tvAlreadyHaveAccount.setOnClickListener { finish() }

        // Register Student Account Action
        btnSignIn.setOnClickListener {
            val id = etId.text.toString().trim()
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val detail = etDetail.text.toString().trim()
            val birthdate = etBirthdate.text.toString().trim()
            val address = etAddress.text.toString().trim()
            val campus = etCampus.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (id.isEmpty() || name.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in ID, Name, and Password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Safely parse Course & Section from 'etDetail'
            val parts = detail.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val course = if (parts.isNotEmpty()) parts[0] else "BSIS"
            val section = if (parts.size >= 2) parts.subList(1, parts.size).joinToString(" ") else "2C"

            // Explicitly register as a Student account only
            val result = dbHelper.registerStudent(
                userId = id,
                name = name,
                email = email,
                course = course,
                section = section,
                birthdate = birthdate,
                address = address,
                campus = campus,
                password = password
            )

            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.success) finish() // back to log in
        }
    }
}