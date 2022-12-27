package com.leaf.explorer.util

import android.R.attr
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import com.genonbeta.android.framework.app.Storage
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.model.FileModel
import java.io.File
import java.util.*


object FileUtils {

    fun getPathName(context: Context, docFile: DocumentFile): String {
        return if (docFile.getUri().path == LeafFile.EXTERNAL_PUBLIC_DIRECTORY) {
            context.getString(R.string.internal_storage)
        } else {
            docFile.getName()
        }
    }

    fun getFileModel(any: Any): FileModel? {
        return try {
            if (any is FileModel) any else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }



    fun getTotalMemorySize(uri: Uri): String {
        return try {
            val stat = StatFs(uri.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            LeafFile.formatLength(totalBlocks * blockSize, true)
        } catch (e: Exception) {
            ""
        }
    }

    fun getFreeMemorySize(uri: Uri): String {
        return try {
            val stat = StatFs(uri.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            LeafFile.formatLength(availableBlocks * blockSize, true)
        } catch (e: Exception) {
            ""
        }
    }

    fun getUsedMemorySize(uri: Uri): String {
        return try {
            val stat = StatFs(uri.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalSize = totalBlocks * blockSize
            val availableSize = availableBlocks * blockSize
            LeafFile.formatLength(totalSize - availableSize, true)
        } catch (e: Exception) {
            ""
        }
    }

    fun getUsedMemoryPercentage(uri: Uri): Int {
        return try {
            val stat = StatFs(uri.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalSize = totalBlocks * blockSize
            val availableSize = availableBlocks * blockSize
            // Log.d("here is", "" + availableSize * 100 / totalSize)
            val size = (availableSize * 100 / totalSize).toInt()
            100 - size
        } catch (e: Exception) {
            0
        }
    }

    private val fileModelList = ArrayList<DocumentFile>()
    fun shareIntentAll(context: Context, selectedItemList: MutableList<Any>) {
        fileModelList.clear()
        for (list in selectedItemList) {
            getShareUri(context, list)
        }
        try {
            shareIntent(context, fileModelList)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.unknown_failure, Toast.LENGTH_LONG).show()
        }

    }

    private fun shareIntent(context: Context, selectedItemList: MutableList<DocumentFile>) {
        if (selectedItemList.size > 0) {
            val shareIntent = Intent()
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.action = if (selectedItemList.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND

            if (selectedItemList.size > 1) {
                val uriList = ArrayList<Uri>()
                val mimeGrouper = Utils.MIMEGrouper()

                for (sharedItem in selectedItemList) {
                    uriList.add(sharedItem.getSecureUri(context, context.getString(R.string.file_provider)))
                    if (!mimeGrouper.isLocked) mimeGrouper.process(sharedItem.getType())
                }
                shareIntent.type = mimeGrouper.toString()
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            } else if (selectedItemList.size == 1) {
                val sharedItem  = selectedItemList[0]
                shareIntent.type = sharedItem.getType()
                shareIntent.putExtra(Intent.EXTRA_STREAM, sharedItem.getSecureUri(context, context.getString(R.string.file_provider)))
            }
            try {
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.text_fileShareAppChoose)
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                Toast.makeText(context, R.string.unknown_failure, Toast.LENGTH_SHORT).show()
            }

        } else {
            Toast.makeText(context, R.string.unknown_failure, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getShareUri(context: Context, any: Any) {
        if (any is FileModel) {
            if (any.file.isDirectory()) {
                val listFile = any.file.listFiles(context)
                for (file in listFile) {
                    getShareUri(context, file)
                }
            } else{
                fileModelList.add(any.file)
            }
        }

    }

    // SAF Operations
    fun verifyFile(context: Context, docFile: DocumentFile): DocumentFile? {
        if (!LeafFile.isSAFRequired(docFile))
            return docFile

        val safPrefUri = getStorageOf(context, docFile)?.let { PreferenceUtil.getSafUriString(it.getName()) } ?: return null
        if (LeafFile.isSAFAccessGranted(context, safPrefUri)) {
            val safFile = LeafFile.findSafFile(context, docFile.getUri(), safPrefUri)

            if (safFile != null)
                try {
                    return safFile
                } catch (ignored: Exception) { }
        } else {
            return null
        }

        return null
    }

    fun getStorageOf(context: Context, docFile: DocumentFile?): DocumentFile? {
        if (docFile == null || "file" != docFile.getUri().scheme) return null
        val path = docFile.getUri().path
        if (path.isNullOrEmpty()) return null
        val storages = LeafFile.availableStorage()
        for (storage in storages) {
            val storagePath = storage.getUri().path
            if (storagePath.isNullOrEmpty()) return null
            if (path.startsWith(storagePath))
                return storage
        }
        return null
    }

    @JvmStatic
    fun delete(context: Context, uri: Uri, storage: Storage): Boolean {
        val file = verifyFile(context, DocumentFile.fromUri(context, uri))
        val newUri = file?.getUri() ?: uri

        return storage.delete(newUri)
    }

    fun alertMountStorage(context: Context, layoutInflater: LayoutInflater) {
        val view = layoutInflater.inflate(R.layout.layout_mount_storage, null, false)

        AlertDialog.Builder(context)
            .setTitle(R.string.mount_dialog)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mount, null)
            .setCancelable(false)
            .create().also { dialog ->
                dialog.setOnShowListener {

                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        context.sendBroadcast(Intent(Keyword.HOME_FRAGMENT_STORAGE_MOUNT))
                        dialog.dismiss()
                    }

                }
            }
            .show()
    }
}