package com.leaf.explorer.data

import android.content.Context
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.leaf.explorer.database.FavFolderDao
import com.leaf.explorer.database.model.FavFolder
import com.leaf.explorer.model.Category
import com.leaf.explorer.model.Storage
import com.leaf.explorer.util.FileUtils
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favFolderDao: FavFolderDao,
) {
    private val contextWeak = WeakReference(context)

    fun explorerItems(): List<Any> {
        val context = contextWeak.get() ?: return emptyList()
        val items = ArrayList<Any>()

        //StoragePath
        for (storage in LeafFile.availableStorage()) {
            items.add(Storage(
                FileUtils.getPathName(context, storage),
                FileUtils.getTotalMemorySize(storage.getUri()),
                FileUtils.getUsedMemorySize(storage.getUri()),
                FileUtils.getFreeMemorySize(storage.getUri()),
                FileUtils.getUsedMemoryPercentage(storage.getUri()),
                storage.getUri()
            ))
        }

        //Categories
        items.add(Category(context.getString(R.string.transfers), R.drawable.ic_trebleshot))
        items.add(Category(context.getString(R.string.player), R.drawable.ic_music_note_white_24dp))

        return items
    }

    fun getFavFolders() = favFolderDao.getAll()
    suspend fun insertFavFolder(favFolder: FavFolder) = favFolderDao.insert(favFolder)
    suspend fun removeFavFolder(favFolder: FavFolder) = favFolderDao.remove(favFolder)
    suspend fun clearFavList() = favFolderDao.removeAll()
}