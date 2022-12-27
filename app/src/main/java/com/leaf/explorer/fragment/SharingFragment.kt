package com.leaf.explorer.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.genonbeta.android.framework.io.LeafFile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import com.leaf.explorer.R
import com.leaf.explorer.activity.PreparationState
import com.leaf.explorer.activity.PreparationViewModel
import com.leaf.explorer.databinding.LayoutEmptyContentBinding
import com.leaf.explorer.databinding.ListSharingItemBinding
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.model.UTransferItem
import com.leaf.explorer.viewmodel.EmptyContentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class SharingFragment : Fragment(R.layout.layout_sharing) {

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uriList = ArrayList<Uri>()

        val dataUri = result.data?.data
        val clipData = result.data?.clipData

        if (dataUri != null) {
            try {
                uriList.clear()
                uriList.add(dataUri)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

        var itemCount = 0
        if (clipData != null) {
            try {
                uriList.clear()
                do {
                    uriList.add(clipData.getItemAt(itemCount).uri)
                    itemCount++
                } while (itemCount < clipData.itemCount)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

        preparationViewModel.consume(uriList, false)
    }

    private fun addFiles(type: String) {
        // Initialize intent
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        // set type
        intent.type = type
        // Launch intent
        resultLauncher.launch(intent)
    }

    private val sharingSelectionsViewModel: SharingSelectionsViewModel by viewModels()
    private val preparationViewModel: PreparationViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = LayoutEmptyContentBinding.bind(view.findViewById(R.id.emptyView))

        val adapter = SharingContentAdapter()

        view.findViewById<View>(R.id.shareOnWeb).setOnClickListener {
            findNavController().navigate(SharingFragmentDirections.actionSharingFragmentToWebShareLauncherFragment2())
        }
        view.findViewById<View>(R.id.addButton).setOnClickListener {
            addFiles("*/*")
        }

        val emptyContentViewModel = EmptyContentViewModel()

        emptyView.viewModel = emptyContentViewModel
        emptyView.emptyText.setText(R.string.empty_transfers_list)
        emptyView.emptyImage.setImageResource(R.drawable.ic_compare_arrows_white_24dp)
        emptyView.executePendingBindings()

        recyclerView.adapter = adapter

        sharingSelectionsViewModel.sharingFiles.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            emptyContentViewModel.with(recyclerView, it.isNotEmpty())
        }

        preparationViewModel.shared.observe(viewLifecycleOwner) {
            when (it) {
                is PreparationState.Progress -> {}
                is PreparationState.Ready -> sharingSelectionsViewModel.update()
                is PreparationState.ReadyFileModel -> {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sharingSelectionsViewModel.dropAll()
    }
}

class UTransferItemCallback : DiffUtil.ItemCallback<TransferItem>() {
    override fun areItemsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean {
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean {
        return oldItem == newItem
    }
}

class SharingContentAdapter : ListAdapter<TransferItem, SharingViewHolder>(UTransferItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharingViewHolder {
        return SharingViewHolder(
            ListSharingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SharingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class SharingViewHolder(private val binding: ListSharingItemBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(transferItem: TransferItem) {
        binding.viewModel = TransferItemContentViewModel(transferItem)
        binding.executePendingBindings()
    }
}

class TransferItemContentViewModel(val transferItem: TransferItem) {
    val name = transferItem.name

    val size = LeafFile.formatLength(transferItem.size)

    val mimeType = transferItem.mimeType
}

@HiltViewModel
class SharingSelectionsViewModel @Inject internal constructor(
    private val selectionRepository: SharingRepository,
) : ViewModel() {

    private val _sharingFiles = MutableLiveData<List<TransferItem>>()

    val sharingFiles = liveData {
        update()
        emitSource(_sharingFiles)
    }

    fun update() {
        viewModelScope.launch(Dispatchers.IO) {
            val mergedList = mutableListOf<TransferItem>().apply {
                selectionRepository.getSelections().forEach {
                    if (it is FileModel) {
                        add(TransferItem(it.file.getName(), it.file.getLength(), it.file.getType(), it.file.getUri().toString()))
                    }
                    if (it is UTransferItem) {
                        add(TransferItem(it.name, it.size, it.mimeType, it.location))
                    }
                }
            }

            _sharingFiles.postValue(mergedList)
        }
    }

    fun serve(list: List<Any>) {
        selectionRepository.addAll(list)
    }

    fun dropAll() {
        selectionRepository.clearSelections()
    }
}

data class TransferItem(val name: String, val size: Long, val mimeType: String, val location: String)

@Singleton
class SharingRepository @Inject constructor() {
    private val selections = mutableListOf<Any>()

    fun addAll(list: List<Any>) {
        synchronized(selections) {
            selections.addAll(list)
        }
    }

    fun clearSelections() {
        selections.clear()
    }

    fun getSelections() = ArrayList(selections)
}