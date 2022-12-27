package com.leaf.explorer.database.model

import android.net.Uri
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.leaf.explorer.model.ListItem
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "webTransfer"
)
data class WebTransfer(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateCreated: Long = System.currentTimeMillis(),
) : Parcelable, ListItem {
    override val listId: Long
        get() = uri.hashCode().toLong() + javaClass.hashCode()
}