package com.example.gradetrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale
import android.database.SQLException

data class StudentGrade(
    val subjectCode: String,
    val subjectName: String,
    val midterm: Double,
    val final: Double
) {
    val average: Double
        get() = (midterm + final) / 2.0
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
        private const val DATABASE_VERSION = 21

        const val TABLE_USERS = "users"
        const val TABLE_GRADES = "grades"
        const val TABLE_SUBJECTS = "subjects"
        const val TABLE_TEACHERS = "teachers"
        const val TABLE_CLASS_SCHEDULE = "faculty_schedule" // schedule na tinuturo ng faculty
        const val STUDENT_SCHEDULE = "student_schedule"     // schedule ng bawat student

        val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        private const val SEED_COURSE = "BSIS"
        private const val SEED_SECTION = "2C"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        insertSubjects(db)
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
                midterm REAL DEFAULT 0,
                final REAL DEFAULT 0,
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
        seedStudentSchedules(db)
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
        insertSubject(
            db,
            "PE 212",
            "PHYSICAL ACTIVITIES TOWARDS HEALTH & FITNESS 3-ARNIS (PATHFit 3)"
        )

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

    private fun insertStudents(db: SQLiteDatabase) {
        val students = listOf(
            "Alab, Sopia C.", "Alfaro, Maxwell James C.", "Alisa, Jasmine Margareth M.",
            "Almazar, Juliana Maryse M.", "Amor, Ralph Jayrhard G.", "Bacaran, Alexander G.",
            "Bacsal, Rochelyn P.", "Bautista, Kaye Cel B", "Billalba, Lexter John M.",
            "Borja, Kristine Joy M.", "Cabrera, Harvey Klaus R.", "Cabuz, Jewell Ace Jhay V.",
            "Callanta, Jullian Maria M.", "Cayabyab, Kleian B.", "Dela Cruz, Marco J.",
            "Delara, Bryan T.", "Ebalobo, Jedediah V.", "Espejo, Kyle Russell B.",
            "Espino, Jerome M.", "Eugenio, John Erick R.", "Eva, Angel T.",
            "Ferrer, Jacky Rose D.", "Galera, Jhayren", "Gamas, Eva Christine C.",
            "Garcia, Gabriel Angelo C", "Gumahob, Arnold Yuri O.", "Gural, Calvin John H.",
            "Isidro, Kristina Cassandra P.", "Lauron, Melissa Faith L.", "Losañez, Jarrell Marcus P.",
            "Lumba, Aizen R.", "Marcos, Red Lourence R.", "Maron, Renz Ryan G.",
            "Navalta, James Laurenz F.", "Oclarit, Aljean L.", "Pascual, Leira C.",
            "Perez, Raeven M.", "Quirona, Reyver Ceyzer DC.", "Ramos, Diana",
            "Reyes, Kings Leanne R.", "Rodiel, Rainier James P.", "Ronquillo, Andrea Clarizze M.",
            "Salvador, Cedrick John", "Santos, Antonette DC.", "Sapitan, Moises Serg G.",
            "Sauadan, Casie Kate R.", "Talucud, Catherine P.", "Trinidad, Irene P.",
            "Tumangan, Ericson B.", "Zacarias, Daniel"
        )
        students.forEachIndexed { index, name ->
            val studentNumber = String.format("%04d", index + 1)
            insertUser(db, "STU2026-$studentNumber", name, "STUDENT", SEED_COURSE, SEED_SECTION, "",
                "student123")
        }
    }

    private fun insertClass(
        db: SQLiteDatabase, facultyId: String, subjectCode: String,
        time: String, room: String, day: String, course: String
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
            put("semester", 1)
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

    private fun seedStudentSchedules(db: SQLiteDatabase) {
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

    private fun parseMinutes(s: String): Int? {
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

    private fun formatTime(totalMin: Int): String {
        val h24 = totalMin / 60
        val m = totalMin % 60
        val suffix = if (h24 >= 12) "PM" else "AM"
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }

    private fun normalizeDay(day: String): String? =
        DAYS.firstOrNull { it.equals(day.trim(), ignoreCase = true) }

    private fun normalizeCourse(course: String): String =
        course.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)

    private fun existsRow(sql: String, args: Array<String>): Boolean {
        val cursor = readableDatabase.rawQuery(sql, args)
        val found = cursor.moveToFirst()
        cursor.close()
        return found
    }

    private fun userHasRole(userId: String, role: String): Boolean =
        existsRow("SELECT 1 FROM $TABLE_USERS WHERE user_id = ? AND role = ?", arrayOf(userId, role))

    private fun hasOverlap(
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

    private val classSelect = """
        SELECT c.id, c.subject_code, sub.subject_name, c.time, c.day, c.room,
               u.name AS instructor, c.course, c.faculty_id, c.semester
        FROM $TABLE_CLASS_SCHEDULE c
        LEFT JOIN $TABLE_SUBJECTS sub ON sub.code = c.subject_code
        LEFT JOIN $TABLE_USERS u ON u.user_id = c.faculty_id
    """.trimIndent()

    private val dayOrder = """
        CASE c.day WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3
        WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6
        WHEN 'Sunday' THEN 7 ELSE 8 END
    """.trimIndent()

    private fun readSchedules(cursor: Cursor): List<ScheduleItem> {
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

    fun registerStudent(
        userId: String, name: String, email: String = "", course: String = "",
        section: String = "", birthdate: String = "", address: String = "",
        campus: String = "", password: String, role: String = "STUDENT", position: String = ""
    ): OpResult {
        val id = userId.trim()
        if (id.isEmpty() || name.isBlank() || password.isEmpty()) {
            return OpResult(false, "Please fill in all required fields")
        }

        if (existsRow("SELECT 1 FROM $TABLE_USERS WHERE user_id = ? COLLATE NOCASE", arrayOf(id))) {
            return OpResult(false, "User ID already exists")
        }

        val values = ContentValues().apply {
            put("user_id", id)
            put("name", name.trim())
            put("email", email)
            put("role", role)
            put("course", course)
            put("section", section)
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

    private fun linkStudentToSectionClasses(userId: String) {
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

    private fun readStudents(cursor: Cursor): List<StudentItem> {
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

    fun saveGrade(
        studentId: String, subjectCode: String, subjectName: String = "",
        midterm: Double, final: Double, semester: Int
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("student_id", studentId)
            put("subject_code", subjectCode)
            put("midterm", midterm)
            put("final", final)
            put("semester", semester)
        }
        val rows = db.update(
            TABLE_GRADES, values,
            "student_id = ? AND subject_code = ? AND semester = ?",
            arrayOf(studentId, subjectCode, semester.toString())
        )
        if (rows > 0) return true
        return db.insert(TABLE_GRADES, null, values) != -1L
    }

    fun getStudentGrades(studentId: String, semester: Int): List<StudentGrade> {
        val list = mutableListOf<StudentGrade>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT g.subject_code, s.subject_name, g.midterm, g.final
            FROM $TABLE_GRADES g
            JOIN $TABLE_SUBJECTS s ON s.code = g.subject_code
            WHERE g.student_id = ? AND g.semester = ?
            ORDER BY g.subject_code
            """.trimIndent(),
            arrayOf(studentId, semester.toString())
        )
        while (cursor.moveToNext()) {
            list.add(StudentGrade(cursor.getString(0), cursor.getString(1),
                cursor.getDouble(2), cursor.getDouble(3)))
        }
        cursor.close()
        return list
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GRADES")
        db.execSQL("DROP TABLE IF EXISTS student_subjects") // galing sa lumang version
        db.execSQL("DROP TABLE IF EXISTS $STUDENT_SCHEDULE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLASS_SCHEDULE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUBJECTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEACHERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }
}