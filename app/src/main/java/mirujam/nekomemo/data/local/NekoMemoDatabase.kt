package mirujam.nekomemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import mirujam.nekomemo.data.local.dao.CategoryDao
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.dao.QuestionDao
import mirujam.nekomemo.data.local.dao.QuestionStatsDao
import mirujam.nekomemo.data.local.dao.TestSessionDao
import mirujam.nekomemo.data.local.entity.CategoryEntity
import mirujam.nekomemo.data.local.entity.QuestionBankEntity
import mirujam.nekomemo.data.local.entity.QuestionEntity
import mirujam.nekomemo.data.local.entity.QuestionStatsEntity
import mirujam.nekomemo.data.local.entity.TestSessionEntity

@Database(
    entities = [
        QuestionBankEntity::class,
        QuestionEntity::class,
        CategoryEntity::class,
        QuestionStatsEntity::class,
        TestSessionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NekoMemoDatabase : RoomDatabase() {
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun questionDao(): QuestionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun questionStatsDao(): QuestionStatsDao
    abstract fun testSessionDao(): TestSessionDao
}
