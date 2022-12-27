package com.leaf.explorer.viewmodel.content

import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.util.MimeIcons

class FileContentViewModel(fileModel: FileModel) {
    val name = fileModel.file.getName()

    val count = fileModel.indexCount

    val isDirectory = fileModel.file.isDirectory()

    val mimeType = fileModel.file.getType()

    val icon = if (isDirectory) R.drawable.ic_folder_white_24dp else MimeIcons.loadMimeIcon(mimeType)

    val indexCount = fileModel.indexCount

    val contentDetail by lazy {
        LeafFile.formatLength(fileModel.file.getLength(), false)
    }

    val uri = fileModel.file.getUri()
}