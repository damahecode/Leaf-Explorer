package com.leaf.explorer.fragment.dialog

import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.genonbeta.android.framework.io.DocumentFile
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.leaf.explorer.R
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.model.FileModel
import com.leaf.explorer.util.FileUtils
import com.leaf.explorer.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class FileDeletionDialog : BottomSheetDialogFragment() {
    private val selectionViewModel: HomeViewModel by activityViewModels()

    private val copiedItems: MutableList<Uri> = ArrayList()
    private var fileList = ArrayList<FileModel>()
    private lateinit var text1: TextView
    private lateinit var textDetails: TextView
    private lateinit var deleteButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var deleteProgress: ProgressBar
    private var stopDeleting = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_file_deletion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        text1 = view.findViewById(R.id.text1)
        textDetails = view.findViewById(R.id.textDetails)
        deleteProgress = view.findViewById(R.id.progress)
        deleteButton = view.findViewById(R.id.deleteButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            stopDeleting = true
            dismiss()
        }

        selectionViewModel.selectionState.observe(this) {
            it.forEach { data ->
                if (data is FileModel) {
                    val docFile = FileUtils.verifyFile(requireContext(), data.file)
                    if (docFile != null) {
                        copiedItems.add(docFile.getUri())
                    }
                    fileList.add(data)
                }
                actionDelete(copiedItems.size == it.size)
            }
        }

        isCancelable = false
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        requireContext().sendBroadcast(Intent(Keyword.FILE_BROWSER_REFRESH))
        copiedItems.clear()
    }

    private fun actionDelete(delete: Boolean) {
        val details = getString(R.string.text_total) + " : " + copiedItems.size.toString()
        textDetails.text = details

        if (delete) {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                deletionDialog(requireContext(), copiedItems)

                for (fileModel in fileList) {
                    fileModel.isSelected = false
                    selectionViewModel.setSelected(fileModel, fileModel.isSelected)
                }
            }
        }
    }

    private fun deletionDialog(context: Context, copiedItems: MutableList<Uri>) {
        var delCount = 0

        while (delCount < copiedItems.size) {
            if (stopDeleting)
                break

            deleteProgress.visibility = View.VISIBLE
            text1.text = getString(R.string.wait)
            deleteButton.visibility = View.GONE

            try {
                val s = copiedItems[delCount].scheme
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    deleteFile(File(copiedItems[delCount].path!!))
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    val file = DocumentFile.fromUri(context, copiedItems[delCount])
                    deleteDocumentFile(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delCount++

            if (delCount == copiedItems.size) {
                stopDeleting = true
                Toast.makeText(context, R.string.success, Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun deleteDocumentFile(file: DocumentFile) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (anotherFile in file.listFiles(requireContext())) {
                    deleteDocumentFile(anotherFile)
                }
            }

            if (!file.delete(requireContext())) {
                // TODO fIX : fail to delete
            }
        } else {
            // TODO fIX : File Not Found
        }
    }

    private fun deleteFile(file: File) {
        if (file.exists()) {
            if (file.isDirectory) {
                for (anotherFile in file.listFiles()!!) {
                    deleteFile(anotherFile)
                }
            }

            if (!file.delete()) {
                // TODO fIX : fail to delete
            }
        } else {
            // TODO fIX : File Not Found
        }
    }
}