package com.leaf.explorer.fragment

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.genonbeta.android.framework.app.Storage
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import com.leaf.explorer.R
import com.leaf.explorer.activity.SharingActivity
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.databinding.LayoutHomeFragmentBinding
import com.leaf.explorer.fragment.HomeFragmentStateAdapter.PageItem
import com.leaf.explorer.util.*
import com.leaf.explorer.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.layout_home_fragment), MenuProvider {

    private val addAccess = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val context = context

        if (uri != null && context != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val saf = DocumentFile.fromUri(context, uri, true)
            PreferenceUtil.setStringPreferences(saf.getName(), uri.toString())
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val a = intent.action ?: return
            if (a == Keyword.FILE_BROWSER_PATH)
                binding.viewPager.currentItem = 1
            if (a == Keyword.FILE_BROWSER_HOME)
                binding.viewPager.currentItem = 0
            if (a == Keyword.HOME_FRAGMENT_STORAGE_MOUNT)
                addAccess.launch(null)
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()
    lateinit var binding: LayoutHomeFragmentBinding
    private val filter = IntentFilter()
    private val selectedItems = ArrayList<Any>()
    private var favExits = false
    private var unselectShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filter.addAction(Keyword.FILE_BROWSER_PATH)
        filter.addAction(Keyword.FILE_BROWSER_HOME)
        filter.addAction(Keyword.HOME_FRAGMENT_STORAGE_MOUNT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding = LayoutHomeFragmentBinding.bind(view)
        val pagerAdapter = HomeFragmentStateAdapter(requireContext(), childFragmentManager, lifecycle)

        pagerAdapter.add(PageItem(getString(R.string.home), ExplorerFragment()))
        pagerAdapter.add(PageItem(getString(R.string.text_fileBrowser), FileBrowserFragment()))

        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.adapter = pagerAdapter

        binding.unselectButton.setOnClickListener {
            binding.unselectButton.visibility = View.GONE
            binding.pasteButton.visibility = View.GONE

            for (any in selectedItems) {
                FileUtils.getFileModel(any)?.let {
                    it.isSelected = false
                    homeViewModel.setSelected(it, it.isSelected)
                }
            }
            sendBroadcast(Keyword.FILE_BROWSER_UPDATE)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // Add menu items here
        menuInflater.inflate(R.menu.home_menu, menu)

        val selections = menu.findItem(R.id.selections)
        val leafShare = menu.findItem(R.id.leafShare)
        val rename = menu.findItem(R.id.rename)
        val createFolder = menu.findItem(R.id.create_folder)
        val delete = menu.findItem(R.id.delete)
        val copyTo = menu.findItem(R.id.copy)
        val moveTo = menu.findItem(R.id.move)
        val actionArchive = menu.findItem(R.id.actionArchive)
        val shareExternal = menu.findItem(R.id.share_external)
        val detail = menu.findItem(R.id.detail)
        val favorites = menu.findItem(R.id.favorites)
        val showHiddenFiles = menu.findItem(R.id.show_hidden_files)

        val prefShowHidden = PreferenceUtil.getPreferences(requireContext()).getBoolean(Keyword.SHOW_HIDDEN, false)

        homeViewModel.selectionState.observe(this) {
            selectedItems.clear()
            selectedItems.addAll(it)
            val enable = selectedItems.isNotEmpty()
            val size1 = selectedItems.size == 1

            binding.unselectButton.visibility = if (enable || unselectShow) View.VISIBLE else View.GONE

            selections.title = selectedItems.size.toString()
            selections.isEnabled = enable
            leafShare.isEnabled = enable
            createFolder.isEnabled = !enable
            rename.isEnabled = enable && size1
            delete.isEnabled = enable
            copyTo.isEnabled = enable
            moveTo.isEnabled = enable
            actionArchive.isEnabled = enable
            shareExternal.isEnabled = enable
            detail.isEnabled = enable && size1
            favorites.isEnabled = enable && size1
            showHiddenFiles.isEnabled = !enable
            showHiddenFiles.isChecked = prefShowHidden

            homeViewModel.favFolderList.observe(viewLifecycleOwner) { favFolders ->
                try {
                    favorites.title = if (size1) {
                        val fileModel = FileUtils.getFileModel(selectedItems[0])
                        if (fileModel != null) {
                            favorites.isEnabled = fileModel.file.isDirectory()

                            for (favFolder in favFolders) {
                                if (favFolder.uri == fileModel.file.getUri()) {
                                    favExits = true
                                    break
                                } else {
                                    favExits = false
                                }
                            }
                        }

                        if (favExits) {
                            getString(R.string.remove_favorites)
                        } else {
                            getString(R.string.add_favorites)
                        }
                    } else {
                        getString(R.string.add_favorites)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                favFolders.forEach { fav ->
                    if (!LeafFile.uriExists(requireContext(), fav.uri)) {
                        homeViewModel.actionFavFolder(fav.uri, true)
                    }
                }
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        // Handle the menu selection
        when (menuItem.itemId) {
            R.id.selections -> {}
            R.id.leafShare -> {
                homeViewModel.shareFileModel(selectedItems)
                val leafTransfer: Intent = Intent(context, SharingActivity::class.java)
                    .setAction(Keyword.INTENT_LEAF_SHARE)
                    .putExtra(Keyword.INTENT_LEAF_SHARE, true)
                startActivity(leafTransfer)
            }
            R.id.create_folder -> {
                if (binding.viewPager.currentItem == 1)
                    createFolderDialog.show()
            }
            R.id.rename -> renameFileModelDialog()
            R.id.delete -> findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToFileDeleteDialog())
            R.id.copy -> homeViewModel.currentPath?.let { pasteButtonShow(it.getUri(), false) }
            R.id.move -> homeViewModel.currentPath?.let { pasteButtonShow(it.getUri(), true) }
            R.id.actionArchive -> {
                Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                // homeViewModel.currentPath?.let { actionArchive(it.getUri()) }
            }
            R.id.share_external -> FileUtils.shareIntentAll(requireContext(), selectedItems)
            R.id.detail -> findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToFileDetailsDialog())
            R.id.favorites -> {
                if (selectedItems.size == 1) {
                    val fileModel = FileUtils.getFileModel(selectedItems[0])
                    if (fileModel != null) {
                        homeViewModel.actionFavFolder(fileModel.file.getUri(), favExits)
                    }
                }
            }
            R.id.show_hidden_files -> {
                val showHidden = PreferenceUtil.getPreferences(requireContext()).getBoolean(Keyword.SHOW_HIDDEN, false)
                if (showHidden) {
                    PreferenceUtil.editBooleanPreferences(requireContext(), Keyword.SHOW_HIDDEN, false)
                    menuItem.isChecked = false
                } else {
                    PreferenceUtil.editBooleanPreferences(requireContext(), Keyword.SHOW_HIDDEN, true)
                    menuItem.isChecked = true
                }
                sendBroadcast(Keyword.FILE_BROWSER_REFRESH)
            }
        }

        return false
    }

    private fun sendBroadcast(keyword: String) {
        requireContext().sendBroadcast(Intent(keyword))
    }

    override fun onResume() {
        super.onResume()
        //ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        requireContext().registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(receiver)
        createFolderDialog.dismiss()
    }

    private val createFolderDialog by lazy {
        val view = layoutInflater.inflate(R.layout.layout_create_rename, null, false)
        val editText = view.findViewById<EditText>(R.id.editText)
        val errorText = view.findViewById<TextView>(R.id.errorText)

        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.create_folder)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create, null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val displayName = editText.text.toString().trim()
                        val currentPath = FileUtils.verifyFile(requireContext(), homeViewModel.currentPath!!)
                        errorText.text = getString(R.string.error) + " :- " + if (displayName.isEmpty()) {
                            getString(R.string.error_empty_field)
                        } else if (currentPath?.createDirectory(requireContext(), displayName) != null) {
                            dialog.dismiss()
                            editText.text.clear()
                            sendBroadcast(Keyword.FILE_BROWSER_REFRESH)
                            ""
                        } else {
                            getString(R.string.create_folder_failure)
                        }
                    }
                }
            }
    }

    private fun renameFileModelDialog() {
        val view = layoutInflater.inflate(R.layout.layout_create_rename, null, false)
        val editText = view.findViewById<EditText>(R.id.editText)
        val errorText = view.findViewById<TextView>(R.id.errorText)

        try {
            val fileModel = FileUtils.getFileModel(selectedItems[0])
            var extWarning = false

            if (fileModel != null) {
                editText.setText(fileModel.file.getName())

                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.rename)
                    .setView(view)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.rename, null)
                    .setCancelable(false)
                    .create().also { dialog ->
                        dialog.setOnShowListener {
                            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                                dialog.dismiss()
                                editText.text.clear()
                            }

                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                                val displayName = editText.text.toString().trim()

                                extWarning = if (!extWarning) {
                                    LeafFile.getFileExtension(fileModel.file.getName()) != LeafFile.getFileExtension(
                                        displayName
                                    )
                                } else {
                                    false
                                }

                                val renamedFile = FileUtils.verifyFile(requireContext(), fileModel.file)
                                errorText.text = getString(R.string.error) + " :- " + if (displayName.isEmpty()) {
                                    getString(R.string.error_empty_field)
                                } else if (displayName == fileModel.file.getName()) {
                                    getString(R.string.already_exits)
                                } else if (extWarning && !fileModel.file.isDirectory()) {
                                    getString(R.string.extWarning)
                                } else if (renamedFile?.renameTo(
                                        requireContext(),
                                        displayName) != null) {
                                    fileModel.isSelected = false
                                    homeViewModel.setSelected(fileModel, fileModel.isSelected)
                                    sendBroadcast(Keyword.FILE_BROWSER_REFRESH)
                                    dialog.dismiss()
                                    editText.text.clear()
                                    ""
                                } else {
                                    getString(R.string.unknown_failure)
                                }
                            }

                        }
                    }
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStorage(): Storage {
        return Storage(context)
    }

    private fun pasteButtonShow(copyPath: Uri, move: Boolean) {
        val copiedItems = ArrayList<DocumentFile>()

        for (any in selectedItems) {
            FileUtils.getFileModel(any)?.let {
                copiedItems.add(it.file)
                it.isSelected = false
                homeViewModel.setSelected(it, it.isSelected)
            }
        }

        unselectShow = true
        binding.pasteButton.visibility = View.VISIBLE
        sendBroadcast(Keyword.FILE_BROWSER_UPDATE)

        binding.pasteButton.setOnClickListener {
            if (binding.viewPager.currentItem == 1) {
                val currentPath = FileUtils.verifyFile(requireContext(), homeViewModel.currentPath!!)
                if (currentPath != null) {
                    val paste = object : CopyPasteUtils.PasteBuilder(context) {
                        override fun success() {
                            super.success()
                            pasteSuccessAction()
                        }

                        override fun dismiss() {
                            super.dismiss()
                            pasteSuccessAction()
                        }
                    }
                    paste.create(copyPath, CopyPasteUtils.getNode(requireContext(), getStorage(), copiedItems), move, currentPath.getUri())
                    paste.show()
                } else {
                    Toast.makeText(context, R.string.unknown_failure, Toast.LENGTH_SHORT).show()
                }

            } else {
                Toast.makeText(context, "Please choose storage path", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun actionArchive(copyPath: Uri) {
        val copiedItems = ArrayList<DocumentFile>()

        for (any in selectedItems) {
            FileUtils.getFileModel(any)?.let {
                copiedItems.add(it.file)
                it.isSelected = false
                homeViewModel.setSelected(it, it.isSelected)
            }
        }

        unselectShow = true
        binding.pasteButton.visibility = View.VISIBLE
        sendBroadcast(Keyword.FILE_BROWSER_UPDATE)

        binding.pasteButton.setOnClickListener {
            if (binding.viewPager.currentItem == 1) {
                homeViewModel.currentPath?.let {
                    ArchiveUtils.CreateZipFile(copyPath, requireContext(), getStorage(), copiedItems, it.getUri())
                    pasteSuccessAction()
                }
            } else {
                Toast.makeText(context, "Please choose storage path", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pasteSuccessAction() {
        binding.pasteButton.visibility = View.GONE
        unselectShow = false
        binding.unselectButton.visibility = View.GONE
        sendBroadcast(Keyword.FILE_BROWSER_REFRESH)
        getStorage().closeSu()
    }

}

class HomeFragmentStateAdapter(
    val context: Context, fm: FragmentManager, lifecycle: Lifecycle,
) : FragmentStateAdapter(fm, lifecycle) {
    private val fragments: MutableList<PageItem> = ArrayList()

    fun add(fragment: PageItem) {
        fragments.add(fragment)
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = getItem(position)

        return fragment.clazz
    }

    override fun getItemCount(): Int = fragments.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getItem(position: Int): PageItem = fragments[position]

    data class PageItem(var title: String, var clazz: Fragment)
}