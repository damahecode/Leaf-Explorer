package com.leaf.explorer.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.model.FileModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contextWeak = WeakReference(context)

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    var appDirectory: DocumentFile
        get() {
            val context = contextWeak.get()!!
            val defaultPath = defaultAppDirectory
            val preferredPath = preferences.getString(KEY_STORAGE_PATH, null)

            if (preferredPath != null) {
                try {
                    val storageFolder = DocumentFile.fromUri(context, Uri.parse(preferredPath))
                    if (storageFolder.isDirectory() && storageFolder.canWrite()) return storageFolder
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (defaultPath.isFile) defaultPath.delete()
            if (!defaultPath.isDirectory) defaultPath.mkdirs()
            return DocumentFile.fromFile(defaultPath)
        }
        set(value) {
            preferences.edit {
                putString(KEY_STORAGE_PATH, value.getUri().toString())
            }
        }

    val defaultAppDirectory: File by lazy {

        var primaryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!primaryDir.isDirectory && !primaryDir.mkdirs() || !primaryDir.canWrite()) {
            primaryDir = Environment.getExternalStorageDirectory()
        }

        File(primaryDir.toString() + File.separator + context.getString(R.string.app_name))
    }

    fun getFileList(file: DocumentFile): List<FileModel> {
        check(file.isDirectory()) {
            "${file.originalUri} is not a directory."
        }

        return file.listFiles(context).map {
            FileModel(it, it.takeIf { it.isDirectory() }?.listFiles(context)?.size ?: 0)
        }
    }

    companion object {
        const val KEY_STORAGE_PATH = "storage_path"
    }
}