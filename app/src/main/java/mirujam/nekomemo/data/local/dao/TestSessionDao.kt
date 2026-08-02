package mirujam.nekomemo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mirujam.nekomemo.data.local.entity.TestSessionEntity

@Dao
interface TestSessionDao {

    @Query("SELECT * FROM test_sessions WHERE bankId = :bankId")
    suspend fun getSession(bankId: Long): TestSessionEntity?

    @Query("SELECT * FROM test_sessions WHERE bankId = :bankId")
    fun getSessionFlow(bankId: Long): Flow<TestSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: TestSessionEntity)

    @Query("DELETE FROM test_sessions WHERE bankId = :bankId")
    suspend fun clearSession(bankId: Long)

    @Query("DELETE FROM test_sessions")
    suspend fun deleteAll()
}
