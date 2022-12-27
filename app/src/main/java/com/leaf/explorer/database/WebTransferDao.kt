package com.leaf.explorer.database

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.leaf.explorer.database.model.WebTransfer

@Dao
interface WebTransferDao {
    @Query("SELECT * FROM webTransfer WHERE id = :id")
    fun get(id: Int): LiveData<WebTransfer>

    @Query("SELECT * FROM webTransfer WHERE uri = :uri")
    suspend fun get(uri: Uri): WebTransfer?

    @Query("SELECT * FROM webTransfer ORDER BY dateCreated DESC")
    fun getAll(): LiveData<List<WebTransfer>>

    @Insert
    suspend fun insert(webTransfer: WebTransfer)

    @Delete
    suspend fun remove(webTransfer: WebTransfer)
}