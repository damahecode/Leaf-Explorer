package com.leaf.explorer.fragment.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import com.leaf.explorer.R
import com.leaf.explorer.viewmodel.CrashLogViewModel

@AndroidEntryPoint
class CrashLogFragment : BottomSheetDialogFragment() {
    private val viewModel: CrashLogViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_crash_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val crashLogText = view.findViewById<TextView>(R.id.crashLog)
        val copyButton = view.findViewById<MaterialButton>(R.id.copyButton)

        viewModel.crashLog.observe(viewLifecycleOwner) { report ->
            crashLogText.text = report

            copyButton.setOnClickListener {
                val clipboardManager = it.context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_report), report))

                Toast.makeText(it.context, R.string.copy_text_to_clipboard_success, Toast.LENGTH_SHORT).show()

                findNavController().navigateUp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.clearCrashLog()
    }

    companion object {
        private const val TAG = "CrashLogFragment"
    }
}