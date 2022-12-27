package com.leaf.explorer.fragment.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.io.LeafFile
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.leaf.explorer.R
import com.leaf.explorer.data.WebDataRepository
import com.leaf.explorer.database.model.WebTransfer
import com.leaf.explorer.databinding.LayoutWebTransferDetailsBinding
import com.leaf.explorer.util.Activities
import com.leaf.explorer.util.MimeIcons
import javax.inject.Inject

@AndroidEntryPoint
class WebTransferDetailsFragment : BottomSheetDialogFragment() {
    @Inject
    lateinit var factory: WebTransferDetailsViewModel.Factory

    private val args: WebTransferDetailsFragmentArgs by navArgs()

    private val viewModel: WebTransferDetailsViewModel by viewModels {
        WebTransferDetailsViewModel.ModelFactory(factory, args.transfer)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_web_transfer_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutWebTransferDetailsBinding.bind(view)

        binding.viewModel = WebTransferContentViewModel(args.transfer)
        binding.executePendingBindings()

        viewModel.detail.observe(viewLifecycleOwner) {
            if (it == null) {
                findNavController().navigateUp()
            } else {
                binding.removeButton.setOnClickListener { _ ->
                    viewModel.remove(it)
                }
                binding.openButton.setOnClickListener { view ->
                    val file = viewModel.file.value ?: return@setOnClickListener

                    try {
                        file.sync(view.context)
                        Activities.view(view.context, file)
                    } catch (ignored: Throwable) {
                        view.isEnabled = false
                    }
                }
            }
        }

        viewModel.file.observe(viewLifecycleOwner) {
            if (it == null || !it.exists()) {
                binding.openButton.isEnabled = false
                binding.openButton.text = getString(R.string.removed)
            }
        }
    }
}

class WebTransferContentViewModel(transfer: WebTransfer) {
    val name = transfer.name

    val icon = MimeIcons.loadMimeIcon(transfer.mimeType)

    val mimeType = transfer.mimeType

    val size = LeafFile.formatLength(transfer.size)

    val uri = transfer.uri
}

class WebTransferDetailsViewModel @AssistedInject internal constructor(
    @ApplicationContext context: Context,
    private val webDataRepository: WebDataRepository,
    @Assisted private val transfer: WebTransfer,
) : ViewModel() {
    val detail = webDataRepository.getReceivedContent(transfer.id)

    val file = liveData(Dispatchers.IO) {
        try {
            emit(DocumentFile.fromUri(context, transfer.uri))
        } catch (e: Throwable) {
            emit(null)
        }
    }

    fun remove(transfer: WebTransfer) {
        viewModelScope.launch {
            webDataRepository.remove(transfer)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(transfer: WebTransfer): WebTransferDetailsViewModel
    }

    class ModelFactory(
        private val factory: Factory,
        private val transfer: WebTransfer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(WebTransferDetailsViewModel::class.java)) {
                "Requested unknown view model type"
            }

            return factory.create(transfer) as T
        }
    }
}