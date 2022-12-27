package com.leaf.explorer.viewholder

import com.leaf.explorer.adapter.custom.EditableListAdapter
import com.leaf.explorer.databinding.ListSectionTitleBinding
import com.leaf.explorer.model.TitleSectionContentModel
import com.leaf.explorer.viewmodel.content.TitleSectionContentViewModel

class TitleSectionViewHolder(val binding: ListSectionTitleBinding) : EditableListAdapter.EditableViewHolder(binding.root) {
    fun bind(contentModel: TitleSectionContentModel) {
        binding.viewModel = TitleSectionContentViewModel(contentModel)
        binding.executePendingBindings()
    }
}