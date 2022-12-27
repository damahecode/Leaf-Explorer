package com.leaf.explorer.binding

import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.databinding.BindingAdapter
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.leaf.explorer.GlideApp
import com.leaf.explorer.fragment.TransferItem
import com.leaf.explorer.fragment.dialog.WebTransferContentViewModel
import com.leaf.explorer.util.MimeIcons
import com.leaf.explorer.viewmodel.content.FileContentViewModel

private fun load(imageView: ImageView, uri: Uri, circle: Boolean = false, @DrawableRes fallback: Int = 0) {
    GlideApp.with(imageView)
        .load(uri)
        .override(200)
        .also {
            if (fallback != 0) {
                it.fallback(fallback)
                    .error(fallback)
            }

            if (circle) {
                it.circleCrop()
            } else {
                it.centerCrop()
            }
        }
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(imageView)
}

@BindingAdapter("thumbnailOf")
fun loadThumbnailOf(imageView: ImageView, viewModel: FileContentViewModel) {
    if (viewModel.mimeType.startsWith("image/") || viewModel.mimeType.startsWith("video/")) {
        load(imageView, viewModel.uri, circle = true)
    } else {
        imageView.setImageDrawable(null)
    }
}

@BindingAdapter("iconOf")
fun loadIconOf(imageView: ImageView, mimeType: String) {
    imageView.setImageResource(MimeIcons.loadMimeIcon(mimeType))
}

@BindingAdapter("iconOf")
fun loadIconOf(imageView: ImageView, icon: Int) {
    GlideApp.with(imageView)
        .load(icon)
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(imageView)
}

@BindingAdapter("thumbnailOf")
fun loadThumbnailOf(imageView: ImageView, item: TransferItem) {
    if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
        load(imageView, Uri.parse(item.location), circle = true)
    } else {
        imageView.setImageDrawable(null)
    }
}

@BindingAdapter("thumbnailOf")
fun loadThumbnailOf(imageView: ImageView, viewModel: WebTransferContentViewModel) {
    if (viewModel.mimeType.startsWith("image/") || viewModel.mimeType.startsWith("video/")) {
        load(imageView, viewModel.uri, circle = true)
    } else {
        imageView.setImageDrawable(null)
    }
}