package com.example.gradetrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale
import android.database.SQLException
import android.util.Log

/**
 * midtermEntered / finalEntered = true kapag may tunay na grade na naipasok
 * (kasama ang tunay na 0). Para hindi malito ang "0" at "walang grade".
 */
data class StudentGrade(
    val subjectCode: String,
    val subjectName: String,
    val midterm: Double,
    val final: Double,
    val midtermEntered: Boolean = midterm > 0.0,
    val finalEntered: Boolean = final > 0.0
) {
    val hasAnyGrade: Boolean
        get() = midtermEntered || finalEntered

    val average: Double
        get() {
            val entered = mutableListOf<Double>()
            if (midtermEntered) entered.add(midterm)
            if (finalEntered) entered.add(final)
            return if (entered.isEmpty()) 0.0 else entered.sum() / entered.size
        }
}

data class ScheduleItem(
    val id: Long,
    val subjectCode: String,
    val time: String,
    val day: String,
    val room: String,
    val instructor: String,
    val course: String,
    val subjectName: String = "",
    val facultyId: String = "",
    val semester: Int = 1
)


data class SubjectItem(
    val id: Long,
    val code: String,
    val subjectName: String,
    val units: Int
)

data class StudentItem(
    val userId: String,
    val name: String,
    val course: String,
    val section: String,
    val email: String
)

data class GradeEntry(
    val studentId: String,
    val name: String,
    val midterm: Double,
    val final: Double
)

