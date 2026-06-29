package mirujam.nekomemo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from v1.0 (DB version 1) to v1.2 (DB version 2).
 *
 * Changes:
 * - Adds a new `categories` table (`id`, `name`) with a UNIQUE index on `name`,
 *   seeded with a default "GENERAL" category.
 * - `question_banks.category` (TEXT) is replaced by `categoryId` (INTEGER) with a
 *   FOREIGN KEY to `categories(id)` ON DELETE RESTRICT.
 * - Each distinct `category` text value from the old `question_banks` is migrated
 *   into `categories`, and every `question_banks` row is remapped to its new
 *   `categoryId` (falling back to "GENERAL" if no match is found).
 * - `question_banks` gains indices on `createdAt` and `categoryId`.
 * - `questions.version` column is removed; the table is rebuilt without it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new `categories` table with a UNIQUE index on `name`.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

        // 2. Seed a default "GENERAL" category so a valid fallback always exists.
        db.execSQL("INSERT OR IGNORE INTO `categories` (`name`) VALUES ('GENERAL')")

        // 3. Migrate every distinct `category` text value from `question_banks` into `categories`.
        val distinctCategories = db.query("SELECT DISTINCT `category` FROM `question_banks`")
        while (distinctCategories.moveToNext()) {
            val category = distinctCategories.getString(0)
            db.execSQL(
                "INSERT OR IGNORE INTO `categories` (`name`) VALUES (?)",
                arrayOf<Any?>(category)
            )
        }
        distinctCategories.close()

        // 4. Create the new `question_banks_temp` table with the v2 schema
        //    (categoryId replaces the old category TEXT; FK ON DELETE RESTRICT).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `question_banks_temp` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        // Pre-fetch the default "GENERAL" category id to use as a fallback when
        // remapping rows (every category was inserted above, so this is just a safety net).
        val generalCursor = db.query("SELECT `id` FROM `categories` WHERE `name` = 'GENERAL'")
        val generalCategoryId = if (generalCursor.moveToFirst()) generalCursor.getLong(0) else 0L
        generalCursor.close()

        // 5. Copy `question_banks` rows into the temp table, mapping the old
        //    `category` TEXT to the new `categoryId` via a lookup in `categories`.
        val banksCursor = db.query("SELECT `id`, `title`, `category`, `createdAt` FROM `question_banks`")
        while (banksCursor.moveToNext()) {
            val id = banksCursor.getLong(0)
            val title = banksCursor.getString(1)
            val category = banksCursor.getString(2)
            val createdAt = banksCursor.getLong(3)

            val categoryCursor = db.query(
                "SELECT `id` FROM `categories` WHERE `name` = ?",
                arrayOf<Any?>(category)
            )
            val categoryId = if (categoryCursor.moveToFirst()) categoryCursor.getLong(0) else generalCategoryId
            categoryCursor.close()

            db.execSQL(
                "INSERT INTO `question_banks_temp` (`id`, `title`, `categoryId`, `createdAt`) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(id, title, categoryId, createdAt)
            )
        }
        banksCursor.close()

        // 6. Drop the old `question_banks` and rename the temp table into place.
        db.execSQL("DROP TABLE IF EXISTS `question_banks`")
        db.execSQL("ALTER TABLE `question_banks_temp` RENAME TO `question_banks`")

        // 7. Create the v2 indices on `question_banks`.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_banks_createdAt` ON `question_banks` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_banks_categoryId` ON `question_banks` (`categoryId`)")

        // 8. Create the new `questions_temp` table WITHOUT the `version` column.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `questions_temp` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `questionBankId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `options` TEXT NOT NULL,
                `correctIndex` INTEGER NOT NULL,
                FOREIGN KEY(`questionBankId`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        // 9. Copy `questions` rows into the temp table, dropping the `version` column.
        val questionsCursor = db.query("SELECT `id`, `questionBankId`, `text`, `options`, `correctIndex` FROM `questions`")
        while (questionsCursor.moveToNext()) {
            val id = questionsCursor.getLong(0)
            val questionBankId = questionsCursor.getLong(1)
            val text = questionsCursor.getString(2)
            val options = questionsCursor.getString(3)
            val correctIndex = questionsCursor.getInt(4)

            db.execSQL(
                "INSERT INTO `questions_temp` (`id`, `questionBankId`, `text`, `options`, `correctIndex`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(id, questionBankId, text, options, correctIndex)
            )
        }
        questionsCursor.close()

        // 10. Drop the old `questions` and rename the temp table into place.
        db.execSQL("DROP TABLE IF EXISTS `questions`")
        db.execSQL("ALTER TABLE `questions_temp` RENAME TO `questions`")

        // 11. Recreate the index on `questions.questionBankId`.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_questionBankId` ON `questions` (`questionBankId`)")
    }
}

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
