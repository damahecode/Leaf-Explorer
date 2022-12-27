package com.leaf.explorer.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.adapter.FileAdapter
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.databinding.LayoutEmptyContentBinding
import com.leaf.explorer.databinding.ListPathBinding
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.util.Activities
import com.leaf.explorer.util.FileUtils
import com.leaf.explorer.viewmodel.EmptyContentViewModel
import com.leaf.explorer.viewmodel.FilesViewModel
import com.leaf.explorer.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class FileBrowserFragment : Fragment(R.layout.layout_file_browser_fragment) {

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        private var afterPopup = false

        override fun handleOnBackPressed() {
            if (viewModel.goUp()) {
                afterPopup = false
            } else if (afterPopup) {
                afterPopup = false
                sendBroadcast(Keyword.FILE_BROWSER_HOME)
            } else {
                afterPopup = true
                pathPopupMenu()
                pathsPopupMenu.show()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val a = intent.action ?: return
            if (a == Keyword.FILE_BROWSER_UPDATE)
                homeViewModel.externalState.value = Unit
            if (a == Keyword.FILE_BROWSER_REFRESH)
                homeViewModel.currentPath?.let { viewModel.requestPath(it) }
            if (a == Keyword.FILE_BROWSER_PATH) {
                viewModel.requestPath(viewModel.getHomePath)
            }
        }
    }

    private val viewModel: FilesViewModel by viewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private lateinit var pathsPopupMenu: PopupMenu
    private val filter = IntentFilter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filter.addAction(Keyword.FILE_BROWSER_UPDATE)
        filter.addAction(Keyword.FILE_BROWSER_REFRESH)
        filter.addAction(Keyword.FILE_BROWSER_PATH)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = LayoutEmptyContentBinding.bind(view.findViewById(R.id.emptyView))

        val adapter = FileAdapter(requireContext()) { fileModel, clickType ->
            when (clickType) {
                FileAdapter.ClickType.Default -> {
                    if (fileModel.file.isDirectory()) {
                        viewModel.requestPath(fileModel.file)
                    } else {
                        Activities.view(view.context, fileModel.file)
                    }
                }
                FileAdapter.ClickType.ToggleSelect -> {
                    homeViewModel.setSelected(fileModel, fileModel.isSelected)
                }
            }
        }

        val emptyContentViewModel = EmptyContentViewModel()

        val pathRecyclerView = view.findViewById<RecyclerView>(R.id.pathRecyclerView)
        val pathSelectorButton = view.findViewById<View>(R.id.pathSelectorButton)
        val pathAdapter = PathAdapter {
            if (it.file.getUri() == Uri.fromFile(File(Keyword.FILE_BROWSER_HOME)))
                sendBroadcast(Keyword.FILE_BROWSER_HOME)
            else
                viewModel.requestPath(it.file)
        }

        pathsPopupMenu = PopupMenu(requireContext(), pathSelectorButton).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
        }
        pathSelectorButton.setOnClickListener {
            pathPopupMenu()
            pathsPopupMenu.show()
        }

        emptyView.viewModel = emptyContentViewModel
        emptyView.emptyText.setText(R.string.empty_files_list)
        emptyView.emptyImage.setImageResource(R.drawable.ic_insert_drive_file_white_24dp)
        emptyView.executePendingBindings()

        recyclerView.adapter = adapter
        pathAdapter.setHasStableIds(true)
        pathRecyclerView.adapter = pathAdapter

        pathAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    pathRecyclerView.scrollToPosition(pathAdapter.itemCount - 1)
                }
            }
        )

        viewModel.files.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            emptyContentViewModel.with(recyclerView, it.isNotEmpty())
        }

        viewModel.pathTree.observe(viewLifecycleOwner) {
            pathAdapter.submitList(it)
        }

        viewModel.path.observe(viewLifecycleOwner) {
            homeViewModel.currentPath = it.file
        }

        homeViewModel.externalState.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun pathPopupMenu() {
        pathsPopupMenu.menu.clear()
        val availableStorage = LeafFile.availableStorage()

        pathsPopupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.explorer_home) {
                sendBroadcast(Keyword.FILE_BROWSER_HOME)
            } else if (menuItem.itemId == R.id.default_app_folder) {
                viewModel.requestDefaultAppFolder()
            } else if (menuItem.itemId == R.id.app_folder) {
                viewModel.requestAppFolder()
            } else if (menuItem.groupId == R.id.locations_storage) {
                val newFile = FileUtils.verifyFile(requireContext(), availableStorage[menuItem.itemId])
                if (newFile != null) {
                    viewModel.requestPath(availableStorage[menuItem.itemId])
                } else {
                    FileUtils.alertMountStorage(requireContext(), layoutInflater)
                }
            } else {
                return@setOnMenuItemClickListener false
            }

            return@setOnMenuItemClickListener true
        }
        pathsPopupMenu.inflate(R.menu.file_browser)
        pathsPopupMenu.menu.findItem(R.id.app_folder).isVisible = viewModel.isCustomStorageFolder

        availableStorage.forEachIndexed { index, documentFile ->
            val standardName = FileUtils.getPathName(requireContext(), documentFile)

            pathsPopupMenu.menu.add(R.id.locations_storage, index, Menu.NONE, standardName).apply {
                setIcon(R.drawable.ic_save_white_24dp)
            }
        }
    }

    private fun sendBroadcast(keyword: String) {
        requireContext().sendBroadcast(Intent(keyword))
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(receiver, filter)
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    override fun onPause() {
        super.onPause()
        backPressedCallback.remove()
    }

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(receiver)
    }
}

class PathContentViewModel(context: Context, fileModel: FileModel) {
    val isHome = fileModel.file.getUri() == HOME_URI

    val isFirst = fileModel.file.parent == null

    val title = FileUtils.getPathName(context, fileModel.file)

    val isEnabled = isHome || fileModel.file.canRead()

    companion object {
        val HOME_URI: Uri = Uri.fromFile(File(Keyword.FILE_BROWSER_HOME))
    }
}

class FilePathViewHolder constructor(
    private val clickListener: (FileModel) -> Unit,
    private var binding: ListPathBinding,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(context: Context, fileModel: FileModel) {
        binding.viewModel = PathContentViewModel(context, fileModel)
        binding.button.setOnClickListener {
            clickListener(fileModel)
        }
        binding.executePendingBindings()
    }
}

class PathAdapter(
    private val clickListener: (FileModel) -> Unit,
) : ListAdapter<FileModel, FilePathViewHolder>(FileModelItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilePathViewHolder {
        return FilePathViewHolder(
            clickListener,
            ListPathBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: FilePathViewHolder, position: Int) {
        holder.bind(holder.itemView.context, getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).listId
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_PATH
    }

    companion object {
        const val VIEW_TYPE_PATH = 0
    }
}

class FileModelItemCallback : DiffUtil.ItemCallback<FileModel>() {
    override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
        return oldItem.hashCode() == newItem.hashCode()
    }
}