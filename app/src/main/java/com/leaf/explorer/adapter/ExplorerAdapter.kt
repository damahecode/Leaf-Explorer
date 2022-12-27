package com.leaf.explorer.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.adapter.custom.EditableListAdapter
import com.leaf.explorer.database.model.FavFolder
import com.leaf.explorer.databinding.ListCategoryBinding
import com.leaf.explorer.databinding.ListFavoriteFolderBinding
import com.leaf.explorer.databinding.ListSectionTitleBinding
import com.leaf.explorer.databinding.ListStorageBinding
import com.leaf.explorer.model.*
import com.leaf.explorer.viewholder.TitleSectionViewHolder

class ExplorerAdapter(
    context: Context,
    private val clickListener: (Any, ClickType) -> Unit,
) : EditableListAdapter<Any, EditableListAdapter.EditableViewHolder>(context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditableViewHolder = when (viewType) {
        VIEW_TYPE_SECTION -> TitleSectionViewHolder(
            ListSectionTitleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        VIEW_TYPE_STORAGE -> StorageViewHolder(
            ListStorageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            clickListener
        )
        VIEW_TYPE_CATEGORY -> CategoryViewHolder(
            ListCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            clickListener
        )
        VIEW_TYPE_FAVORITE -> FavoriteFolderViewHolder(
            ListFavoriteFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            clickListener
        )
        else -> throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: EditableViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TitleSectionContentModel -> if (holder is TitleSectionViewHolder) holder.bind(item)
            is Storage -> if (holder is StorageViewHolder) holder.bind(item)
            is Category -> if (holder is CategoryViewHolder) holder.bind(item)
            is FavFolder -> if (holder is FavoriteFolderViewHolder) holder.bind(item)
            else -> throw IllegalStateException()
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is TitleSectionContentModel -> VIEW_TYPE_SECTION
        is Storage -> VIEW_TYPE_STORAGE
        is Category -> VIEW_TYPE_CATEGORY
        is FavFolder -> VIEW_TYPE_FAVORITE
        else -> throw IllegalStateException()
    }

    enum class ClickType {
        Default,
        FavoriteRemove
    }

    companion object {
        const val VIEW_TYPE_SECTION = 1

        const val VIEW_TYPE_STORAGE = 2

        const val VIEW_TYPE_CATEGORY = 3

        const val VIEW_TYPE_FAVORITE = 4
    }
}

class StorageViewHolder(
    private val binding: ListStorageBinding,
    private val clickListener: (Storage, ExplorerAdapter.ClickType) -> Unit,
) : EditableListAdapter.EditableViewHolder(binding.root) {
    fun bind(storage: Storage) {
        binding.viewModel = StorageContentViewModel(storage)
        binding.root.setOnClickListener {
            clickListener(storage, ExplorerAdapter.ClickType.Default)
        }
        binding.executePendingBindings()
    }
}

class CategoryViewHolder(
    private val binding: ListCategoryBinding,
    private val clickListener: (Category, ExplorerAdapter.ClickType) -> Unit,
) : EditableListAdapter.EditableViewHolder(binding.root) {
    fun bind(category: Category) {
        binding.viewModel = CategoryContentViewModel(category)
        binding.root.setOnClickListener {
            clickListener(category, ExplorerAdapter.ClickType.Default)
        }
        binding.executePendingBindings()
    }
}

class FavoriteFolderViewHolder(
    private val binding: ListFavoriteFolderBinding,
    private val clickListener: (FavFolder, ExplorerAdapter.ClickType) -> Unit,
) : EditableListAdapter.EditableViewHolder(binding.root) {
    fun bind(favoriteFolder: FavFolder) {
        binding.viewModel = FavoriteFolderContentViewModel(favoriteFolder)
        binding.root.setOnClickListener {
            clickListener(favoriteFolder, ExplorerAdapter.ClickType.Default)
        }
        binding.removeButton.setOnClickListener {
            clickListener(favoriteFolder, ExplorerAdapter.ClickType.FavoriteRemove)
        }
        binding.executePendingBindings()
    }
}

class StorageContentViewModel(storage: Storage) {
    val name = storage.name
    val total = " Total -" + storage.total
    val used = storage.used + " -Used"
    val free = storage.free + " -Free"
    val percentage = storage.percentage
    val usedPercentage = storage.percentage.toString() + "%"
    val path = storage.storageUri.path
    val icon = if (storage.storageUri.path == "/storage/emulated/0")
        R.drawable.ic_android_white_24dp
    else
        R.drawable.ic_save_white_24dp
}

class CategoryContentViewModel(category: Category) {
    val name = category.name

    val icon = category.icon
}

class FavoriteFolderContentViewModel(favoriteFolder: FavFolder) {
    val name = getName(favoriteFolder.name, favoriteFolder.uri)
    val info = LeafFile.getStandardPath(favoriteFolder.uri).path
    val icon = R.drawable.ic_folder_white_24dp

    private fun getName(name: String, uri: Uri): String {
        return if (LeafFile.schemeContent(uri)) {
            "$name (saf)"
        } else
            name
    }
}