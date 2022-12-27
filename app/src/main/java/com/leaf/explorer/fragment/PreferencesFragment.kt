package com.leaf.explorer.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.leaf.explorer.R
import com.leaf.explorer.activity.IntroductionPrefsFragment

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_main_app)
        addPreferencesFromResource(R.xml.preferences_main_notification)
        IntroductionPrefsFragment.loadThemeOptionsTo(requireContext(), findPreference("theme"))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.preferences_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actions_preference_main_reset_to_defaults -> findNavController().navigate(
                PreferencesFragmentDirections.actionPreferencesFragment2ToResetPreferencesFragment()
            )
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}

class ResetPreferencesFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.preferences_reset_question)
            .setMessage(R.string.preferences_reset_notice)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    clear()
                }
                PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences_defaults_main, true)

                activity?.finish()
            }
            .show()
    }
}