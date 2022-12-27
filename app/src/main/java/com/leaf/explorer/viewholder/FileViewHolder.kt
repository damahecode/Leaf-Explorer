package com.leaf.explorer.viewholder

import com.leaf.explorer.adapter.FileAdapter
import com.leaf.explorer.adapter.custom.EditableListAdapter
import com.leaf.explorer.databinding.ListFileBinding
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.viewmodel.content.FileContentViewModel

class FileViewHolder(
    private val binding: ListFileBinding,
    private val clickListener: (FileModel, FileAdapter.ClickType) -> Unit,
) : EditableListAdapter.EditableViewHolder(binding.root) {
    fun bind(fileModel: FileModel) {
        binding.viewModel = FileContentViewModel(fileModel)
        binding.root.setOnClickListener {
            clickListener(fileModel, FileAdapter.ClickType.Default)
        }
        binding.selection.setOnClickListener {
            fileModel.isSelected = !fileModel.isSelected
            it.isSelected = fileModel.isSelected
            clickListener(fileModel, FileAdapter.ClickType.ToggleSelect)
        }
        binding.selection.isSelected = fileModel.isSelected
        binding.executePendingBindings()
    }
}