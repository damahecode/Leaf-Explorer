package com.leaf.explorer.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.leaf.explorer.BuildConfig
import com.leaf.explorer.NavHomeDirections
import com.leaf.explorer.R
import com.leaf.explorer.app.Activity
import com.leaf.explorer.config.AppConfig
import com.leaf.explorer.viewmodel.ChangelogViewModel
import com.leaf.explorer.viewmodel.CrashLogViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class HomeActivity : Activity(), NavigationView.OnNavigationItemSelectedListener {
    private val changelogViewModel: ChangelogViewModel by viewModels()

    private val crashLogViewModel: CrashLogViewModel by viewModels()

    private val navigationView: NavigationView by lazy {
        findViewById(R.id.nav_view)
    }

    private val drawerLayout: DrawerLayout by lazy {
        findViewById(R.id.drawer_layout)
    }

    private var pendingMenuItemId = 0

    private val navController by lazy {
        navController(R.id.nav_host_fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.open_navigation_drawer, R.string.close_navigation_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    applyAwaitingDrawerAction()
                }
            }
        )

        toolbar.setupWithNavController(navController, AppBarConfiguration(navController.graph, drawerLayout))
        navigationView.setNavigationItemSelectedListener(this)
        navController.addOnDestinationChangedListener { _, destination, _ -> title = destination.label }

        if (BuildConfig.FLAVOR == "googlePlay" || BuildConfig.FLAVOR == "fossReliant") {
            navigationView.menu.findItem(R.id.donate).isVisible = true
        }

        if (hasIntroductionShown()) {
            if (changelogViewModel.shouldShowChangelog) {
                navController.navigate(NavHomeDirections.actionGlobalChangelogFragment())
            } else if (crashLogViewModel.shouldShowCrashLog) {
                navController.navigate(NavHomeDirections.actionGlobalCrashLogFragment())
            }
        }

    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        openItem(item.itemId)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.close()
        } else {
            super.onBackPressed()
        }
    }

    private fun applyAwaitingDrawerAction() {
        if (pendingMenuItemId == 0) {
            return // drawer was opened, but nothing was clicked.
        }

        when (pendingMenuItemId) {
            R.id.about -> {}
            R.id.preferences -> navController.navigate(NavHomeDirections.actionGlobalNavPreferences())
            R.id.about_damahe_code -> startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://github.com/damahecode")))
            R.id.donate -> startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(AppConfig.URI_SHIV_SHAMBHU_DONATE)))
        }

        pendingMenuItemId = 0
    }

    private fun openItem(@IdRes id: Int) {
        pendingMenuItemId = id
        drawerLayout.close()
    }

}