package com.leaf.explorer.activity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.leaf.explorer.R
import com.leaf.explorer.activity.IntroductionFragmentStateAdapter.PageItem
import com.leaf.explorer.app.Activity
import com.leaf.explorer.databinding.LayoutSplashBinding
import com.leaf.explorer.databinding.ListPermissionBinding
import com.leaf.explorer.util.Permissions
import com.leaf.explorer.util.Permissions.Permission
import com.leaf.explorer.util.Resources.attrToRes
import com.leaf.explorer.util.Resources.resToColor

@AndroidEntryPoint
class WelcomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasIntroductionShown()) {
            finish()
        }

        setContentView(R.layout.activity_welcome)

        skipPermissionRequest = true
        welcomePageDisallowed = true

        val titleContainer: FrameLayout = findViewById(R.id.titleContainer)
        val titleText: TextView = findViewById(R.id.title)
        val nextButton: FloatingActionButton = findViewById(R.id.nextButton)
        val previousButton: AppCompatImageView = findViewById(R.id.previousButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val viewPager = findViewById<ViewPager2>(R.id.activity_welcome_view_pager)
        val appliedColor: Int = com.google.android.material.R.attr.colorSecondary.attrToRes(this).resToColor(this)

        progressBar.progressTintList = ColorStateList.valueOf(appliedColor)

        val adapter = IntroductionFragmentStateAdapter(this, supportFragmentManager, lifecycle)

        adapter.add(PageItem(getString(R.string.welcome), IntroductionSplashFragment::class.java.name))

        val permissionPosition = adapter.itemCount
        if (Build.VERSION.SDK_INT >= 23) adapter.add(
            PageItem(getString(R.string.introduction_set_up_permissions), IntroductionPermissionFragment::class.java.name)
        )

        adapter.add(PageItem(getString(R.string.introduction_personalize), IntroductionPrefsFragment::class.java.name))
        adapter.add(PageItem(getString(R.string.introduction_finished), IntroductionAllSetFragment::class.java.name))

        progressBar.max = (adapter.itemCount - 1) * 100

        previousButton.setOnClickListener {
            if (viewPager.currentItem - 1 >= 0) {
                viewPager.setCurrentItem(viewPager.currentItem - 1, true)
            }
        }

        nextButton.setOnClickListener {
            if (viewPager.currentItem + 1 < adapter.itemCount) {
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                if (Permissions.checkRunningConditions(applicationContext)) {
                    // end presentation
                    defaultPreferences.edit {
                        putBoolean("introduction_shown", true)
                    }
                    startActivity(Intent(this@WelcomeActivity, HomeActivity::class.java))

                    finish()
                } else {
                    viewPager.setCurrentItem(permissionPosition, true)
                    Toast.makeText(this, R.string.warning_permissions_not_accepted, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                    progressBar.progress = position * 100 + (positionOffset * 100).toInt()
                    if (position == 0) {
                        progressBar.alpha = positionOffset
                        previousButton.alpha = positionOffset
                        titleText.alpha = positionOffset
                    } else {
                        progressBar.alpha = 1.0f
                        previousButton.alpha = 1.0f
                        titleText.alpha = 1.0f
                    }
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val lastPage = position == adapter.itemCount - 1
                    TransitionManager.beginDelayedTransition(titleContainer)

                    titleText.text = adapter.getItem(position).title
                    progressBar.visibility = if (lastPage) View.GONE else View.VISIBLE

                    nextButton.setImageResource(
                        if (lastPage) {
                            R.drawable.ic_check_white_24dp
                        } else {
                            R.drawable.ic_navigate_next_white_24dp
                        }
                    )
                }
            }
        )

        viewPager.adapter = adapter
    }
}

class IntroductionSplashFragment : Fragment(R.layout.layout_splash) {
    private lateinit var binding: LayoutSplashBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutSplashBinding.bind(view)
    }

    override fun onResume() {
        super.onResume()
        slideSplashView()
    }

    private fun slideSplashView() {
        val context = context ?: return
        binding.logo.animation = loadAnimation(context, R.anim.enter_from_bottom_centered)
        binding.appBrand.animation = loadAnimation(context, R.anim.enter_from_bottom)
        binding.welcomeText.animation = loadAnimation(context, android.R.anim.fade_in).also { it.startOffset = 1000 }
    }
}

