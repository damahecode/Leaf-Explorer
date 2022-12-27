package com.leaf.explorer.viewmodel

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.*
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.leaf.explorer.R
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.data.ExplorerRepository
import com.leaf.explorer.data.SelectionRepository
import com.leaf.explorer.database.model.FavFolder
import com.leaf.explorer.model.Category
import com.leaf.explorer.model.Storage
import com.leaf.explorer.model.TitleSectionContentModel
import com.leaf.explorer.util.PreferenceUtil
import java.lang.ref.WeakReference
import javax.inject.Inject

@HiltViewModel
class ExplorerViewModel @Inject internal constructor(
    @ApplicationContext context: Context,
    private val explorerRepo: ExplorerRepository,
    private val selectionRepository: SelectionRepository,
) : ViewModel() {
    private val context = WeakReference(context)
    private val _homeList = MutableLiveData<List<Any>>()

    val favFolderList = explorerRepo.getFavFolders()

    fun clearFavList() {
        viewModelScope.launch(Dispatchers.IO) {
            explorerRepo.clearFavList()
        }
    }

    val getHomeList = liveData {
        val merger = MediatorLiveData<List<Any>>()
        _homeList.postValue(explorerRepo.explorerItems())
        val favList = explorerRepo.getFavFolders()

        val observer = Observer<List<Any>> {
            val mergedList = mutableListOf<Any>().apply {
                _homeList.value?.let { addAll(it) }
                favList.value?.let { addAll(it) }
            }

            val sortedList = mergedList.sortedWith { o1, o2 ->
                getItemOrder(o1) - getItemOrder(o2)
            }.toMutableList()

            val copyList = ArrayList(sortedList)

            var previous: Any? = null
            var increase = 0
            copyList.forEachIndexed { index, any ->
                if (any.javaClass != previous?.javaClass) {
                    val titleRes = when (any) {
                        is Storage -> R.string.text_storage
                        is Category -> R.string.categories
                        is FavFolder -> R.string.favorites_folders
                        else -> throw IllegalStateException()
                    }
                    sortedList.add(index + increase, TitleSectionContentModel(context.getString(titleRes)))
                    increase += 1
                }

                previous = any
            }

            merger.value = sortedList
        }

        merger.addSource(_homeList, observer)
        merger.addSource(favList, observer)

        emitSource(merger)
    }

    private fun getItemOrder(any: Any): Int {
        return when (any) {
            is Storage -> 0
            is Category -> 1
            is FavFolder -> 2
            else -> throw IllegalStateException()
        }
    }

    fun setHomePath(path: DocumentFile) {
        selectionRepository.homePath = path
    }

    fun addSafFav(uri: Uri) {
        context.get()?.let {
            it.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            actionFavFolder(uri)
        }
    }

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

    val preLoadFavorites =  PreferenceUtil.getPreferences(context).getBoolean(Keyword.PRE_LOAD_FAVORITES, true)

    val addPreFav: ArrayList<DocumentFile> by lazy {
        val favList = ArrayList<DocumentFile>()

        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_DCIM))
        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_PICTURES))
        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_MUSIC))
        favList.add(LeafFile.getDocumentFilePublicDirectory(Environment.DIRECTORY_MOVIES))

        PreferenceUtil.editBooleanPreferences(context, Keyword.PRE_LOAD_FAVORITES, false)

        favList
    }
}