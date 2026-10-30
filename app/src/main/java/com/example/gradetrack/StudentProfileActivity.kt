package com.example.gradetrack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var studentId: String = ""

    private lateinit var ivStudentProfilePic: ImageView
    private lateinit var tvAvatarInitials: TextView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // fallback
            }
            val uriString = it.toString()
            val success = dbHelper.updateUserProfileImage(studentId, uriString)
            if (success) {
                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                displayImage(uriString)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        dbHelper = DatabaseHelper(this)
        studentId = intent.getStringExtra("STUDENT_ID") ?: ""

        ivStudentProfilePic = findViewById(R.id.ivStudentProfilePic)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)

        // Bottom navigation bar (Home / Grades / Schedule / Profile)
        BottomNavHelper.setup(this, studentId, BottomNavHelper.Tab.PROFILE)

        // Photo Picker Listeners
        findViewById<View>(R.id.cardProfilePic).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.tvChangePhoto).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Handle Logout
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        loadStudentProfile()
    }

    private fun loadStudentProfile() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT name, user_id, COALESCE(course,''), COALESCE(section,''), COALESCE(birthdate,''), COALESCE(address,''), COALESCE(email,''), COALESCE(contact_num,''), COALESCE(department,''), COALESCE(image_uri,'') FROM ${DatabaseHelper.TABLE_USERS} WHERE user_id = ?",
            arrayOf(studentId)
        )

        if (cursor.moveToFirst()) {
            val name = cursor.getString(0) ?: ""
            val userId = cursor.getString(1) ?: ""
            val course = cursor.getString(2) ?: ""
            val section = cursor.getString(3) ?: ""
            val birthdate = cursor.getString(4) ?: ""
            val address = cursor.getString(5) ?: ""
            val email = cursor.getString(6) ?: ""
            val contactNum = cursor.getString(7) ?: ""
            val department = cursor.getString(8) ?: ""
            val imageUri = cursor.getString(9) ?: ""

            // 1. Overview Header
            findViewById<TextView>(R.id.tvHeaderName).text = name
            val courseSectionText = listOf(course, section).filter { it.isNotBlank() }.joinToString(" • ")
            findViewById<TextView>(R.id.tvHeaderCourse).text = courseSectionText

            // 2. Profile Details
            findViewById<TextView>(R.id.tvName).text = name
            findViewById<TextView>(R.id.tvStudentId).text = userId

            val courseDeptText = if (department.isNotBlank()) "$course ($department)" else course
            findViewById<TextView>(R.id.tvCourse).text = courseDeptText.ifBlank { "N/A" }

            findViewById<TextView>(R.id.tvContactNum).text = contactNum.ifBlank { "N/A" }
            findViewById<TextView>(R.id.tvAddress).text = address.ifBlank { "N/A" }
            findViewById<TextView>(R.id.tvEmail).text = email.ifBlank { "N/A" }

            displayImage(imageUri)
        }
        cursor.close()
    }

    private fun displayImage(imageUri: String) {
        if (imageUri.isNotBlank()) {
            try {
                ivStudentProfilePic.setImageURI(Uri.parse(imageUri))
                ivStudentProfilePic.visibility = View.VISIBLE
                tvAvatarInitials.visibility = View.GONE
            } catch (e: Exception) {
                ivStudentProfilePic.visibility = View.GONE
                tvAvatarInitials.visibility = View.VISIBLE
            }
        } else {
            ivStudentProfilePic.visibility = View.GONE
            tvAvatarInitials.visibility = View.VISIBLE
        }
    }
}