package com.leaf.explorer.activity

import android.os.Bundle
import android.view.*
import com.leaf.explorer.R
import com.leaf.explorer.database.model.WebTransfer
import dagger.hilt.android.AndroidEntryPoint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genonbeta.android.framework.io.OpenableContent
import com.leaf.explorer.NavSharingDirections
import com.leaf.explorer.app.Activity
import com.leaf.explorer.config.Keyword
import com.leaf.explorer.fragment.SharingSelectionsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.leaf.explorer.model.UTransferItem
import java.lang.ref.WeakReference
import java.text.Collator
import java.util.*
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class SharingActivity : Activity() {

    private val preparationViewModel: PreparationViewModel by viewModels()
    private val sharingSelectionsViewModel: SharingSelectionsViewModel by viewModels()

    private val navController by lazy {
        navController(R.id.nav_host_fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_sharing)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)

        val prepareSharing = findViewById<ConstraintLayout>(R.id.layout_prepare_sharing)
        val progressBar = findViewById<ContentLoadingProgressBar>(R.id.progressBar)

        findViewById<View>(R.id.cancelButton).setOnClickListener {
            if (!navController.navigateUp()) finish()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            title = destination.label
        }

        newIntent(intent)

        preparationViewModel.shared.observe(this) {
            when (it) {
                is PreparationState.Progress -> {
                    prepareSharing.visibility = View.VISIBLE
                    progressBar.max = it.total

                    if (Build.VERSION.SDK_INT >= 24) {
                        progressBar.setProgress(it.index, true)
                    } else {
                        progressBar.progress = it.index
                    }
                }
                is PreparationState.Ready -> {
                    sharingSelectionsViewModel.serve(it.list)
                    prepareSharing.visibility = View.GONE
                    if (it.open) {
                        navController.navigate(NavSharingDirections.actionGlobalSharingFragment())
                    }
                }
                is PreparationState.ReadyFileModel -> {
                    navController.navigate(
                        NavSharingDirections.actionGlobalSharingFragment()
                    )
                }
            }
        }
    }

    private fun newIntent(intent: Intent?) {
        super.onNewIntent(intent)

        when (intent?.action) {
            ACTION_OPEN_WEB_TRANSFER -> if (intent.hasExtra(EXTRA_WEB_TRANSFER)) {
                val webTransfer = intent.getParcelableExtra<WebTransfer>(EXTRA_WEB_TRANSFER)
                if (webTransfer != null) {
                    Log.d("SharingActivity", "onNewIntent: $webTransfer")
                    navController.navigate(NavSharingDirections.actionGlobalWebTransferDetailsFragment(webTransfer))
                }
            }

            Keyword.INTENT_LEAF_SHARE -> {
                val leafTransfer = intent.getBooleanExtra(Keyword.INTENT_LEAF_SHARE, false)
                if (leafTransfer) {
                    preparationViewModel.fileModelShare()
                }
            }
        }

        val list: List<Uri>? = try {
            when (intent?.action) {
                Intent.ACTION_SEND -> (intent.getParcelableExtra(Intent.EXTRA_STREAM) as Uri?)?.let {
                    Collections.singletonList(it)
                }
                Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                else -> null
            }
        } catch (e: Throwable) {
            null
        }

        if (!list.isNullOrEmpty()) {
            preparationViewModel.consume(list, true)
        }
    }

    override fun onStart() {
        super.onStart()
        backend.notifyActivityInForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        backend.notifyActivityInForeground(false)
    }

    companion object {

        const val ACTION_OPEN_WEB_TRANSFER = "com.leaf.explorer.action.OPEN_WEB_TRANSFER"

        const val EXTRA_WEB_TRANSFER = "extraWebTransfer"
    }
}

@HiltViewModel
class PreparationViewModel @Inject internal constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private var consumer: Job? = null

    private val context = WeakReference(context)

    val shared = MutableLiveData<PreparationState>()

    fun fileModelShare() {
        shared.postValue(PreparationState.ReadyFileModel())
    }

    @Synchronized
    fun consume(contents: List<Uri>, open: Boolean) {
        if (consumer != null) return

        consumer = viewModelScope.launch(Dispatchers.IO) {
            val groupId = Random.nextLong()
            val list = mutableListOf<UTransferItem>()

            contents.forEachIndexed { index, uri ->
                val context = context.get() ?: return@launch
                val id = index.toLong()

                OpenableContent.from(context, uri).runCatching {
                    shared.postValue(PreparationState.Progress(index, contents.size, name))
                    list.add(UTransferItem(id, groupId, name, mimeType, size, null, uri.toString()))
                }
            }

            val collator = Collator.getInstance()
            list.sortWith { o1, o2 -> collator.compare(o1.name, o2.name) }

            shared.postValue(PreparationState.Ready(groupId, list, open))

            consumer = null
        }
    }
}

sealed class PreparationState {
    class Progress(val index: Int, val total: Int, val title: String) : PreparationState()

    class Ready(val id: Long, val list: List<UTransferItem>, val open: Boolean) : PreparationState()

    class ReadyFileModel : PreparationState()
}