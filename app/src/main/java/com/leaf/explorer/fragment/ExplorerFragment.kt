package com.leaf.explorer.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import dagger.hilt.android.AndroidEntryPoint
import com.leaf.explorer.R
import com.leaf.explorer.activity.SharingActivity
import com.leaf.explorer.adapter.ExplorerAdapter
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.database.model.FavFolder
import com.leaf.explorer.model.Category
import com.leaf.explorer.model.Storage
import com.leaf.explorer.util.FileUtils
import com.leaf.explorer.viewmodel.ExplorerViewModel

@AndroidEntryPoint
class ExplorerFragment : Fragment(R.layout.layout_explorer_fragment) {

    private val explorerViewModel: ExplorerViewModel by viewModels()

    private fun openFileBrowser(pathUri: Uri) {
        val file = DocumentFile.fromUri(requireContext(), pathUri)
        val newFile = FileUtils.verifyFile(requireContext(), file)
        if (newFile != null) {
            explorerViewModel.setHomePath(file)
            requireContext().sendBroadcast(Intent(Keyword.FILE_BROWSER_PATH))
        } else {
            FileUtils.alertMountStorage(requireContext(), layoutInflater)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val layoutManager = recyclerView.layoutManager
        check(layoutManager is GridLayoutManager) {
            "Grid layout manager is needed!"
        }
        if (layoutManager.spanCount > 6) layoutManager.spanCount = 6

        val adapter = ExplorerAdapter(requireContext()) { any, clickType ->
            when (clickType) {
                ExplorerAdapter.ClickType.Default -> {
                    if (any is Storage)
                        openFileBrowser(any.storageUri)
                    if (any is Category) {
                        if (any.name == getString(R.string.transfers))
                            startActivity(Intent(context, SharingActivity::class.java))
                        else
                            Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                    }
                    if (any is FavFolder) {
                        openFileBrowser(any.uri)
                    }
                }
                ExplorerAdapter.ClickType.FavoriteRemove -> {
                    if (any is FavFolder) {
                        explorerViewModel.actionFavFolder(any.uri, true)
                    }
                }
            }
        }

        recyclerView.adapter = adapter

        if (explorerViewModel.preLoadFavorites) {
            explorerViewModel.addPreFav.forEach {
                explorerViewModel.actionFavFolder(it.getUri())
            }
        }

        explorerViewModel.favFolderList.observe(viewLifecycleOwner) {
            it.forEach { fav ->
                if (!LeafFile.uriExists(requireContext(), fav.uri)) {
                    explorerViewModel.actionFavFolder(fav.uri, true)
                }
            }
        }

        explorerViewModel.getHomeList.observe(viewLifecycleOwner) {
//            it.forEach { any ->
//                if (any is Category)
//                    layoutManager.spanCount = 4
//            }

            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (it[position]) {
                        // TODO : fIX it
                        is Category/*, is Recent, is Other */ -> 1
                        else -> layoutManager.spanCount
                    }
                }
            }

            adapter.submitList(it)
        }
    }
}