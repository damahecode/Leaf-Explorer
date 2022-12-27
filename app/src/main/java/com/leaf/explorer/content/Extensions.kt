package com.leaf.explorer.content

import android.media.MediaScannerConnection
import android.net.Uri
import com.genonbeta.android.framework.io.DocumentFile

/**
 * @See android.content.ContentUris#removeId
 */
fun Uri.removeId(): Uri {
    // Verify that we have a valid ID to actually remove
    val last: String? = lastPathSegment
    last?.toLong() ?: throw IllegalArgumentException("No path segments to remove")

    val segments: List<String> = pathSegments
    val builder: Uri.Builder = buildUpon()
    builder.path(null)
    for (i in 0 until segments.size - 1) {
        builder.appendPath(segments[i])
    }
    return builder.build()
}

fun MediaScannerConnection.scan(documentFile: DocumentFile) {
    val path = documentFile.filePath
    if (path != null && isConnected) {
        scanFile(path, documentFile.getType())
    }
}