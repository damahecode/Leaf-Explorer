package com.leaf.explorer.model

import android.os.Parcelable
import com.genonbeta.android.framework.io.DocumentFile
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileModel(
    val file: DocumentFile,
    val indexCount: Int = 0
) : Parcelable, ListItem {
    @IgnoredOnParcel
    var isSelected = false

    override val listId: Long
        get() = file.getUri().hashCode().toLong() + javaClass.hashCode()
}