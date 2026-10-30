package com.example.gradetrack

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var adminId: String = "ADMIN"

    // Layout Containers for Tabs
    private lateinit var layoutAdminOverview: View
    private lateinit var layoutAdminStudents: View
    private lateinit var layoutAdminFaculty: View
    private lateinit var layoutAdminAccounts: View

    // Bottom Navigation Bar Views
    private lateinit var btnTabOverview: View
    private lateinit var btnTabStudents: View
    private lateinit var btnTabFaculty: View
    private lateinit var btnTabAccounts: View

    private lateinit var ivTabOverview: ImageView
    private lateinit var ivTabStudents: ImageView
    private lateinit var ivTabFaculty: ImageView
    private lateinit var ivTabAccounts: ImageView

    private lateinit var tvTabOverview: TextView
    private lateinit var tvTabStudents: TextView
    private lateinit var tvTabFaculty: TextView
    private lateinit var tvTabAccounts: TextView

    // Overview Tab Components
    private lateinit var tvCountStudents: TextView
    private lateinit var tvCountFaculty: TextView
    private lateinit var tvCountTotalAccounts: TextView
    private lateinit var layoutDeptStatsContainer: LinearLayout
    private lateinit var btnQuickAddFaculty: MaterialButton
    private lateinit var btnQuickAddStudent: MaterialButton

    // Students Tab Components
    private lateinit var etSearchStudentAdmin: TextInputEditText
    private lateinit var layoutAdminStudentListContainer: LinearLayout
    private lateinit var btnAddStudentTab: MaterialButton

    // Faculty Tab Components
    private lateinit var layoutAdminFacultyListContainer: LinearLayout
    private lateinit var btnAddFacultyTab: MaterialButton

    // All Accounts Tab Components
    private lateinit var etSearchAccountAdmin: TextInputEditText
    private lateinit var layoutAdminAccountsContainer: LinearLayout
    private lateinit var btnAddAccountTab: MaterialButton
    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterStudents: MaterialButton
    private lateinit var btnFilterFaculty: MaterialButton
    private lateinit var btnFilterAdmins: MaterialButton

    private var currentAccountFilter: String = "ALL"

    // Photo Picker State
    private var currentDialogImageUri: String? = null
    private var currentPreviewImageView: ImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // fallback
            }
            val uriString = it.toString()
            currentDialogImageUri = uriString
            currentPreviewImageView?.setImageURI(it)
            currentPreviewImageView?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        dbHelper = DatabaseHelper(this)
        dbHelper.ensureAdminExists()

        adminId = intent.getStringExtra("ADMIN_ID") ?: "ADMIN"

        initViews()
        setupListeners()

        switchTab(0) // Default to Overview tab
    }

    private fun initViews() {
        // Tab Layouts
        layoutAdminOverview = findViewById(R.id.layoutAdminOverview)
        layoutAdminStudents = findViewById(R.id.layoutAdminStudents)
        layoutAdminFaculty = findViewById(R.id.layoutAdminFaculty)
        layoutAdminAccounts = findViewById(R.id.layoutAdminAccounts)

        // Navigation Views
        btnTabOverview = findViewById(R.id.btnTabOverview)
        btnTabStudents = findViewById(R.id.btnTabStudents)
        btnTabFaculty = findViewById(R.id.btnTabFaculty)
        btnTabAccounts = findViewById(R.id.btnTabAccounts)

        ivTabOverview = findViewById(R.id.ivTabOverview)
        ivTabStudents = findViewById(R.id.ivTabStudents)
        ivTabFaculty = findViewById(R.id.ivTabFaculty)
        ivTabAccounts = findViewById(R.id.ivTabAccounts)

        tvTabOverview = findViewById(R.id.tvTabOverview)
        tvTabStudents = findViewById(R.id.tvTabStudents)
        tvTabFaculty = findViewById(R.id.tvTabFaculty)
        tvTabAccounts = findViewById(R.id.tvTabAccounts)

        // Overview Tab Views
        tvCountStudents = findViewById(R.id.tvCountStudents)
        tvCountFaculty = findViewById(R.id.tvCountFaculty)
        tvCountTotalAccounts = findViewById(R.id.tvCountTotalAccounts)
        layoutDeptStatsContainer = findViewById(R.id.layoutDeptStatsContainer)
        btnQuickAddFaculty = findViewById(R.id.btnQuickAddFaculty)
        btnQuickAddStudent = findViewById(R.id.btnQuickAddStudent)

        // Students Tab Views
        etSearchStudentAdmin = findViewById(R.id.etSearchStudentAdmin)
        layoutAdminStudentListContainer = findViewById(R.id.layoutAdminStudentListContainer)
        btnAddStudentTab = findViewById(R.id.btnAddStudentTab)

        // Faculty Tab Views
        layoutAdminFacultyListContainer = findViewById(R.id.layoutAdminFacultyListContainer)
        btnAddFacultyTab = findViewById(R.id.btnAddFacultyTab)

        // Accounts Tab Views
        etSearchAccountAdmin = findViewById(R.id.etSearchAccountAdmin)
        layoutAdminAccountsContainer = findViewById(R.id.layoutAdminAccountsContainer)
        btnAddAccountTab = findViewById(R.id.btnAddAccountTab)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterStudents = findViewById(R.id.btnFilterStudents)
        btnFilterFaculty = findViewById(R.id.btnFilterFaculty)
        btnFilterAdmins = findViewById(R.id.btnFilterAdmins)

        findViewById<View>(R.id.btnLogout).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupListeners() {
        // Tab Switches
        btnTabOverview.setOnClickListener { switchTab(0) }
        btnTabStudents.setOnClickListener { switchTab(1) }
        btnTabFaculty.setOnClickListener { switchTab(2) }
        btnTabAccounts.setOnClickListener { switchTab(3) }

        // Quick Actions
        btnQuickAddFaculty.setOnClickListener { showAddEditUserDialog(defaultRole = "FACULTY") }
        btnQuickAddStudent.setOnClickListener { selectSectionThenAddStudent() }
        btnAddStudentTab.setOnClickListener { selectSectionThenAddStudent() }
        btnAddFacultyTab.setOnClickListener { showAddEditUserDialog(defaultRole = "FACULTY") }
        btnAddAccountTab.setOnClickListener { showAddEditUserDialog(defaultRole = "STUDENT") }

        // Student Search
        etSearchStudentAdmin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadStudentList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Account Search
        etSearchAccountAdmin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadAccountList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Role Filter Buttons
        btnFilterAll.setOnClickListener { setAccountFilter("ALL") }
        btnFilterStudents.setOnClickListener { setAccountFilter("STUDENT") }
        btnFilterFaculty.setOnClickListener { setAccountFilter("FACULTY") }
        btnFilterAdmins.setOnClickListener { setAccountFilter("ADMIN") }
    }

    /**
     * Requirement: Once clicking Add Student, prompt section selection first (e.g. 2C, 2B),
     * then open the Add Account form for that section.
     */
    private fun selectSectionThenAddStudent() {
        val sections = arrayOf("BSIS 2C", "BSIS 2B", "BSIS 2A", "BSIS 2D")
        AlertDialog.Builder(this)
            .setTitle("Select Section for New Student")
            .setItems(sections) { _, which ->
                val selectedSection = sections[which]
                showAddEditUserDialog(defaultRole = "STUDENT", prefilledSection = selectedSection)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setAccountFilter(role: String) {
        currentAccountFilter = role
        val primaryDark = getColor(R.color.primary_dark)

        fun style(btn: MaterialButton, active: Boolean) {
            btn.backgroundTintList = ColorStateList.valueOf(
                if (active) primaryDark else Color.TRANSPARENT
            )
            btn.setTextColor(if (active) Color.WHITE else primaryDark)
            btn.strokeColor = ColorStateList.valueOf(primaryDark)
            btn.strokeWidth = if (active) 0 else 2
        }

        style(btnFilterAll, role == "ALL")
        style(btnFilterStudents, role == "STUDENT")
        style(btnFilterFaculty, role == "FACULTY")
        style(btnFilterAdmins, role == "ADMIN")

        loadAccountList(etSearchAccountAdmin.text.toString())
    }

    private fun switchTab(tabIndex: Int) {
        layoutAdminOverview.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        layoutAdminStudents.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        layoutAdminFaculty.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        layoutAdminAccounts.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE

        fun styleTab(iv: ImageView, tv: TextView, active: Boolean) {
            iv.alpha = if (active) 1.0f else 0.4f
            tv.alpha = if (active) 1.0f else 0.4f
        }

        styleTab(ivTabOverview, tvTabOverview, tabIndex == 0)
        styleTab(ivTabStudents, tvTabStudents, tabIndex == 1)
        styleTab(ivTabFaculty, tvTabFaculty, tabIndex == 2)
        styleTab(ivTabAccounts, tvTabAccounts, tabIndex == 3)

        when (tabIndex) {
            0 -> loadOverviewData()
            1 -> loadStudentList(etSearchStudentAdmin.text.toString())
            2 -> loadFacultyList()
            3 -> loadAccountList(etSearchAccountAdmin.text.toString())
        }
    }

    // ---------------------------------------------------------------------
    // 1. OVERVIEW DATA & DEPARTMENT STUDENT COUNTS
    // ---------------------------------------------------------------------

    private fun loadOverviewData() {
        val summary = dbHelper.getAccountSummaryCounts()
        tvCountStudents.text = summary.totalStudents.toString()
        tvCountFaculty.text = summary.totalFaculty.toString()
        tvCountTotalAccounts.text = summary.totalUsers.toString()

        layoutDeptStatsContainer.removeAllViews()

        val deptCounts = dbHelper.getDepartmentStudentCounts()

        if (deptCounts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No students enrolled in any department."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, 16, 0, 16)
            }
            layoutDeptStatsContainer.addView(emptyView)
            return
        }

        for (dept in deptCounts) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
            }

            val tvDeptName = TextView(this).apply {
                text = dept.department
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvCount = TextView(this).apply {
                text = "${dept.count} Students"
                textSize = 13f
                setTextColor(getColor(R.color.primary_dark))
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_spinner_container)
                setPadding(16, 8, 16, 8)
            }

            row.addView(tvDeptName)
            row.addView(tvCount)
            layoutDeptStatsContainer.addView(row)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(getColor(R.color.border_color))
            }
            layoutDeptStatsContainer.addView(divider)
        }
    }

    // ---------------------------------------------------------------------
    // 2. STUDENTS TAB & STUDENT DETAILS/GRADES VIEW
    // ---------------------------------------------------------------------

    private fun loadStudentList(query: String = "") {
        layoutAdminStudentListContainer.removeAllViews()

        val students = dbHelper.getAllUserAccounts("STUDENT", query)

        if (students.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = if (query.isEmpty()) "No students registered." else "No student matching '$query'"
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            layoutAdminStudentListContainer.addView(emptyText)
            return
        }

        for (item in students) {
            val card = CardView(this).apply {
                radius = 12f
                cardElevation = 2f
                setContentPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                setOnClickListener { showStudentDetailsAndGradesDialog(item) }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = item.name
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            val tvDetails = TextView(this).apply {
                text = "ID: ${item.userId} • ${item.course.ifBlank { "N/A" }} ${item.section.ifBlank { "" }}".trim()
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
            }

            infoLayout.addView(tvName)
            infoLayout.addView(tvDetails)

            val tvAction = TextView(this).apply {
                text = "View Info & Grades ›"
                textSize = 12f
                setTextColor(getColor(R.color.primary_dark))
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(infoLayout)
            row.addView(tvAction)
            card.addView(row)

            layoutAdminStudentListContainer.addView(card)
        }
    }

    private fun showStudentDetailsAndGradesDialog(student: DatabaseHelper.UserAccountItem) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        // Student Profile Header with Photo
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val ivPhoto = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(72, 72).apply { setMargins(0, 0, 16, 0) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_avatar_circle)
            if (student.imageUri.isNotBlank()) {
                try {
                    setImageURI(Uri.parse(student.imageUri))
                } catch (e: Exception) {
                    // fallback
                }
            }
        }

        val tvHeaderInfo = TextView(this).apply {
            text = "${student.name}\nID: ${student.userId}\nCourse: ${student.course}\nDept: ${student.department.ifBlank { "N/A" }} • Sec: ${student.section.ifBlank { "N/A" }}"
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerRow.addView(ivPhoto)
        headerRow.addView(tvHeaderInfo)
        layout.addView(headerRow)

        // Contact & Address Details
        val tvDetails = TextView(this).apply {
            text = "Contact Num: ${student.contactNum.ifBlank { "N/A" }}\nAddress: ${student.address.ifBlank { "N/A" }}\nEmail: ${student.email.ifBlank { "N/A" }}"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvDetails)

        // Academic Averages
        val sem1Avg = dbHelper.getSemesterAverage(student.userId, 1)
        val sem2Avg = dbHelper.getSemesterAverage(student.userId, 2)
        val overallAvg = dbHelper.getOverallAverage(student.userId)

        fun fmt(v: Double?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

        val tvAverages = TextView(this).apply {
            text = "1st Sem Average: ${fmt(sem1Avg)}\n2nd Sem Average: ${fmt(sem2Avg)}\nOverall Average: ${fmt(overallAvg)} (${dbHelper.getAcademicStatus(overallAvg)})"
            textSize = 13f
            setTextColor(getColor(R.color.primary_dark))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvAverages)

        val btnEdit = MaterialButton(this).apply {
            text = "Edit Student Info"
            setOnClickListener {
                showAddEditUserDialog(student)
            }
        }

        val btnDelete = MaterialButton(this).apply {
            text = "Delete Student Account"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setOnClickListener {
                showDeleteUserConfirmDialog(student)
            }
        }

        layout.addView(btnEdit)
        layout.addView(btnDelete)

        AlertDialog.Builder(this)
            .setTitle("Student Profile & Academic Summary")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // 3. FACULTY MANAGEMENT & POSITIONS
    // ---------------------------------------------------------------------

    private fun loadFacultyList() {
        layoutAdminFacultyListContainer.removeAllViews()

        val facultyMembers = dbHelper.getAllUserAccounts("FACULTY")

        if (facultyMembers.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No faculty members registered."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            layoutAdminFacultyListContainer.addView(emptyText)
            return
        }

        for (item in facultyMembers) {
            val card = CardView(this).apply {
                radius = 12f
                cardElevation = 2f
                setContentPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                setOnClickListener { showAddEditUserDialog(item) }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = item.name
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            val tvDetails = TextView(this).apply {
                val pos = item.position.ifBlank { "Instructor" }
                val dept = item.department.ifBlank { item.course.ifBlank { "Faculty" } }
                text = "ID: ${item.userId} • $pos • $dept"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
            }

            infoLayout.addView(tvName)
            infoLayout.addView(tvDetails)

            val tvEdit = TextView(this).apply {
                text = "Edit Position ›"
                textSize = 12f
                setTextColor(getColor(R.color.primary_dark))
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(infoLayout)
            row.addView(tvEdit)
            card.addView(row)

            layoutAdminFacultyListContainer.addView(card)
        }
    }

    // ---------------------------------------------------------------------
    // 4. ALL ACCOUNTS MANAGEMENT
    // ---------------------------------------------------------------------

    private fun loadAccountList(query: String = "") {
        layoutAdminAccountsContainer.removeAllViews()

        val accounts = dbHelper.getAllUserAccounts(currentAccountFilter, query)

        if (accounts.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No accounts found."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            layoutAdminAccountsContainer.addView(emptyText)
            return
        }

        for (item in accounts) {
            val card = CardView(this).apply {
                radius = 12f
                cardElevation = 2f
                setContentPadding(16, 14, 16, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
                setOnClickListener { showAddEditUserDialog(item) }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = "${item.name} (${item.role})"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            val tvDetails = TextView(this).apply {
                val detailStr = when (item.role) {
                    "STUDENT" -> "Course: ${item.course} ${item.section}"
                    "FACULTY" -> "Pos: ${item.position.ifBlank { "Instructor" }} • Dept: ${item.department.ifBlank { item.course }}"
                    else -> "Admin Account"
                }
                text = "ID: ${item.userId} • $detailStr"
                textSize = 11f
                setTextColor(getColor(R.color.text_secondary))
            }

            infoLayout.addView(tvName)
            infoLayout.addView(tvDetails)

            val tvEdit = TextView(this).apply {
                text = "Manage ›"
                textSize = 12f
                setTextColor(getColor(R.color.primary_dark))
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(infoLayout)
            row.addView(tvEdit)
            card.addView(row)

            layoutAdminAccountsContainer.addView(card)
        }
    }

    // ---------------------------------------------------------------------
    // ADD & EDIT USER DIALOG (INCLUDES PICTURE PICKER, ALL FIELDS & POSITION)
    // ---------------------------------------------------------------------

    private fun showAddEditUserDialog(
        existingUser: DatabaseHelper.UserAccountItem? = null,
        defaultRole: String = "STUDENT",
        prefilledSection: String = ""
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }

        val isEdit = existingUser != null
        currentDialogImageUri = existingUser?.imageUri

        // Profile Picture Section
        val picLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val ivPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 16, 0) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_avatar_circle)
            if (!currentDialogImageUri.isNullOrBlank()) {
                try {
                    setImageURI(Uri.parse(currentDialogImageUri))
                } catch (e: Exception) {
                    // fallback
                }
            }
        }

        val btnPickPic = MaterialButton(this).apply {
            text = "📷 Select Profile Photo"
            setOnClickListener {
                currentPreviewImageView = ivPreview
                pickImageLauncher.launch("image/*")
            }
        }

        picLayout.addView(ivPreview)
        picLayout.addView(btnPickPic)
        layout.addView(picLayout)

        // Role Dropdown (ONLY STUDENT & FACULTY available; ADMIN is pre-created and unique)
        val tvRoleLabel = TextView(this).apply { text = "Account Role:" }
        val roles = if (existingUser?.role?.equals("ADMIN", ignoreCase = true) == true) {
            arrayOf("ADMIN")
        } else {
            arrayOf("STUDENT", "FACULTY")
        }

        val spRole = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminDashboardActivity, android.R.layout.simple_spinner_dropdown_item, roles)
            val activeRole = existingUser?.role ?: defaultRole
            val idx = roles.indexOfFirst { it.equals(activeRole, ignoreCase = true) }
            if (idx >= 0) setSelection(idx)
            isEnabled = !(isEdit && existingUser?.role?.equals("ADMIN", ignoreCase = true) == true)
        }

        val etUserId = EditText(this).apply {
            hint = "User ID (e.g. MA-202601 or FAC2026-001)"
            setText(existingUser?.userId ?: "")
            isEnabled = !isEdit
        }

        val etName = EditText(this).apply {
            hint = "Full Name"
            setText(existingUser?.name ?: "")
        }

        val etCourse = EditText(this).apply {
            hint = "Course (e.g. BSIS, BSIT)"
            setText(existingUser?.course ?: "BSIS")
        }

        val etDepartment = EditText(this).apply {
            hint = "Department (e.g. Information Technology, Information Systems)"
            setText(if (existingUser?.department.isNullOrBlank()) existingUser?.course ?: "" else existingUser?.department ?: "")
        }

        val etSection = EditText(this).apply {
            hint = "Section (e.g. BSIS 2C, 2B)"
            setText(existingUser?.section ?: prefilledSection)
        }

        // Position field is STRICTLY for Faculty accounts only!
        val initialRole = existingUser?.role ?: defaultRole
        val isFacultyInitially = initialRole.equals("FACULTY", ignoreCase = true)

        val etPosition = EditText(this).apply {
            hint = "Faculty Position (e.g. Instructor, Chair, Dean)"
            setText(existingUser?.position ?: "")
            visibility = if (isFacultyInitially) View.VISIBLE else View.GONE
        }

        spRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedRole = spRole.selectedItem.toString()
                etPosition.visibility = if (selectedRole.equals("FACULTY", ignoreCase = true)) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val etContactNum = EditText(this).apply {
            hint = "Contact Number (e.g. 09123456789)"
            setText(existingUser?.contactNum ?: "")
        }

        val etAddress = EditText(this).apply {
            hint = "Home Address"
            setText(existingUser?.address ?: "")
        }

        val etEmail = EditText(this).apply {
            hint = "Email Address"
            setText(existingUser?.email ?: "")
        }

        val etPassword = EditText(this).apply {
            hint = "Password"
            setText(existingUser?.password ?: "1234")
        }

        layout.addView(tvRoleLabel)
        layout.addView(spRole)
        layout.addView(etUserId)
        layout.addView(etName)
        layout.addView(etCourse)
        layout.addView(etDepartment)
        layout.addView(etSection)
        layout.addView(etPosition) // Position field is ONLY visible when Role is FACULTY
        layout.addView(etContactNum)
        layout.addView(etAddress)
        layout.addView(etEmail)
        layout.addView(etPassword)

        val dialogTitle = if (isEdit) "Edit Account: ${existingUser?.userId}" else "Add New Account ${if (prefilledSection.isNotBlank()) "($prefilledSection)" else ""}"

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(layout)
            .setPositiveButton(if (isEdit) "Update" else "Save", null)
            .setNegativeButton("Cancel", null)

        // Show Delete option only if editing a non-ADMIN account
        if (isEdit && existingUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
            dialog.setNeutralButton("Delete Account") { _, _ ->
                showDeleteUserConfirmDialog(existingUser!!)
            }
        }

        val createdDialog = dialog.create()

        createdDialog.setOnShowListener {
            createdDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val userId = etUserId.text.toString().trim()
                val name = etName.text.toString().trim()
                val role = spRole.selectedItem as String
                val course = etCourse.text.toString().trim()
                val dept = etDepartment.text.toString().trim()
                val section = etSection.text.toString().trim()
                // Position is clear if role is not FACULTY
                val position = if (role.equals("FACULTY", ignoreCase = true)) etPosition.text.toString().trim() else ""
                val contactNum = etContactNum.text.toString().trim()
                val address = etAddress.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (userId.isEmpty() || name.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "User ID, Name, and Password are required.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val success = dbHelper.upsertUserAccount(
                    userId = userId,
                    name = name,
                    role = role,
                    course = course,
                    section = section,
                    position = position,
                    email = email,
                    password = password,
                    contactNum = contactNum,
                    department = dept,
                    address = address,
                    imageUri = currentDialogImageUri ?: ""
                )

                if (success) {
                    Toast.makeText(this, "Account saved successfully!", Toast.LENGTH_SHORT).show()
                    createdDialog.dismiss()

                    loadOverviewData()
                    loadStudentList(etSearchStudentAdmin.text.toString())
                    loadFacultyList()
                    loadAccountList(etSearchAccountAdmin.text.toString())
                } else {
                    Toast.makeText(this, "Failed to save account.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        createdDialog.show()
    }

    private fun showDeleteUserConfirmDialog(user: DatabaseHelper.UserAccountItem) {
        if (user.userId.equals("ADMIN", ignoreCase = true) || user.role.equals("ADMIN", ignoreCase = true)) {
            Toast.makeText(this, "The primary System Admin account cannot be deleted.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete account ${user.userId} (${user.name})?")
            .setPositiveButton("Delete") { _, _ ->
                val success = dbHelper.deleteUserByUserId(user.userId)
                if (success) {
                    Toast.makeText(this, "Account deleted.", Toast.LENGTH_SHORT).show()
                    loadOverviewData()
                    loadStudentList(etSearchStudentAdmin.text.toString())
                    loadFacultyList()
                    loadAccountList(etSearchAccountAdmin.text.toString())
                } else {
                    Toast.makeText(this, "Failed to delete account.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}