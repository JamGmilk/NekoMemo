package mirujam.nekomemo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from v1.2 (DB version 2) to v1.3 (DB version 3).
 *
 * Changes:
 * - questions.correctIndex (INTEGER) → correctIndices (TEXT, JSON array)
 * - questions.type (INTEGER) added, inferred from correctIndices
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create temp table with new schema
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `questions_temp` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `questionBankId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `options` TEXT NOT NULL,
                `correctIndices` TEXT NOT NULL,
                `type` INTEGER NOT NULL,
                FOREIGN KEY(`questionBankId`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        // Migrate data: convert single correctIndex to JSON array and infer type
        val cursor = db.query("SELECT `id`, `questionBankId`, `text`, `options`, `correctIndex` FROM `questions`")
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val questionBankId = cursor.getLong(1)
            val text = cursor.getString(2)
            val options = cursor.getString(3)
            val correctIndex = cursor.getInt(4)

            // Convert single int to JSON array, e.g. 2 → "[2]"
            val correctIndicesJson = org.json.JSONArray().put(correctIndex).toString()
            // Single correctIndex always maps to Single Choice (1)
            val typeCode = 1

            db.execSQL(
                "INSERT INTO `questions_temp` (`id`, `questionBankId`, `text`, `options`, `correctIndices`, `type`) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(id, questionBankId, text, options, correctIndicesJson, typeCode)
            )
        }
        cursor.close()

        db.execSQL("DROP TABLE IF EXISTS `questions`")
        db.execSQL("ALTER TABLE `questions_temp` RENAME TO `questions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_questionBankId` ON `questions` (`questionBankId`)")
    }
}
