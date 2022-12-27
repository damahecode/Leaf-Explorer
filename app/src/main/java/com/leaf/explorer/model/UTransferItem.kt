package com.leaf.explorer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UTransferItem(
    var id: Long,
    var groupId: Long,
    var name: String,
    var mimeType: String,
    var size: Long,
    var directory: String?,
    var location: String,
    var dateCreated: Long = System.currentTimeMillis(),
    var dateModified: Long = dateCreated,
) : Parcelable