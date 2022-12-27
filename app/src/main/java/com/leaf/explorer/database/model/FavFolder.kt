package com.leaf.explorer.database.model

import android.net.Uri
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "favFolder"
)
data class FavFolder(
    @PrimaryKey
    val uri: Uri,
    val name: String,
) : Parcelable