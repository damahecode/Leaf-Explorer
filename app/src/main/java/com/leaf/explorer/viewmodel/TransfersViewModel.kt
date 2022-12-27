package com.leaf.explorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.leaf.explorer.data.WebDataRepository
import com.leaf.explorer.database.model.WebTransfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransfersViewModel @Inject internal constructor(
    private val webDataRepository: WebDataRepository,
) : ViewModel() {

    val transfersHistory = liveData {
        val webTransfers = webDataRepository.getReceivedContents()

        emitSource(webTransfers)
    }

    fun deleteTransferHistory(webTransfer: WebTransfer) {
        viewModelScope.launch(Dispatchers.IO) {
            webDataRepository.remove(webTransfer)
        }
    }
}