class IntroductionPermissionFragment : Fragment(R.layout.layout_permissions) {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermissionsState()
    }

    private val adapter = PermissionsAdapter {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request()
        } else {
            requestPermissions.launch(it.perm.id)
        }
    }.apply {
        setHasStableIds(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.adapter = adapter

        updatePermissionsState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsState()
    }

    private fun updatePermissionsState() {
        adapter.submitList(
            Permissions.getAll().map {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    CheckedPermission(it, Environment.isExternalStorageManager())
                } else {
                    CheckedPermission(it, checkSelfPermission(requireContext(), it.id) == PERMISSION_GRANTED)
                }
            }
        )
    }

    val request = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", requireActivity().applicationContext.packageName))
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            }
        }
    }
}

class IntroductionPrefsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preference_introduction_look)
        loadThemeOptionsTo(requireContext(), findPreference("theme"))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutParams = FrameLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    companion object {
        fun loadThemeOptionsTo(context: Context, themePreference: ListPreference?) {
            if (themePreference == null) return

            val valueList: MutableList<String> = arrayListOf("light", "dark")
            val titleList: MutableList<String> = arrayListOf(
                context.getString(R.string.light_theme),
                context.getString(R.string.dark_theme)
            )

            if (Build.VERSION.SDK_INT >= 26) {
                valueList.add("system")
                titleList.add(context.getString(R.string.follow_system_theme))
            } else {
                valueList.add("battery")
                titleList.add(context.getString(R.string.battery_saver_theme))
            }

            themePreference.entries = titleList.toTypedArray()
            themePreference.entryValues = valueList.toTypedArray()
        }
    }
}

class IntroductionAllSetFragment : Fragment(R.layout.layout_introduction_all_set)

class IntroductionFragmentStateAdapter(
    val context: Context, fm: FragmentManager, lifecycle: Lifecycle,
) : FragmentStateAdapter(fm, lifecycle) {
    private val fragments: MutableList<PageItem> = ArrayList()

    private val fragmentFactory: FragmentFactory = fm.fragmentFactory

    fun add(fragment: PageItem) {
        fragments.add(fragment)
    }

    override fun createFragment(position: Int): Fragment {
        val item = getItem(position)
        val fragment = item.fragment ?: fragmentFactory.instantiate(context.classLoader, item.clazz)

        item.fragment = fragment

        return fragment
    }

    override fun getItemCount(): Int = fragments.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun getItem(position: Int): PageItem = synchronized(fragments) { fragments[position] }

    @Parcelize
    data class PageItem(var title: String, var clazz: String) : Parcelable {
        @IgnoredOnParcel
        var fragment: Fragment? = null
    }
}

class PermissionContentViewModel(permission: CheckedPermission) {
    val title = permission.perm.title

    val description = permission.perm.description

    val granted = permission.granted
}

data class CheckedPermission(val perm: Permission, val granted: Boolean)

class PermissionItemCallback : DiffUtil.ItemCallback<CheckedPermission>() {
    override fun areItemsTheSame(oldItem: CheckedPermission, newItem: CheckedPermission): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: CheckedPermission, newItem: CheckedPermission): Boolean {
        return oldItem != newItem
    }
}

class PermissionViewHolder(
    private val clickListener: (CheckedPermission) -> Unit,
    private val binding: ListPermissionBinding,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(permission: CheckedPermission) {
        binding.viewModel = PermissionContentViewModel(permission)
        binding.button.setOnClickListener {
            clickListener(permission)
        }
        binding.executePendingBindings()
    }
}

class PermissionsAdapter(
    private val clickListener: (CheckedPermission) -> Unit,
) : ListAdapter<CheckedPermission, PermissionViewHolder>(PermissionItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        return PermissionViewHolder(
            clickListener,
            ListPermissionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).perm.id.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_PERMISSION
    }

    companion object {
        private const val VIEW_TYPE_PERMISSION = 0
    }
}