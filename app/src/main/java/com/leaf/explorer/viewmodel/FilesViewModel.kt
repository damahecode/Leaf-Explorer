/*
 * Copyright (C) 2022 Mahadev Code
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.leaf.explorer.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.leaf.explorer.R
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.data.FileRepository
import com.leaf.explorer.data.SelectionRepository
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.model.ListItem
import com.leaf.explorer.model.TitleSectionContentModel
import com.leaf.explorer.util.PreferenceUtil
import java.io.File
import java.lang.ref.WeakReference
import java.text.Collator
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject internal constructor(
    @ApplicationContext context: Context,
    private val fileRepository: FileRepository,
    private val selectionRepository: SelectionRepository,
) : ViewModel() {

    private val context = WeakReference(context)

    private val textFolder = context.getString(R.string.folder)

    private val textFile = context.getString(R.string.file)

    private val _files = MutableLiveData<List<ListItem>>()

    val getHomePath: DocumentFile
        get() = selectionRepository.homePath ?: DocumentFile.fromFile(Environment.getExternalStorageDirectory())

    val files = Transformations.map(
        liveData {
            requestPath(getHomePath)
            emitSource(_files)
        }
    ) {
        selectionRepository.whenContains(it) { item, selected ->
            if (item is FileModel) item.isSelected = selected
        }
        it
    }

    val isCustomStorageFolder: Boolean
        get() = Uri.fromFile(fileRepository.defaultAppDirectory) != fileRepository.appDirectory.getUri()

    private val _path = MutableLiveData<FileModel>()

    val path = liveData {
        emitSource(_path)
    }

    private val _pathTree = MutableLiveData<List<FileModel>>()

    val pathTree = liveData {
        emitSource(_pathTree)
    }

    private fun showHidden() : Boolean {
        val mContext = context.get() ?: return false

        val prefs = PreferenceUtil.getPreferences(mContext)
        return prefs.getBoolean(Keyword.SHOW_HIDDEN, false)
    }

    private fun createOrderedFileList(file: DocumentFile): List<ListItem> {
        val pathTree = mutableListOf<FileModel>()

        var pathChild = file
        var breakWhile = false
        do {
            pathTree.add(FileModel(pathChild))

            LeafFile.availableStorage().forEach { docFile ->
                if (pathChild.getUri().path == docFile.getUri().path) {
                    pathTree.add(FileModel(DocumentFile.fromFile(File(Keyword.FILE_BROWSER_HOME))))
                    breakWhile = true
                }
            }
            if (breakWhile)
                break
        } while (pathChild.parent?.also { pathChild = it } != null)

        pathTree.reverse()
        _pathTree.postValue(pathTree)

        val list = fileRepository.getFileList(file)

        if (list.isEmpty()) return list

        val collator = Collator.getInstance()
        collator.strength = Collator.TERTIARY

        val sortedList = list.sortedWith(compareBy(collator) {
            it.file.getName()
        })

        val contents = ArrayList<ListItem>(0)
        val files = ArrayList<FileModel>(0)

        sortedList.forEach {
            if (showHidden()) {
                if (it.file.isDirectory()) contents.add(it)
                else if (it.file.isFile()) files.add(it)
            } else {
                if (!it.file.getName().startsWith(".")) {
                    if (it.file.isDirectory()) contents.add(it)
                    else if (it.file.isFile()) files.add(it)
                }
            }
        }

        if (contents.isNotEmpty()) {
            contents.add(0, TitleSectionContentModel(textFolder))
        }

        if (files.isNotEmpty()) {
            contents.add(TitleSectionContentModel(textFile))
            contents.addAll(files)
        }

        return contents
    }

    fun goUp(): Boolean {
        val paths = pathTree.value ?: return false

        if (paths.size < 2) {
            return false
        }

        val iterator = paths.asReversed().listIterator()
        if (iterator.hasNext()) {
            iterator.next() // skip the first one that is already visible
            do {
                val next = iterator.next()
                if (next.file.canRead()) {
                    requestPath(next.file)
                    return true
                }
            } while (iterator.hasNext())
        }

        return false
    }

    fun requestDefaultAppFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            context.get()?.let {
                requestPathInternal(DocumentFile.fromFile(fileRepository.defaultAppDirectory))
            }
        }
    }

    fun requestAppFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            context.get()?.let {
                requestPathInternal(fileRepository.appDirectory)
            }
        }
    }

    fun requestPath(file: DocumentFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                requestPathInternal(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestPathInternal(file: DocumentFile) {
        _path.postValue(FileModel(file))
        _files.postValue(createOrderedFileList(file))
    }
}
