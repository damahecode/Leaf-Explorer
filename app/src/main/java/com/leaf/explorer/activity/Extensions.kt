package com.leaf.explorer.activity

import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment

fun FragmentActivity.navController(id: Int): NavController {
    return (supportFragmentManager.findFragmentById(id) as NavHostFragment).navController
}