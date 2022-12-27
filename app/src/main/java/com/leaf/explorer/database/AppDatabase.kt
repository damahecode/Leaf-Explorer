package com.leaf.explorer.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.leaf.explorer.database.model.*

@Database(
    entities = [
        FavFolder::class,
        WebTransfer::class,
    ],
    views = [],
    version = 1
)
@TypeConverters(
    UriTypeConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favFolderDao(): FavFolderDao

    abstract fun webTransferDao(): WebTransferDao
}