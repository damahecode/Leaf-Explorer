package com.leaf.explorer.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Storage(
    val name: String,
    val total: String,
    val used: String,
    val free: String,
    val percentage: Int,
    val storageUri: Uri,
) : Parcelable {

    override fun equals(other: Any?): Boolean {
        return other is Storage && storageUri == other.storageUri
    }
}

@Parcelize
data class Category(
    val name: String,
    val icon: Int,
) : Parcelable {

    override fun equals(other: Any?): Boolean {
        return other is Category && name == other.name
    }
}