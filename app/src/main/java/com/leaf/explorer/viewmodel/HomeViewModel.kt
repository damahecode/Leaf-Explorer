package com.leaf.explorer.viewmodel

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genonbeta.android.framework.io.DocumentFile
import com.leaf.explorer.data.ExplorerRepository
import com.leaf.explorer.data.SelectionRepository
import com.leaf.explorer.database.model.FavFolder
import com.leaf.explorer.fragment.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject internal constructor(
    @ApplicationContext context: Context,
    private val selectionRepository: SelectionRepository,
    private val explorerRepo: ExplorerRepository,
    private val sharingRepository: SharingRepository,
) : ViewModel() {
    private val context = WeakReference(context)

    var currentPath: DocumentFile? = null

    val externalState = MutableLiveData<Unit>()

    val selectionState = selectionRepository.selectionState

    override fun onCleared() {
        super.onCleared()
        selectionRepository.clearSelections()
    }

    fun setSelected(obj: Any, selected: Boolean) {
        selectionRepository.setSelected(obj, selected)
    }

    val favFolderList = explorerRepo.getFavFolders()
    fun actionFavFolder(uri: Uri, remove: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = context.get() ?: return@launch

                val document = DocumentFile.fromUri(context, uri, true)
                val favFolder = FavFolder(uri, document.getName())

                try {
                    if (remove) {
                        explorerRepo.removeFavFolder(favFolder)
                    } else {
                        explorerRepo.insertFavFolder(favFolder)
                    }
                } catch (ignored: SQLiteConstraintException) {
                    // The selected path may already exist!
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareFileModel(list: List<Any>) {
        sharingRepository.clearSelections()
        sharingRepository.addAll(list)
    }
}