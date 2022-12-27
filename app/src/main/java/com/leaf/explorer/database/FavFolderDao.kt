package com.leaf.explorer.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.leaf.explorer.database.model.FavFolder

@Dao
interface FavFolderDao {
    @Query("SELECT * FROM favFolder ORDER BY name ASC")
    fun getAll(): LiveData<List<FavFolder>>

    @Insert
    suspend fun insert(folder: FavFolder)

    @Query("DELETE FROM favFolder")
    suspend fun removeAll()

    @Delete
    suspend fun remove(folder: FavFolder)
}