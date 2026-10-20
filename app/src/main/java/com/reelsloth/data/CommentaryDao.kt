package com.reelsloth.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(commentary: CommentaryEntity)

    @Query("SELECT * FROM commentary WHERE period_key = :periodKey")
    suspend fun get(periodKey: String): CommentaryEntity?

    @Query("SELECT * FROM commentary WHERE period_key = :periodKey")
    fun observe(periodKey: String): Flow<CommentaryEntity?>

    @Query("DELETE FROM commentary")
    suspend fun deleteAll()
}