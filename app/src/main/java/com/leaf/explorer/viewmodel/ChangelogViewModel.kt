package com.leaf.explorer.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.leaf.explorer.data.ExtrasRepository
import javax.inject.Inject

@HiltViewModel
class ChangelogViewModel @Inject internal constructor(
    private val extrasRepository: ExtrasRepository,
) : ViewModel() {
    val changelog = extrasRepository.getChangelog()

    val shouldShowChangelog
        get() = extrasRepository.shouldShowLatestChangelog()

    fun declareLatestChangelogAsShown() = extrasRepository.declareLatestChangelogAsShown()
}