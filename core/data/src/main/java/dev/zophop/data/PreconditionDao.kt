package dev.zophop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreconditionDao {
    @Query("SELECT * FROM preconditions ORDER BY updatedAt DESC")
    fun getAllPreconditionsFlow(): Flow<List<PreconditionEntity>>

    @Query("SELECT * FROM preconditions")
    suspend fun getAllPreconditions(): List<PreconditionEntity>

    @Query("SELECT * FROM preconditions WHERE id = :id")
    suspend fun getPreconditionById(id: String): PreconditionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePrecondition(precondition: PreconditionEntity)

    @Query("DELETE FROM preconditions WHERE id = :id")
    suspend fun deletePreconditionById(id: String)
}
