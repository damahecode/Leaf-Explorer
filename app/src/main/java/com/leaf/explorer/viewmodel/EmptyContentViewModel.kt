package com.leaf.explorer.viewmodel

import android.view.View
import androidx.databinding.ObservableBoolean

class EmptyContentViewModel {
    var hasContent = ObservableBoolean()

    var loading = ObservableBoolean()

    fun with(content: View, hasContent: Boolean) {
        this.hasContent.set(hasContent)
        content.visibility = if (hasContent) View.VISIBLE else View.GONE
    }
}