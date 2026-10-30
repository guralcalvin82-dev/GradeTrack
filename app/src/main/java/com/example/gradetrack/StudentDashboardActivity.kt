package com.example.gradetrack

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    // Views for Academic Summary
    private lateinit var tvFirstSem: TextView
    private lateinit var tvSecondSem: TextView
    private lateinit var tvOverallAverage: TextView
    private lateinit var tvStatus: TextView

    // Action Buttons
    private lateinit var btnPrintGrades: MaterialButton

    private var studentId: String = ""

    // Reference para hindi ma-garbage collect ang WebView habang nagpiprint
    private var printWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        dbHelper = DatabaseHelper(this)
        studentId = intent.getStringExtra("STUDENT_ID") ?: ""

        initViews()
        setupNavigation()

        // Bottom navigation bar (Home / Grades / Schedule / Profile)
        BottomNavHelper.setup(this, studentId, BottomNavHelper.Tab.HOME)
    }

    override fun onResume() {
        super.onResume()
        showAcademicSummary(studentId)
    }

    private fun initViews() {
        tvFirstSem = findViewById(R.id.tvFirstSem)
        tvSecondSem = findViewById(R.id.tvSecondSem)
        tvOverallAverage = findViewById(R.id.tvOverallAverage)
        tvStatus = findViewById(R.id.tvStatus)

        btnPrintGrades = findViewById(R.id.btnPrintGrades)
    }

    private fun setupNavigation() {
        // View / Print Grades
        btnPrintGrades.setOnClickListener { showGradesOptionsDialog() }
    }

    // ---------------------------------------------------------------------
    // ACADEMIC SUMMARY
    // ---------------------------------------------------------------------

    private fun showAcademicSummary(studentId: String) {
        val sem1 = dbHelper.getSemesterAverage(studentId, 1)
        val sem2 = dbHelper.getSemesterAverage(studentId, 2)
        val overall = dbHelper.getOverallAverage(studentId)

        fun fmt(v: Double?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

        tvFirstSem.text = fmt(sem1)
        tvSecondSem.text = fmt(sem2)
        tvOverallAverage.text = "Overall Average: ${fmt(overall)}"

        when (dbHelper.getAcademicStatus(overall)) {
            "Passed" -> {
                tvStatus.text = "PASSED"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            }
            "Failed" -> {
                tvStatus.text = "FAILED"
                tvStatus.setTextColor(android.graphics.Color.RED)
            }
            else -> {
                tvStatus.text = "NO GRADES"
                tvStatus.setTextColor(getColor(R.color.primary_dark))
            }
        }
    }

    // ---------------------------------------------------------------------
    // VIEW / PRINT GRADES
    // ---------------------------------------------------------------------

    private fun showGradesOptionsDialog() {
        if (studentId.isEmpty()) {
            Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("1st Semester", "2nd Semester", "Overall (1st + 2nd Semester)")
        var selected = 2

        AlertDialog.Builder(this)
            .setTitle("Select Grades")
            .setSingleChoiceItems(options, selected) { _, which -> selected = which }
            .setPositiveButton("View") { _, _ -> previewGrades(semestersFor(selected)) }
            .setNeutralButton("Print") { _, _ -> printGrades(semestersFor(selected)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun semestersFor(choice: Int): List<Int> = when (choice) {
        0 -> listOf(1)
        1 -> listOf(2)
        else -> listOf(1, 2)
    }

    // Preview sa loob ng dialog, may Print button din
    private fun previewGrades(semesters: List<Int>) {
        val webView = WebView(this)
        webView.loadDataWithBaseURL(null, buildGradesHtml(semesters), "text/HTML", "UTF-8", null)

        AlertDialog.Builder(this)
            .setTitle("Grade Report")
            .setView(webView)
            .setPositiveButton("Print") { _, _ -> printGrades(semesters) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun printGrades(semesters: List<Int>) {
        if (studentId.isEmpty()) {
            Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show()
            return
        }

        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Grades_$studentId"
                printManager.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().build()
                )
                printWebView = null
            }
        }
        webView.loadDataWithBaseURL(null, buildGradesHtml(semesters), "text/HTML", "UTF-8", null)
        printWebView = webView
    }

    private fun buildGradesHtml(semesters: List<Int>): String {
        // Student info from DB (matches Admin info)
        var name = studentId
        var course = ""
        var section = ""
        var department = ""
        var contactNum = ""
        var address = ""
        var email = ""

        dbHelper.readableDatabase.rawQuery(
            "SELECT name, COALESCE(course,''), COALESCE(section,''), COALESCE(department,''), COALESCE(contact_num,''), COALESCE(address,''), COALESCE(email,'') " +
                    "FROM ${DatabaseHelper.TABLE_USERS} WHERE user_id = ?",
            arrayOf(studentId)
        ).use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                course = c.getString(1)
                section = c.getString(2)
                department = c.getString(3)
                contactNum = c.getString(4)
                address = c.getString(5)
                email = c.getString(6)
            }
        }

        val sb = StringBuilder()
        sb.append(
            """
            <html><head><meta charset="utf-8"/>
            <style>
              body { font-family: sans-serif; padding: 24px; color: #111; }
              h1 { font-size: 22px; margin: 0 0 8px 0; color: #202734; }
              .info { font-size: 13px; margin-bottom: 20px; padding: 14px; border: 1px solid #D8E5CE; border-radius: 8px; background: #F3F7F0; color: #202734; line-height: 1.7; }
              h2 { font-size: 16px; margin: 20px 0 8px 0; color: #202734; border-bottom: 2px solid #36415A; padding-bottom: 4px; }
              table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 8px; }
              th, td { border: 1px solid #ccc; padding: 8px; text-align: center; }
              th { background: #36415A; color: #fff; }
              td.left { text-align: left; }
              .summary { margin-top: 20px; font-size: 14px; font-weight: bold; padding: 10px; background: #E0F8C7; border-radius: 6px; color: #202734; }
            </style></head><body>
            <h1>Student Grade Report</h1>
            <div class="info">
              <b>Full Name:</b> ${TextUtils.htmlEncode(name)}<br/>
              <b>Student ID:</b> ${TextUtils.htmlEncode(studentId)}<br/>
              <b>Course &amp; Section:</b> ${TextUtils.htmlEncode("$course $section".trim())}<br/>
              <b>Department:</b> ${TextUtils.htmlEncode(department.ifBlank { "N/A" })}<br/>
              <b>Contact Number:</b> ${TextUtils.htmlEncode(contactNum.ifBlank { "N/A" })}<br/>
              <b>Home Address:</b> ${TextUtils.htmlEncode(address.ifBlank { "N/A" })}<br/>
              <b>Email Address:</b> ${TextUtils.htmlEncode(email.ifBlank { "N/A" })}
            </div>
            """.trimIndent()
        )

        val semAverages = mutableListOf<Double>()

        for (sem in semesters) {
            val label = if (sem == 1) "1st Semester" else "2nd Semester"
            sb.append("<h2>$label</h2>")

            val grades = dbHelper.getStudentGrades(studentId, sem)
            if (grades.isEmpty()) {
                sb.append("<div>No grades recorded.</div>")
                continue
            }

            sb.append("<table><tr><th>Subject</th><th>Units</th><th>Midterm</th><th>Final</th><th>Average</th></tr>")

            val subjectAverages = mutableListOf<Double>()
            for (g in grades) {
                val valid = listOfNotNull(
                    g.midterm.takeIf { it > 0.0 },
                    g.final.takeIf { it > 0.0 }
                )
                val avg = if (valid.isNotEmpty()) valid.average() else null
                if (avg != null) subjectAverages.add(avg)

                val units = dbHelper.getSubjectUnits(g.subjectCode)
                val subject = "${g.subjectCode} - ${g.subjectName}"

                sb.append("<tr>")
                sb.append("<td class=\"left\">${TextUtils.htmlEncode(subject)}</td>")
                sb.append("<td>$units</td>")
                sb.append("<td>${if (g.midterm > 0.0) fmtGrade(g.midterm) else "-"}</td>")
                sb.append("<td>${if (g.final > 0.0) fmtGrade(g.final) else "-"}</td>")
                sb.append("<td>${avg?.let { String.format(Locale.US, "%.2f", it) } ?: "-"}</td>")
                sb.append("</tr>")
            }
            sb.append("</table>")

            if (subjectAverages.isNotEmpty()) {
                val semAvg = subjectAverages.average()
                semAverages.add(semAvg)
                sb.append(
                    "<div style=\"margin-top:6px;font-size:13px;\">" +
                            "Semester Average: ${String.format(Locale.US, "%.2f", semAvg)}</div>"
                )
            }
        }

        // Overall lang ang ipapakita kung dalawang semester ang napili
        if (semesters.size == 2) {
            if (semAverages.isNotEmpty()) {
                val overall = semAverages.average()
                val status = if (overall >= 75.0) "PASSED" else "FAILED"
                sb.append(
                    "<div class=\"summary\">Overall Average: " +
                            "${String.format(Locale.US, "%.2f", overall)} &nbsp;|&nbsp; Status: $status</div>"
                )
            } else {
                sb.append("<div class=\"summary\">Overall Average: -- &nbsp;|&nbsp; Status: NO GRADES</div>")
            }
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun fmtGrade(v: Double): String = String.format(Locale.US, "%.2f", v)
}