data class OpResult(val success: Boolean, val message: String)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "GradeManagement.db"
        private const val DATABASE_VERSION = 26

        const val TABLE_USERS = "users"
        const val TABLE_GRADES = "grades"
        const val TABLE_SUBJECTS = "subjects"
        const val TABLE_TEACHERS = "teachers"
        const val TABLE_CLASS_SCHEDULE = "faculty_schedule" // schedule na tinuturo ng faculty
        const val STUDENT_SCHEDULE = "student_schedule"     // schedule ng bawat student

        val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        const val SEED_COURSE = "BSIS"
        const val SEED_SECTION = "2C"
        const val PASSING_MAX = 2.75   // 1.00 hanggang 2.75 = Passed, mas mataas = Failed

    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun checkAndUpdateColumns(db: SQLiteDatabase) {
        try {
            val columns = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info($TABLE_USERS)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            if (!columns.contains("contact_num")) {
                db.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN contact_num TEXT")
            }
            if (!columns.contains("department")) {
                db.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN department TEXT")
            }
            if (!columns.contains("image_uri")) {
                db.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN image_uri TEXT")
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking/updating table columns", e)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            checkAndUpdateColumns(db)
            insertSubjects(db)
            ensureAdminExists(db)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'STUDENT',
                course TEXT,
                section TEXT,
                position TEXT,
                birthdate TEXT,
                address TEXT,
                email TEXT,
                campus TEXT,
                contact_num TEXT,
                department TEXT,
                image_uri TEXT,
                password TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TEACHERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                teacher_id TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                email TEXT,
                department TEXT,
                position TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SUBJECTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT UNIQUE NOT NULL,
                subject_name TEXT NOT NULL,
                units INTEGER NOT NULL DEFAULT 3
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CLASS_SCHEDULE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                faculty_id TEXT NOT NULL,
                subject_code TEXT NOT NULL,
                course TEXT NOT NULL,
                day TEXT NOT NULL,
                time TEXT NOT NULL,
                start_min INTEGER NOT NULL,
                end_min INTEGER NOT NULL,
                room TEXT NOT NULL,
                semester INTEGER NOT NULL DEFAULT 1,
                UNIQUE(course, subject_code, semester),
                FOREIGN KEY (faculty_id) REFERENCES $TABLE_USERS(user_id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY (subject_code) REFERENCES $TABLE_SUBJECTS(code)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $STUDENT_SCHEDULE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id TEXT NOT NULL,
                class_id INTEGER NOT NULL,
                UNIQUE(student_id, class_id),
                FOREIGN KEY (student_id) REFERENCES $TABLE_USERS(user_id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY (class_id) REFERENCES $TABLE_CLASS_SCHEDULE(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_GRADES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id TEXT NOT NULL,
                subject_code TEXT NOT NULL,
                prelim REAL DEFAULT 0,
                midterm REAL,
                final REAL,
                semester INTEGER NOT NULL,
                UNIQUE(student_id, subject_code, semester),
                FOREIGN KEY (student_id) REFERENCES $TABLE_USERS(user_id)
                    ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY (subject_code) REFERENCES $TABLE_SUBJECTS(code)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )

        insertFaculty(db)
        insertSubjects(db)
        insertStudents(db)
        insertInitialClasses(db)
        insertSecondSemesterClasses(db)   // must come BEFORE seedStudentSchedules
        seedStudentSchedules(db)
        ensureAdminExists(db)
    }

    fun getFacultyIdByName(name: String): String? {
        val cursor = readableDatabase.rawQuery(
            """
        SELECT user_id
        FROM $TABLE_USERS
        WHERE role = 'FACULTY' AND name = ? COLLATE NOCASE
        LIMIT 1
        """.trimIndent(),
            arrayOf(name.trim())
        )

        val id = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return id
    }


    private fun insertFaculty(db: SQLiteDatabase) {
        val faculty = listOf(
            Triple("FAC2026-001", "Kyle Joseph Fabian", "Information Technology"),
            Triple("FAC2026-002", "Deserie Robles", "Information Systems"),
            Triple("FAC2026-003", "Miguel Don Gatchalian", "Information Technology"),
            Triple("FAC2026-004", "Eroll Reyes", null),
            Triple("FAC2026-005", "Cindy Ostonal", null),
            Triple("FAC2026-006", "Christian Paradero", null),
            Triple("FAC2026-007", "Carl Ashlie Ramos", null)
        )
        for ((id, name, dept) in faculty) {
            insertUser(db, id, name, "FACULTY", "", "", "Instructor", "faculty123")
            val values = ContentValues().apply {
                put("teacher_id", id)
                put("name", name)
                put("department", dept)
                put("position", "Instructor")
            }
            db.insert(TABLE_TEACHERS, null, values)
        }
    }


    private fun insertSubjects(db: SQLiteDatabase) {
        // FIRST SEMESTER
        insertSubject(db, "CC-IM 213", "INFORMATION MANAGEMENT")
        insertSubject(db, "CC-DSA 213", "DATA STRUCTURE AND ALGORITHMS")
        insertSubject(db, "IS-DCN 213", "DATA COMMUNICATION AND NETWORKING")
        insertSubject(db, "CT-MAD 213", "MOBILE APPLICATION")
        insertSubject(db, "CT-WA 213", "WEB APPLICATION")
        insertSubject(db, "GE-RLW 213", "RIZAL'S LIFE AND WORKS")
        insertSubject(db, "GE-STS 213", "SCIENCE, TECHNOLOGY AND SOCIETY")
        insertSubject(db, "PE 212", "PHYSICAL ACTIVITIES TOWARDS HEALTH & FITNESS 3-ARNIS (PATHFit 3)")

        // SECOND SEMESTER
        insertSubject(db, "IS-DMS 223", "DEPLOYMENT MAINTENANCE AND SERVICES")
        insertSubject(db, "DM-HRM 323", "HUMAN RESOURCE MANAGEMENT")
        insertSubject(db, "AD-PT 223", "PRINCIPLES OF TEACHING")
        insertSubject(db, "GE-Hum 223", "ETHICS")
        insertSubject(db, "GE-Soc 223", "GENERAL SOCIOLOGY (WITH GENDER AND DEVELOPMENT)")
        insertSubject(db, "GE-Sci 223", "ENVIRONMENTAL SCIENCE")
        insertSubject(db, "CC-ADET 223", "APPLICATION DEVELOPMENT & EMERGING TECHNOLOGIES")
        insertSubject(db, "PE 222", "PHYSICAL ACTIVITIES TOWARDS HEALTH & FITNESS 4-TEAM SPORTS (PATHFit 4)")
    }

    private fun insertSubject(
        db: SQLiteDatabase,
        code: String,
        name: String
    ) {
        val values = ContentValues().apply {
            put("code", code)
            put("subject_name", name)
            put("units", 3)
        }

        db.insertWithOnConflict(
            TABLE_SUBJECTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun insertUser(
        db: SQLiteDatabase, userId: String, name: String, role: String,
        course: String, section: String, position: String, password: String
    ) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("name", name)
            put("role", role)
            put("course", course)
            put("section", section)
            put("position", position)
            put("password", password)
        }
        db.insert(TABLE_USERS, null, values)
    }

    private fun studentSeedList(): List<Pair<String, String>> = listOf(
        "MA-25010881" to "Alab, Sopia C.",
        "MA-25010908" to "Alfaro, Maxwell James C.",
        "MA-25010932" to "Alisa, Jasmine Margareth M.",
        "MA-25011005" to "Almazar, Juliana Maryse M.",
        "MA-25010945" to "Amor, Ralph Jayrhard G.",
        "MA-25010946" to "Bacaran, Alexander G.",
        "MA-25010947" to "Bacsal, Rochelyn P.",
        "MA-25010948" to "Bautista, Kaye Cel B",
        "MA-25010949" to "Billalba, Lexter John M.",
        "MA-25010905" to "Borja, Kristine Joy M.",
        "MA-25010951" to "Cabrera, Harvey Klaus R.",
        "MA-25010832" to "Cabuz, Jewell Ace Jhay V.",
        "MA-25010952" to "Callanta, Jullian Maria M.",
        "MA-25010953" to "Cayabyab, Kleian B.",
        "MA-25010954" to "Dela Cruz, Marco J.",
        "MA-25010955" to "Delara, Bryan T.",
        "MA-25010660" to "Ebalobo, Jedediah V.",
        "MA-25010663" to "Espejo, Kyle Russell B.",
        "MA-25010956" to "Espino, Jerome M.",
        "MA-25010957" to "Eugenio, John Erick R.",
        "MA-25010958" to "Eva, Angel T.",
        "MA-25010928" to "Ferrer, Jacky Rose D.",
        "MA-25010666" to "Galera, Jhayren",
        "MA-25010959" to "Gamas, Eva Christine C.",
        "MA-25010667" to "Garcia, Gabriel Angelo C",
        "MA-25010695" to "Gumahob, Arnold Yuri O.",
        "MA-25010696" to "Gural, Calvin John H.",
        "MA-25010960" to "Isidro, Kristina Cassandra P.",
        "MA-25010961" to "Lauron, Melissa Faith L.",
        "MA-25011007" to "Losañez, Jarrell Marcus P.",
        "MA-25010962" to "Lumba, Aizen R.",
        "MA-25010963" to "Magluyan",   // PALITAN ng kumpletong pangalan
        "MA-25010964" to "Marcos, Red Lourence R.",
        "MA-25010965" to "Maron, Renz Ryan G.",
        "MA-25010983" to "Navalta, James Laurenz F.",
        "MA-25010987" to "Oclarit, Aljean L.",
        "MA-25010927" to "Pascual, Leira C.",
        "MA-25010989" to "Perez, Raeven M.",
        "MA-25010779" to "Quirona, Reyver Ceyzer DC.",
        "MA-25010990" to "Ramos, Diana",
        "MA-25010991" to "Reyes, Kings Leanne R.",
        "MA-25010992" to "Rodiel, Rainier James P.",
        "MA-25010993" to "Ronquillo, Andrea Clarizze M.",
        "MA-25010995" to "Salvador, Cedrick John",
        "MA-25010996" to "Santos, Antonette DC.",
        "MA-25010997" to "Sapitan, Moises Serg G.",
        "MA-25011009" to "Sauadan, Casie Kate R.",
        "MA-25010999" to "Talucud, Catherine P.",
        "MA-25010785" to "Trinidad, Irene P.",
        "MA-25011000" to "Tumangan, Ericson B.",
        "MA-25011003" to "Zacarias, Daniel"
    )

    private fun insertStudents(db: SQLiteDatabase) {
        for ((id, name) in studentSeedList()) {
            insertUser(db, id, name, "STUDENT", SEED_COURSE, SEED_SECTION, "", "student123")
        }
    }

    private fun migrateStudentIds(db: SQLiteDatabase) {
        for ((newId, name) in studentSeedList()) {
            // I-rename ang lumang STU2026-xxxx ID ayon sa pangalan
            db.execSQL(
                "UPDATE $TABLE_USERS SET user_id = ? " +
                        "WHERE role = 'STUDENT' AND name = ? AND user_id LIKE 'STU2026-%'",
                arrayOf(newId, name)
            )

            // Kung wala pa talaga (hal. si Magluyan), idagdag
            val c = db.rawQuery("SELECT 1 FROM $TABLE_USERS WHERE user_id = ?", arrayOf(newId))
            val exists = c.moveToFirst()
            c.close()
            if (!exists) {
                insertUser(db, newId, name, "STUDENT", SEED_COURSE, SEED_SECTION, "", "student123")
            }
        }
        seedStudentSchedules(db) // i-link ang bagong dagdag sa schedule ng section
    }

    private fun insertClass(
        db: SQLiteDatabase, facultyId: String, subjectCode: String,
        time: String, room: String, day: String, course: String,
        semester: Int = 1
    ) {
        val (start, end) = parseTimeRange(time) ?: Pair(0, 0)
        val values = ContentValues().apply {
            put("faculty_id", facultyId)
            put("subject_code", subjectCode)
            put("course", course)
            put("day", day)
            put("time", "${formatTime(start)} - ${formatTime(end)}")
            put("start_min", start)
            put("end_min", end)
            put("room", room)
            put("semester", semester)
        }
        db.insert(TABLE_CLASS_SCHEDULE, null, values)
    }

    private fun insertInitialClasses(db: SQLiteDatabase) {
        val kyle = "FAC2026-001"
        val deserie = "FAC2026-002"
        val migs = "FAC2026-003"
        val eroll = "FAC2026-004"
        val cindy = "FAC2026-005"
        val christian = "FAC2026-006"
        val carl = "FAC2026-007"

        // --- BSIS 2A ---
        insertClass(db, migs, "CC-DSA 213", "10:00 AM - 12:00 PM", "Comlab 3", "Tuesday", "BSIS 2A")
        insertClass(db, migs, "CC-IM 213", "1:00 PM - 4:00 PM", "Comlab 1", "Tuesday", "BSIS 2A")
        insertClass(db, eroll, "PE 212", "12:00 PM - 2:00 PM", "AC", "Thursday", "BSIS 2A")
        insertClass(db, cindy, "GE-RLW 213", "2:00 PM - 5:00 PM", "Butil 1", "Thursday", "BSIS 2A")
        insertClass(db, deserie, "IS-DCN 213", "10:00 AM - 1:00 PM", "Comlab 3", "Friday", "BSIS 2A")
        insertClass(db, christian, "GE-STS 213", "1:00 PM - 4:00 PM", "Science Lab", "Friday", "BSIS 2A")
        insertClass(db, carl, "CT-WA 213", "1:00 PM - 4:00 PM", "Comlab 3", "Saturday", "BSIS 2A")
        insertClass(db, kyle, "CT-MAD 213", "5:00 PM - 8:00 PM", "Comlab 2", "Saturday", "BSIS 2A")

        // --- BSIS 2B ---
        insertClass(db, migs, "CC-DSA 213", "8:00 AM - 10:00 AM", "Comlab 3", "Tuesday", "BSIS 2B")
        insertClass(db, migs, "CC-IM 213", "4:00 PM - 7:00 PM", "Comlab 1", "Tuesday", "BSIS 2B")
        insertClass(db, eroll, "PE 212", "12:00 PM - 2:00 PM", "AC", "Wednesday", "BSIS 2B")
        insertClass(db, cindy, "GE-RLW 213", "2:00 PM - 5:00 PM", "Butil 1", "Wednesday", "BSIS 2B")
        insertClass(db, deserie, "IS-DCN 213", "7:00 AM - 10:00 AM", "Comlab 3", "Friday", "BSIS 2B")
        insertClass(db, christian, "GE-STS 213", "4:00 PM - 7:00 PM", "Science Lab", "Friday", "BSIS 2B")
        insertClass(db, carl, "CT-WA 213", "8:00 AM - 11:00 AM", "Comlab 3", "Saturday", "BSIS 2B")
        insertClass(db, kyle, "CT-MAD 213", "2:00 PM - 5:00 PM", "Comlab 2", "Saturday", "BSIS 2B")

        // --- BSIS 2C ---
        insertClass(db, migs, "CC-DSA 213", "10:00 AM - 12:00 PM", "Comlab 1", "Tuesday", "BSIS 2C")
        insertClass(db, migs, "CC-IM 213", "8:00 AM - 11:00 AM", "Comlab 1", "Wednesday", "BSIS 2C")
        insertClass(db, eroll, "PE 212", "8:00 AM - 10:00 AM", "AC", "Thursday", "BSIS 2C")
        insertClass(db, cindy, "GE-RLW 213", "10:00 AM - 1:00 PM", "Butil 1", "Thursday", "BSIS 2C")
        insertClass(db, deserie, "IS-DCN 213", "10:00 AM - 1:00 PM", "Comlab 3", "Friday", "BSIS 2C")
        insertClass(db, christian, "GE-STS 213", "1:00 PM - 4:00 PM", "Science Lab", "Friday", "BSIS 2C")
        insertClass(db, carl, "CT-WA 213", "8:00 AM - 11:00 AM", "Comlab 2", "Saturday", "BSIS 2C")
        insertClass(db, kyle, "CT-MAD 213", "5:00 PM - 8:00 PM", "Comlab 3", "Saturday", "BSIS 2C")

        // --- BSIS 2D ---
        insertClass(db, migs, "CC-DSA 213", "1:00 PM - 3:00 PM", "Comlab 3", "Tuesday", "BSIS 2D")
        insertClass(db, migs, "CC-IM 213", "1:00 PM - 4:00 PM", "Comlab 1", "Wednesday", "BSIS 2D")
        insertClass(db, eroll, "PE 212", "12:00 PM - 2:00 PM", "AC", "Thursday", "BSIS 2D")
        insertClass(db, cindy, "GE-RLW 213", "2:00 PM - 5:00 PM", "Butil 1", "Thursday", "BSIS 2D")
        insertClass(db, deserie, "IS-DCN 213", "7:00 AM - 10:00 AM", "Comlab 3", "Friday", "BSIS 2D")
        insertClass(db, christian, "GE-STS 213", "4:00 PM - 7:00 PM", "Science Lab", "Friday", "BSIS 2D")
        insertClass(db, carl, "CT-WA 213", "8:00 AM - 11:00 AM", "Comlab 3", "Saturday", "BSIS 2D")
        insertClass(db, kyle, "CT-MAD 213", "2:00 PM - 5:00 PM", "Comlab 2", "Saturday", "BSIS 2D")
    }

    private fun insertSecondSemesterClasses(db: SQLiteDatabase) {
        val kyle = "FAC2026-001"
        val deserie = "FAC2026-002"
        val migs = "FAC2026-003"
        val eroll = "FAC2026-004"
        val cindy = "FAC2026-005"
        val christian = "FAC2026-006"
        val carl = "FAC2026-007"
        val sem = 2

        // --- BSIS 2A ---
        insertClass(db, migs, "DM-HRM 323", "10:00 AM - 12:00 PM", "Comlab 3", "Tuesday", "BSIS 2A", sem)
        insertClass(db, migs, "AD-PT 223", "1:00 PM - 4:00 PM", "Comlab 1", "Tuesday", "BSIS 2A", sem)
        insertClass(db, eroll, "PE 222", "12:00 PM - 2:00 PM", "AC", "Thursday", "BSIS 2A", sem)
        insertClass(db, cindy, "GE-Hum 223", "2:00 PM - 5:00 PM", "Butil 1", "Thursday", "BSIS 2A", sem)
        insertClass(db, deserie, "IS-DMS 223", "10:00 AM - 1:00 PM", "Comlab 3", "Friday", "BSIS 2A", sem)
        insertClass(db, christian, "GE-Soc 223", "1:00 PM - 4:00 PM", "Science Lab", "Friday", "BSIS 2A", sem)
        insertClass(db, carl, "GE-Sci 223", "1:00 PM - 4:00 PM", "Comlab 3", "Saturday", "BSIS 2A", sem)
        insertClass(db, kyle, "CC-ADET 223", "5:00 PM - 8:00 PM", "Comlab 2", "Saturday", "BSIS 2A", sem)

        // --- BSIS 2B ---
        insertClass(db, migs, "DM-HRM 323", "8:00 AM - 10:00 AM", "Comlab 3", "Tuesday", "BSIS 2B", sem)
        insertClass(db, migs, "AD-PT 223", "4:00 PM - 7:00 PM", "Comlab 1", "Tuesday", "BSIS 2B", sem)
        insertClass(db, eroll, "PE 222", "12:00 PM - 2:00 PM", "AC", "Wednesday", "BSIS 2B", sem)
        insertClass(db, cindy, "GE-Hum 223", "2:00 PM - 5:00 PM", "Butil 1", "Wednesday", "BSIS 2B", sem)
        insertClass(db, deserie, "IS-DMS 223", "7:00 AM - 10:00 AM", "Comlab 3", "Friday", "BSIS 2B", sem)
        insertClass(db, christian, "GE-Soc 223", "4:00 PM - 7:00 PM", "Science Lab", "Friday", "BSIS 2B", sem)
        insertClass(db, carl, "GE-Sci 223", "8:00 AM - 11:00 AM", "Comlab 3", "Saturday", "BSIS 2B", sem)
        insertClass(db, kyle, "CC-ADET 223", "2:00 PM - 5:00 PM", "Comlab 2", "Saturday", "BSIS 2B", sem)

        // --- BSIS 2C ---
        insertClass(db, migs, "DM-HRM 323", "10:00 AM - 12:00 PM", "Comlab 1", "Tuesday", "BSIS 2C", sem)
        insertClass(db, migs, "AD-PT 223", "8:00 AM - 11:00 AM", "Comlab 1", "Wednesday", "BSIS 2C", sem)
        insertClass(db, eroll, "PE 222", "8:00 AM - 10:00 AM", "AC", "Thursday", "BSIS 2C", sem)
        insertClass(db, cindy, "GE-Hum 223", "10:00 AM - 1:00 PM", "Butil 1", "Thursday", "BSIS 2C", sem)
        insertClass(db, deserie, "IS-DMS 223", "10:00 AM - 1:00 PM", "Comlab 3", "Friday", "BSIS 2C", sem)
        insertClass(db, christian, "GE-Soc 223", "1:00 PM - 4:00 PM", "Science Lab", "Friday", "BSIS 2C", sem)
        insertClass(db, carl, "GE-Sci 223", "8:00 AM - 11:00 AM", "Comlab 2", "Saturday", "BSIS 2C", sem)
        insertClass(db, kyle, "CC-ADET 223", "5:00 PM - 8:00 PM", "Comlab 3", "Saturday", "BSIS 2C", sem)

        // --- BSIS 2D ---
        insertClass(db, migs, "DM-HRM 323", "1:00 PM - 3:00 PM", "Comlab 3", "Tuesday", "BSIS 2D", sem)
        insertClass(db, migs, "AD-PT 223", "1:00 PM - 4:00 PM", "Comlab 1", "Wednesday", "BSIS 2D", sem)
        insertClass(db, eroll, "PE 222", "12:00 PM - 2:00 PM", "AC", "Thursday", "BSIS 2D", sem)
        insertClass(db, cindy, "GE-Hum 223", "2:00 PM - 5:00 PM", "Butil 1", "Thursday", "BSIS 2D", sem)
        insertClass(db, deserie, "IS-DMS 223", "7:00 AM - 10:00 AM", "Comlab 3", "Friday", "BSIS 2D", sem)
        insertClass(db, christian, "GE-Soc 223", "4:00 PM - 7:00 PM", "Science Lab", "Friday", "BSIS 2D", sem)
        insertClass(db, carl, "GE-Sci 223", "8:00 AM - 11:00 AM", "Comlab 3", "Saturday", "BSIS 2D", sem)
        insertClass(db, kyle, "CC-ADET 223", "2:00 PM - 5:00 PM", "Comlab 2", "Saturday", "BSIS 2D", sem)
    }

    fun seedStudentSchedules(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO $STUDENT_SCHEDULE (student_id, class_id)
            SELECT u.user_id, c.id
            FROM $TABLE_USERS u
            JOIN $TABLE_CLASS_SCHEDULE c
              ON c.course = UPPER(TRIM(COALESCE(u.course,'') || ' ' || COALESCE(u.section,'')))
            WHERE u.role = 'STUDENT'
            """.trimIndent()
        )
    }

    fun parseMinutes(s: String): Int? {
        val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*([AaPp][Mm])\s*$""").matchEntire(s) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val pm = m.groupValues[3].equals("PM", ignoreCase = true)
        if (hour !in 1..12 || minute !in 0..59) return null
        if (hour == 12) hour = 0
        if (pm) hour += 12
        return hour * 60 + minute
    }

    fun parseTimeRange(time: String): Pair<Int, Int>? {
        val parts = time.split("-").map { it.trim() }
        if (parts.size != 2) return null
        val start = parseMinutes(parts[0]) ?: return null
        val end = parseMinutes(parts[1]) ?: return null
        return if (end > start) Pair(start, end) else null
    }

    fun formatTime(totalMin: Int): String {
        val h24 = totalMin / 60
        val m = totalMin % 60
        val suffix = if (h24 >= 12) "PM" else "AM"
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }

    fun normalizeDay(day: String): String? =
        DAYS.firstOrNull { it.equals(day.trim(), ignoreCase = true) }

    fun normalizeCourse(course: String): String =
        course.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)

    fun existsRow(sql: String, args: Array<String>): Boolean {
        val cursor = readableDatabase.rawQuery(sql, args)
        val found = cursor.moveToFirst()
        cursor.close()
        return found
    }

    fun userHasRole(userId: String, role: String): Boolean =
        existsRow("SELECT 1 FROM $TABLE_USERS WHERE user_id = ? AND role = ?", arrayOf(userId, role))

    fun hasOverlap(
        column: String, value: String, day: String,
        start: Int, end: Int, semester: Int, excludeId: Long
    ): Boolean = existsRow(
        """
        SELECT 1 FROM $TABLE_CLASS_SCHEDULE
        WHERE $column = ? COLLATE NOCASE AND day = ? AND semester = ?
          AND id != ? AND start_min < ? AND end_min > ?
        LIMIT 1
        """.trimIndent(),
        arrayOf(value, day, semester.toString(), excludeId.toString(), end.toString(), start.toString())
    )

    fun addSubject(code: String, subjectName: String, units: Int = 3): Boolean {
        val values = ContentValues().apply {
            put("code", code)
            put("subject_name", subjectName)
            put("units", units)
        }
        return writableDatabase.insert(TABLE_SUBJECTS, null, values) != -1L
    }

    fun updateSubject(id: Long, code: String, subjectName: String, units: Int): Boolean {
        return try {
            val values = ContentValues().apply {
                put("code", code)
                put("subject_name", subjectName)
                put("units", units)
            }
            writableDatabase.update(TABLE_SUBJECTS, values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: SQLiteConstraintException) {
            false // duplicate code
        }
    }

    fun deleteSubject(id: Long): Boolean {
        return try {
            writableDatabase.delete(TABLE_SUBJECTS, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    fun getAllSubjects(): List<SubjectItem> {
        val list = mutableListOf<SubjectItem>()
        val cursor = readableDatabase.rawQuery(
            "SELECT id, code, subject_name, units FROM $TABLE_SUBJECTS ORDER BY code", null
        )
        while (cursor.moveToNext()) {
            list.add(SubjectItem(cursor.getLong(0), cursor.getString(1),
                cursor.getString(2), cursor.getInt(3)))
        }
        cursor.close()
        return list
    }

    fun getSubjectName(code: String): String {
        val cursor = readableDatabase.rawQuery(
            "SELECT subject_name FROM $TABLE_SUBJECTS WHERE code = ?", arrayOf(code)
        )
        val name = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return name
    }

    fun getSubjectUnits(code: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT units FROM $TABLE_SUBJECTS WHERE code = ?", arrayOf(code)
        )
        val units = if (cursor.moveToFirst()) cursor.getInt(0) else 3
        cursor.close()
        return units
    }

    fun addClass(
        facultyId: String, subjectCode: String, course: String,
        day: String, time: String, room: String, semester: Int = 1
    ): OpResult = saveClass(null, facultyId, subjectCode, course, day, time, room, semester)

    fun updateClass(
        classId: Long, facultyId: String, subjectCode: String, course: String,
        day: String, time: String, room: String, semester: Int = 1
    ): OpResult = saveClass(classId, facultyId, subjectCode, course, day, time, room, semester)

    private fun saveClass(
        classId: Long?, facultyId: String, subjectCode: String, course: String,
        day: String, time: String, room: String, semester: Int
    ): OpResult {
        val code = subjectCode.trim()
        val section = normalizeCourse(course)
        val rm = room.trim()

        if (facultyId.isBlank() || code.isEmpty() || section.isEmpty() || rm.isEmpty()) {
            return OpResult(false, "Please fill in all fields")
        }
        val d = normalizeDay(day) ?: return OpResult(false, "Invalid day (use Monday to Sunday)")
        val range = parseTimeRange(time)
            ?: return OpResult(false, "Invalid time. Use format: 10:00 AM - 12:00 PM")
        val (start, end) = range

        if (!userHasRole(facultyId, "FACULTY")) return OpResult(false, "Faculty not found")
        if (getSubjectName(code).isEmpty()) return OpResult(false, "Subject $code does not exist")

        val exclude = classId ?: -1L

        if (existsRow(
                "SELECT 1 FROM $TABLE_CLASS_SCHEDULE WHERE course = ? AND subject_code = ? AND semester = ? AND id != ?",
                arrayOf(section, code, semester.toString(), exclude.toString())
            )
        ) return OpResult(false, "$code is already scheduled for $section")

        if (hasOverlap("faculty_id", facultyId, d, start, end, semester, exclude))
            return OpResult(false, "Faculty already has a class at this day and time")
        if (hasOverlap("course", section, d, start, end, semester, exclude))
            return OpResult(false, "$section already has a class at this day and time")
        if (hasOverlap("room", rm, d, start, end, semester, exclude))
            return OpResult(false, "Room $rm is already taken at this day and time")

        val values = ContentValues().apply {
            put("faculty_id", facultyId)
            put("subject_code", code)
            put("course", section)
            put("day", d)
            put("time", "${formatTime(start)} - ${formatTime(end)}")
            put("start_min", start)
            put("end_min", end)
            put("room", rm)
            put("semester", semester)
        }

        return try {
            val ok = if (classId == null) {
                writableDatabase.insert(TABLE_CLASS_SCHEDULE, null, values) != -1L
            } else {
                writableDatabase.update(TABLE_CLASS_SCHEDULE, values, "id = ?", arrayOf(classId.toString())) > 0
            }
            if (ok) OpResult(true, if (classId == null) "Schedule added" else "Schedule updated")
            else OpResult(false, "Save failed")
        } catch (e: SQLiteConstraintException) {
            OpResult(false, "Save failed: ${e.message}")
        }
    }

    fun deleteClass(classId: Long): Boolean =
        writableDatabase.delete(TABLE_CLASS_SCHEDULE, "id = ?", arrayOf(classId.toString())) > 0

    val classSelect = """
        SELECT c.id, c.subject_code, sub.subject_name, c.time, c.day, c.room,
               u.name AS instructor, c.course, c.faculty_id, c.semester
        FROM $TABLE_CLASS_SCHEDULE c
        LEFT JOIN $TABLE_SUBJECTS sub ON sub.code = c.subject_code
        LEFT JOIN $TABLE_USERS u ON u.user_id = c.faculty_id
    """.trimIndent()

    val dayOrder = """
        CASE c.day WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3
        WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6
        WHEN 'Sunday' THEN 7 ELSE 8 END
    """.trimIndent()

    fun readSchedules(cursor: Cursor): List<ScheduleItem> {
        val list = mutableListOf<ScheduleItem>()
        while (cursor.moveToNext()) {
            list.add(
                ScheduleItem(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    subjectCode = cursor.getString(
                        cursor.getColumnIndexOrThrow("subject_code")
                    ) ?: "",
                    subjectName = cursor.getString(
                        cursor.getColumnIndexOrThrow("subject_name")
                    ) ?: "",
                    time = cursor.getString(
                        cursor.getColumnIndexOrThrow("time")
                    ) ?: "",
                    day = cursor.getString(
                        cursor.getColumnIndexOrThrow("day")
                    ) ?: "",
                    room = cursor.getString(
                        cursor.getColumnIndexOrThrow("room")
                    ) ?: "",
                    instructor = cursor.getString(
                        cursor.getColumnIndexOrThrow("instructor")
                    ) ?: "",
                    course = cursor.getString(
                        cursor.getColumnIndexOrThrow("course")
                    ) ?: "",
                    facultyId = cursor.getString(
                        cursor.getColumnIndexOrThrow("faculty_id")
                    ) ?: "",
                    semester = cursor.getInt(
                        cursor.getColumnIndexOrThrow("semester")
                    )
                )
            )
        }
        cursor.close()
        return list
    }

    fun getClassesByFaculty(facultyId: String, semester: Int = 1): List<ScheduleItem> =
        readSchedules(
            readableDatabase.rawQuery(
                "$classSelect WHERE c.faculty_id = ? AND c.semester = ? ORDER BY $dayOrder, c.start_min",
                arrayOf(facultyId, semester.toString())
            )
        )


    fun getClassesBySection(
        course: String,
        semester: Int? = null
    ): List<ScheduleItem> {

        val sql: String
        val args: Array<String>

        if (semester == null) {
            sql = """
            $classSelect
            WHERE c.course = ?
            ORDER BY $dayOrder, c.start_min
        """.trimIndent()

            args = arrayOf(normalizeCourse(course))
        } else {
            sql = """
            $classSelect
            WHERE c.course = ? AND c.semester = ?
            ORDER BY $dayOrder, c.start_min
        """.trimIndent()

            args = arrayOf(
                normalizeCourse(course),
                semester.toString()
            )
        }

        return readSchedules(
            readableDatabase.rawQuery(sql, args)
        )
    }

    fun getAllClasses(
        semester: Int? = null
    ): List<ScheduleItem> {

        return if (semester == null) {
            readSchedules(
                readableDatabase.rawQuery(
                    """
                $classSelect
                ORDER BY c.course, $dayOrder, c.start_min
                """.trimIndent(),
                    null
                )
            )
        } else {
            readSchedules(
                readableDatabase.rawQuery(
                    """
                $classSelect
                WHERE c.semester = ?
                ORDER BY c.course, $dayOrder, c.start_min
                """.trimIndent(),
                    arrayOf(semester.toString())
                )
            )
        }
    }


    fun getStudentSchedule(studentId: String, semester: Int = 1): List<ScheduleItem> =
        readSchedules(
            readableDatabase.rawQuery(
                """
                $classSelect
                JOIN $STUDENT_SCHEDULE ss ON ss.class_id = c.id
                WHERE ss.student_id = ? AND c.semester = ?
                ORDER BY $dayOrder, c.start_min
                """.trimIndent(),
                arrayOf(studentId, semester.toString())
            )
        )

    fun getStudentSubjects(studentId: String, semester: Int = 1): List<SubjectItem> {
        val list = mutableListOf<SubjectItem>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT DISTINCT s.id, s.code, s.subject_name, s.units
            FROM $STUDENT_SCHEDULE ss
            JOIN $TABLE_CLASS_SCHEDULE c ON c.id = ss.class_id
            JOIN $TABLE_SUBJECTS s ON s.code = c.subject_code
            WHERE ss.student_id = ? AND c.semester = ?
            ORDER BY s.code
            """.trimIndent(),
            arrayOf(studentId, semester.toString())
        )
        while (cursor.moveToNext()) {
            list.add(SubjectItem(cursor.getLong(0), cursor.getString(1),
                cursor.getString(2), cursor.getInt(3)))
        }
        cursor.close()
        return list
    }

    fun getAddableClasses(studentId: String, facultyId: String, semester: Int = 1): List<ScheduleItem> =
        readSchedules(
            readableDatabase.rawQuery(
                """
                $classSelect
                WHERE c.faculty_id = ? AND c.semester = ?
                  AND c.id NOT IN (SELECT class_id FROM $STUDENT_SCHEDULE WHERE student_id = ?)
                  AND c.subject_code NOT IN (
                      SELECT c2.subject_code FROM $STUDENT_SCHEDULE ss2
                      JOIN $TABLE_CLASS_SCHEDULE c2 ON c2.id = ss2.class_id
                      WHERE ss2.student_id = ? AND c2.semester = ?
                  )
                ORDER BY c.course, $dayOrder, c.start_min
                """.trimIndent(),
                arrayOf(facultyId, semester.toString(), studentId, studentId, semester.toString())
            )
        )

    fun addToStudentSchedule(studentId: String, classId: Long): OpResult {
        val db = writableDatabase
        if (!userHasRole(studentId, "STUDENT")) return OpResult(false, "Student not found")

        val c = db.rawQuery(
            "SELECT subject_code, day, start_min, end_min, semester FROM $TABLE_CLASS_SCHEDULE WHERE id = ?",
            arrayOf(classId.toString())
        )
        if (!c.moveToFirst()) {
            c.close()
            return OpResult(false, "Class not found")
        }
        val code = c.getString(0)
        val day = c.getString(1)
        val start = c.getInt(2)
        val end = c.getInt(3)
        val sem = c.getInt(4)
        c.close()

        if (existsRow(
                """
                SELECT 1 FROM $STUDENT_SCHEDULE ss
                JOIN $TABLE_CLASS_SCHEDULE c2 ON c2.id = ss.class_id
                WHERE ss.student_id = ? AND c2.subject_code = ? AND c2.semester = ?
                """.trimIndent(),
                arrayOf(studentId, code, sem.toString())
            )
        ) return OpResult(false, "$code is already in this student's schedule")

        val cur = db.rawQuery(
            """
            SELECT c2.subject_code FROM $STUDENT_SCHEDULE ss
            JOIN $TABLE_CLASS_SCHEDULE c2 ON c2.id = ss.class_id
            WHERE ss.student_id = ? AND c2.day = ? AND c2.semester = ?
              AND c2.start_min < ? AND c2.end_min > ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(studentId, day, sem.toString(), end.toString(), start.toString())
        )
        if (cur.moveToFirst()) {
            val other = cur.getString(0)
            cur.close()
            return OpResult(false, "Conflicts with $other (same day and time)")
        }
        cur.close()

        val values = ContentValues().apply {
            put("student_id", studentId)
            put("class_id", classId)
        }
        return if (db.insert(STUDENT_SCHEDULE, null, values) != -1L)
            OpResult(true, "Added to student's schedule")
        else OpResult(false, "Could not add to schedule")
    }

    fun addClassToSection(classId: Long): OpResult {
        val c = readableDatabase.rawQuery(
            "SELECT course FROM $TABLE_CLASS_SCHEDULE WHERE id = ?", arrayOf(classId.toString())
        )
        if (!c.moveToFirst()) {
            c.close()
            return OpResult(false, "Class not found")
        }
        val section = c.getString(0)
        c.close()

        val ids = mutableListOf<String>()
        val cur = readableDatabase.rawQuery(
            """
            SELECT user_id FROM $TABLE_USERS
            WHERE role = 'STUDENT'
              AND UPPER(TRIM(COALESCE(course,'') || ' ' || COALESCE(section,''))) = ?
            """.trimIndent(),
            arrayOf(section)
        )
        while (cur.moveToNext()) ids.add(cur.getString(0))
        cur.close()

        if (ids.isEmpty()) return OpResult(false, "No students found in $section")

        var added = 0
        var skipped = 0
        for (id in ids) {
            if (addToStudentSchedule(id, classId).success) added++ else skipped++
        }
        return OpResult(added > 0, "Added to $added student(s), skipped $skipped (already added or time conflict)")
    }

    fun removeFromStudentSchedule(studentId: String, classId: Long): Boolean =
        writableDatabase.delete(
            STUDENT_SCHEDULE, "student_id = ? AND class_id = ?",
            arrayOf(studentId, classId.toString())
        ) > 0

    fun debugDumpGrades(tag: String = "GRADES") {
        readableDatabase.rawQuery(
            "SELECT student_id, subject_code, midterm, final, semester FROM $TABLE_GRADES", null
        ).use { c ->
            android.util.Log.d(tag, "TOTAL grade rows = ${c.count}")
            while (c.moveToNext()) {
                android.util.Log.d(
                    tag,
                    "row: student=${c.getString(0)} subj=${c.getString(1)} " +
                            "mid=${if (c.isNull(2)) "null" else c.getDouble(2)} " +
                            "fin=${if (c.isNull(3)) "null" else c.getDouble(3)} sem=${c.getInt(4)}"
                )
            }
        }
    }

    fun registerStudent(
        userId: String, name: String, email: String = "", course: String = "",
        section: String = "", birthdate: String = "", address: String = "",
        campus: String = "", password: String, role: String = "STUDENT", position: String = ""
    ): OpResult {
        val id = userId.trim()
        if (id.isEmpty() || name.isBlank() || password.isEmpty()) {
            return OpResult(false, "Please fill in all required fields")
        }

        // Kung seeded account na hindi pa na-claim (default password), i-claim ito
        var claimId: String? = null
        readableDatabase.rawQuery(
            "SELECT user_id, role, password FROM $TABLE_USERS WHERE user_id = ? COLLATE NOCASE",
            arrayOf(id)
        ).use { c ->
            if (c.moveToFirst()) {
                val isUnclaimedSeed = c.getString(1).equals("STUDENT", ignoreCase = true) &&
                        c.getString(2) == "student123"
                if (!isUnclaimedSeed || !role.equals("STUDENT", ignoreCase = true)) {
                    return OpResult(false, "User ID already exists")
                }
                claimId = c.getString(0)
            }
        }

        if (claimId != null) {
            val claim = ContentValues().apply {
                put("name", name.trim().replace(Regex("\\s+"), " "))
                put("email", email)
                if (course.isNotBlank()) put("course", course.trim())
                if (section.isNotBlank()) put("section", section.trim())
                put("birthdate", birthdate)
                put("address", address)
                put("campus", campus)
                put("password", password)
            }
            return try {
                writableDatabase.update(TABLE_USERS, claim, "user_id = ?", arrayOf(claimId!!))
                linkStudentToSectionClasses(claimId!!)
                OpResult(true, "Account created")
            } catch (e: SQLException) {
                OpResult(false, "Database error: ${e.message}")
            }
        }

        // ---- dito na ang dati mong code (hindi ginalaw) ----
        val values = ContentValues().apply {
            put("user_id", id)
            put("name", name.trim().replace(Regex("\\s+"), " "))
            put("email", email)
            put("role", role)
            put("course", course.trim())
            put("section", section.trim())
            put("position", position)
            put("birthdate", birthdate)
            put("address", address)
            put("campus", campus)
            put("password", password)
        }

        return try {
            writableDatabase.insertOrThrow(TABLE_USERS, null, values)
            if (role.equals("STUDENT", ignoreCase = true)) linkStudentToSectionClasses(id)
            OpResult(true, "Account created")
        } catch (e: SQLiteConstraintException) {
            OpResult(false, "Could not save account: ${e.message}")
        } catch (e: SQLException) {
            OpResult(false, "Database error: ${e.message}")
        }
    }

    fun linkStudentToSectionClasses(userId: String) {
        writableDatabase.execSQL(
            """
        INSERT OR IGNORE INTO $STUDENT_SCHEDULE (student_id, class_id)
        SELECT u.user_id, c.id
        FROM $TABLE_USERS u
        JOIN $TABLE_CLASS_SCHEDULE c
          ON c.course = UPPER(TRIM(COALESCE(u.course,'') || ' ' || COALESCE(u.section,'')))
        WHERE u.user_id = ? AND u.role = 'STUDENT'
        """.trimIndent(),
            arrayOf(userId)
        )
    }

    fun readStudents(cursor: Cursor): List<StudentItem> {
        val list = mutableListOf<StudentItem>()
        while (cursor.moveToNext()) {
            list.add(
                StudentItem(
                    userId = cursor.getString(0),
                    name = cursor.getString(1),
                    course = cursor.getString(2) ?: "",
                    section = cursor.getString(3) ?: "",
                    email = cursor.getString(4) ?: ""
                )
            )
        }
        cursor.close()
        return list
    }

    fun getAllStudents(): List<StudentItem> =
        readStudents(
            readableDatabase.rawQuery(
                "SELECT user_id, name, course, section, email FROM $TABLE_USERS WHERE role = 'STUDENT' ORDER BY name",
                null
            )
        )

    fun getStudentsBySection(course: String): List<StudentItem> =
        readStudents(
            readableDatabase.rawQuery(
                """
                SELECT user_id, name, course, section, email FROM $TABLE_USERS
                WHERE role = 'STUDENT'
                  AND UPPER(TRIM(COALESCE(course,'') || ' ' || COALESCE(section,''))) = ?
                ORDER BY name
                """.trimIndent(),
                arrayOf(normalizeCourse(course))
            )
        )

    fun updateStudentSection(studentId: String, course: String, section: String): Boolean {
        val values = ContentValues().apply {
            put("course", course.trim())
            put("section", section.trim())
        }
        return writableDatabase.update(
            TABLE_USERS, values, "user_id = ? AND role = 'STUDENT'", arrayOf(studentId)
        ) > 0
    }

    fun deleteStudent(studentId: String): Boolean =
        writableDatabase.delete(TABLE_USERS, "user_id = ? AND role = 'STUDENT'", arrayOf(studentId)) > 0

    fun getStudentName(studentId: String): String = getUserName(studentId)

    fun getUserName(userId: String): String {
        val cursor = readableDatabase.rawQuery(
            "SELECT name FROM $TABLE_USERS WHERE user_id = ?", arrayOf(userId)
        )
        val name = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return name
    }

    fun getGradeSheet(classId: Long): List<GradeEntry> {
        val list = mutableListOf<GradeEntry>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT u.user_id, u.name, COALESCE(g.midterm, 0), COALESCE(g.final, 0)
            FROM $STUDENT_SCHEDULE ss
            JOIN $TABLE_CLASS_SCHEDULE c ON c.id = ss.class_id
            JOIN $TABLE_USERS u ON u.user_id = ss.student_id
            LEFT JOIN $TABLE_GRADES g
              ON g.student_id = u.user_id AND g.subject_code = c.subject_code AND g.semester = c.semester
            WHERE ss.class_id = ?
            ORDER BY u.name
            """.trimIndent(),
            arrayOf(classId.toString())
        )
        while (cursor.moveToNext()) {
            list.add(GradeEntry(cursor.getString(0), cursor.getString(1),
                cursor.getDouble(2), cursor.getDouble(3)))
        }
        cursor.close()
        return list
    }

    /**
     * Binabasa ang grade ng isang student sa isang subject at semester.
     * Ibinabalik ang Pair(midterm, final); null ang bawat isa kung hindi pa naipasok.
     */
    fun getGradeValues(studentId: String, subjectCode: String, semester: Int): Pair<Double?, Double?> {
        val cursor = readableDatabase.rawQuery(
            "SELECT midterm, final FROM $TABLE_GRADES WHERE student_id = ? AND subject_code = ? AND semester = ?",
            arrayOf(studentId, subjectCode, semester.toString())
        )
        var result: Pair<Double?, Double?> = Pair(null, null)
        if (cursor.moveToFirst()) {
            val m = if (cursor.isNull(0)) null else cursor.getDouble(0)
            val f = if (cursor.isNull(1)) null else cursor.getDouble(1)
            result = Pair(m, f)
        }
        cursor.close()
        return result
    }

    fun deleteGrade(studentId: String, subjectCode: String, semester: Int): Boolean =
        writableDatabase.delete(
            TABLE_GRADES, "student_id = ? AND subject_code = ? AND semester = ?",
            arrayOf(studentId, subjectCode, semester.toString())
        ) > 0

    /**
     * Blank grades (null) ay NULL ang laman, para ma-differentiate ang tunay na 0 sa "hindi pa naipasok".
     * Kapag parehong blangko, buburahin ang row.
     */
    fun saveGrade(
        studentId: String, subjectCode: String, subjectName: String = "",
        midterm: Double?, final: Double?, semester: Int
    ): Boolean {
        val db = writableDatabase

        if (midterm == null && final == null) {
            db.delete(
                TABLE_GRADES, "student_id = ? AND subject_code = ? AND semester = ?",
                arrayOf(studentId, subjectCode, semester.toString())
            )
            return true
        }

        val values = ContentValues().apply {
            put("student_id", studentId)
            put("subject_code", subjectCode)
            if (midterm != null) put("midterm", midterm) else putNull("midterm")
            if (final != null) put("final", final) else putNull("final")
            put("semester", semester)
        }

        return try {
            val rows = db.update(
                TABLE_GRADES, values,
                "student_id = ? AND subject_code = ? AND semester = ?",
                arrayOf(studentId, subjectCode, semester.toString())
            )
            if (rows > 0) true
            else db.insertOrThrow(TABLE_GRADES, null, values) != -1L
        } catch (e: SQLException) {
            android.util.Log.e("GRADE", "saveGrade failed: $studentId $subjectCode sem=$semester", e)
            false
        }
    }

    /**
     * Lahat ng subject sa schedule ng student para sa semester, kasama ang grade kung meron na.
     * Kaya laging may lalabas na subject kahit wala pang grade.
     */
    fun getStudentGrades(studentId: String, semester: Int): List<StudentGrade> {
        val list = mutableListOf<StudentGrade>()
        val targetCodes = if (semester == 1) {
            listOf("CC-IM 213", "CC-DSA 213", "IS-DCN 213", "CT-MAD 213", "CT-WA 213", "GE-RLW 213", "GE-STS 213", "PE 212")
        } else {
            listOf("IS-DMS 223", "DM-HRM 323", "AD-PT 223", "GE-Hum 223", "GE-Soc 223", "GE-Sci 223", "CC-ADET 223", "PE 222")
        }

        val placeholders = targetCodes.joinToString(",") { "?" }
        val queryArgs = mutableListOf(studentId, semester.toString())
        queryArgs.addAll(targetCodes)

        val sql = """
            SELECT s.code, s.subject_name, g.midterm, g.final
            FROM $TABLE_SUBJECTS s
            LEFT JOIN $TABLE_GRADES g
              ON g.student_id = ? AND g.subject_code = s.code AND g.semester = ?
            WHERE s.code IN ($placeholders)
            ORDER BY s.code
        """.trimIndent()

        val cursor = readableDatabase.rawQuery(sql, queryArgs.toTypedArray())
        while (cursor.moveToNext()) {
            val hasMid = !cursor.isNull(2)
            val hasFin = !cursor.isNull(3)
            list.add(
                StudentGrade(
                    subjectCode = cursor.getString(0),
                    subjectName = cursor.getString(1),
                    midterm = if (hasMid) cursor.getDouble(2) else 0.0,
                    final = if (hasFin) cursor.getDouble(3) else 0.0,
                    midtermEntered = hasMid,
                    finalEntered = hasFin
                )
            )
        }
        cursor.close()
        return list
    }

    /** Academic status base sa average. null = wala pang grade. */
    fun getAcademicStatus(average: Double?): String = when {
        average == null -> "No Grade"
        average <= PASSING_MAX -> "Passed"
        else -> "Failed"
    }

    /** Average ng lahat ng subject (na may grade) sa isang semester. null kung wala pang grade. */
    fun getSemesterAverage(studentId: String, semester: Int): Double? {
        val averages = getStudentGrades(studentId, semester)
            .filter { it.hasAnyGrade }
            .map { it.average }
        return if (averages.isEmpty()) null else averages.sum() / averages.size
    }

    /** Overall average ng 1st at 2nd semester. null kung wala pang grade. */
    fun getOverallAverage(studentId: String): Double? {
        val averages = (1..2).flatMap { sem ->
            getStudentGrades(studentId, sem).filter { it.hasAnyGrade }.map { it.average }
        }
        return if (averages.isEmpty()) null else averages.sum() / averages.size
    }

    /**
     * Dating bug: binubura ng onUpgrade ang LAHAT ng table, kasama ang grades, tuwing tataasan ang version.
     * Ngayon, sine-save muna ang grades at ibinabalik pagkatapos mag-recreate.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion >= 25) {
            migrateStudentIds(db)   // rename lang ng ID, walang binubura
            return
        }

        // Lumang code (para sa mga version na mas mababa sa 25)
        val savedGrades = mutableListOf<ContentValues>()
        try {
            db.rawQuery(
                "SELECT student_id, subject_code, prelim, midterm, final, semester FROM $TABLE_GRADES",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    savedGrades.add(ContentValues().apply {
                        put("student_id", c.getString(0))
                        put("subject_code", c.getString(1))
                        if (c.isNull(2)) putNull("prelim") else put("prelim", c.getDouble(2))
                        if (c.isNull(3)) putNull("midterm") else put("midterm", c.getDouble(3))
                        if (c.isNull(4)) putNull("final") else put("final", c.getDouble(4))
                        put("semester", c.getInt(5))
                    })
                }
            }
        } catch (e: SQLException) {
            // lumang schema na walang grades table o ibang columns; walang maibabalik
        }

        db.execSQL("DROP TABLE IF EXISTS $TABLE_GRADES")
        db.execSQL("DROP TABLE IF EXISTS student_subjects")
        db.execSQL("DROP TABLE IF EXISTS $STUDENT_SCHEDULE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLASS_SCHEDULE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUBJECTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEACHERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)

        for (cv in savedGrades) {
            try {
                db.insertWithOnConflict(TABLE_GRADES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            } catch (e: SQLException) {
                // student o subject na wala na sa bagong data; laktawan
            }
        }
    }

    // --- ADMIN HELPER METHODS ---

    fun ensureAdminExists(db: SQLiteDatabase = writableDatabase) {
        try {
            val cursor = db.rawQuery(
                "SELECT 1 FROM $TABLE_USERS WHERE UPPER(user_id) = 'ADMIN' OR role = 'ADMIN'",
                null
            )
            val exists = cursor.moveToFirst()
            cursor.close()

            if (!exists) {
                val values = ContentValues().apply {
                    put("user_id", "ADMIN")
                    put("name", "System Administrator")
                    put("role", "ADMIN")
                    put("course", "Administration")
                    put("position", "System Admin")
                    put("email", "admin@bpc.edu.ph")
                    put("password", "ADMIN01")
                }
                db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Failed to ensure admin exists", e)
        }
    }

    data class DeptStudentCount(val department: String, val count: Int)

    fun getDepartmentStudentCounts(): List<DeptStudentCount> {
        val list = mutableListOf<DeptStudentCount>()
        val db = readableDatabase
        db.rawQuery(
            """
            SELECT CASE WHEN UPPER(TRIM(COALESCE(course,''))) = '' THEN 'Unassigned' ELSE UPPER(TRIM(course)) END AS dept,
                   COUNT(*) AS student_count
            FROM $TABLE_USERS
            WHERE role = 'STUDENT'
            GROUP BY dept
            ORDER BY student_count DESC, dept ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(DeptStudentCount(cursor.getString(0), cursor.getInt(1)))
            }
        }
        return list
    }

    data class AccountSummaryCounts(val totalStudents: Int, val totalFaculty: Int, val totalAdmins: Int, val totalUsers: Int)

    fun getAccountSummaryCounts(): AccountSummaryCounts {
        var students = 0
        var faculty = 0
        var admins = 0
        var total = 0
        val db = readableDatabase
        db.rawQuery(
            """
            SELECT role, COUNT(*) 
            FROM $TABLE_USERS 
            GROUP BY role
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val role = cursor.getString(0)?.uppercase() ?: ""
                val cnt = cursor.getInt(1)
                total += cnt
                when (role) {
                    "STUDENT" -> students += cnt
                    "FACULTY" -> faculty += cnt
                    "ADMIN" -> admins += cnt
                }
            }
        }
        return AccountSummaryCounts(students, faculty, admins, total)
    }

    data class UserAccountItem(
        val id: Long,
        val userId: String,
        val name: String,
        val role: String,
        val course: String,
        val section: String,
        val position: String,
        val email: String,
        val password: String,
        val contactNum: String = "",
        val department: String = "",
        val address: String = "",
        val imageUri: String = ""
    )

    fun getAllUserAccounts(roleFilter: String = "ALL", searchQuery: String = ""): List<UserAccountItem> {
        val list = mutableListOf<UserAccountItem>()
        val db = readableDatabase
        val selectionList = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (!roleFilter.equals("ALL", ignoreCase = true)) {
            selectionList.add("role = ?")
            selectionArgs.add(roleFilter.uppercase())
        }

        if (searchQuery.isNotBlank()) {
            val q = "%${searchQuery.trim().lowercase()}%"
            selectionList.add("(LOWER(user_id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(COALESCE(course,'')) LIKE ? OR LOWER(COALESCE(email,'')) LIKE ?)")
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
            selectionArgs.add(q)
        }

        val whereClause = if (selectionList.isNotEmpty()) "WHERE " + selectionList.joinToString(" AND ") else ""
        val sql = "SELECT id, user_id, name, role, COALESCE(course,''), COALESCE(section,''), COALESCE(position,''), COALESCE(email,''), password, COALESCE(contact_num,''), COALESCE(department,''), COALESCE(address,''), COALESCE(image_uri,'') FROM $TABLE_USERS $whereClause ORDER BY role ASC, name ASC"

        db.rawQuery(sql, selectionArgs.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    UserAccountItem(
                        id = cursor.getLong(0),
                        userId = cursor.getString(1),
                        name = cursor.getString(2),
                        role = cursor.getString(3),
                        course = cursor.getString(4),
                        section = cursor.getString(5),
                        position = cursor.getString(6),
                        email = cursor.getString(7),
                        password = cursor.getString(8),
                        contactNum = cursor.getString(9),
                        department = cursor.getString(10),
                        address = cursor.getString(11),
                        imageUri = cursor.getString(12)
                    )
                )
            }
        }
        return list
    }

    fun deleteUserByUserId(userId: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_USERS, "user_id = ?", arrayOf(userId)) > 0
    }

    fun upsertUserAccount(
        userId: String,
        name: String,
        role: String,
        course: String = "",
        section: String = "",
        position: String = "",
        email: String = "",
        password: String = "1234",
        contactNum: String = "",
        department: String = "",
        address: String = "",
        imageUri: String = ""
    ): Boolean {
        val db = writableDatabase
        val finalDept = if (department.isNotBlank()) department else course
        val values = ContentValues().apply {
            put("user_id", userId)
            put("name", name)
            put("role", role.uppercase())
            put("course", course)
            put("section", section)
            put("position", position)
            put("email", email)
            put("password", password)
            put("contact_num", contactNum)
            put("department", finalDept)
            put("address", address)
            put("image_uri", imageUri)
        }
        val rows = db.update(TABLE_USERS, values, "LOWER(user_id) = LOWER(?)", arrayOf(userId))

        if (role.equals("FACULTY", ignoreCase = true)) {
            val teacherValues = ContentValues().apply {
                put("name", name)
                if (email.isNotBlank()) put("email", email)
                put("department", finalDept)
                if (position.isNotBlank()) put("position", position)
            }
            db.update(TABLE_TEACHERS, teacherValues, "LOWER(teacher_id) = LOWER(?)", arrayOf(userId))
        }

        if (rows > 0) return true
        val res = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return res != -1L
    }

    fun updateUserProfileImage(userId: String, imageUri: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("image_uri", imageUri)
        }
        return db.update(TABLE_USERS, values, "user_id = ?", arrayOf(userId)) > 0
    }
}