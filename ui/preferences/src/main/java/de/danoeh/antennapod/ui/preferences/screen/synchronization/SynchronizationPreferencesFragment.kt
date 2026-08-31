package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SynchronizationPreferencesFragment : AnimatedPreferenceFragment() {

    private val viewModel: SynchronizationPreferencesViewModel by lazy {
        ViewModelProvider(this)[SynchronizationPreferencesViewModel::class.java]
    }

    private var syncStatusJob: Job? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_synchronization)
        setupScreen()
        updateScreen()
    }

    override fun onStart() {
        super.onStart()
        updateScreen()
        viewModel.onStarted()
        syncStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            launch { viewModel.uiState.collect { render(it) } }
            launch {
                viewModel.syncStatus.collect { event -> if (event != null) syncStatusChanged(event) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        syncStatusJob?.cancel()
        syncStatusJob = null
        actionBar().subtitle = ""
    }

    private fun syncStatusChanged(event: SyncServiceEvent) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        updateScreen()
        viewModel.onSyncEvent(event)
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun actionBar() = (activity as AppCompatActivity?)!!.supportActionBar!!

    private fun render(state: SyncSettingsUiState) {
        val actionBar = actionBar()
        actionBar.setTitle(state.titleRes)
        actionBar.subtitle = when (val subtitle = state.subtitle) {
            SyncSubtitle.Absent -> null
            SyncSubtitle.Cleared -> ""
            is SyncSubtitle.Message -> getString(subtitle.resId)
            is SyncSubtitle.LastSyncReport -> String.format(
                "%1\$s (%2\$s)",
                getString(
                    if (subtitle.successful) {
                        R.string.gpodnetsync_pref_report_successful
                    } else {
                        R.string.gpodnetsync_pref_report_failed
                    }
                ),
                DateUtils.getRelativeDateTimeString(
                    context,
                    subtitle.attemptedAt,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_TIME
                )
            )
        }
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun setupScreen() {
        val activity = activity
        findPreference<Preference>(PREFERENCE_GPODNET_SETLOGIN_INFORMATION)!!
            .setOnPreferenceClickListener {
                val dialog = object : AuthenticationDialog(
                    activity!!,
                    R.string.pref_gpodnet_setlogin_information_title,
                    false,
                    SynchronizationCredentials.getUsername(),
                    null
                ) {
                    override fun onConfirmed(username: String, password: String) {
                        SynchronizationCredentials.setPassword(password)
                    }
                }
                dialog.show()
                true
            }
        findPreference<Preference>(PREFERENCE_SYNC)!!.setOnPreferenceClickListener {
            SynchronizationQueue.instance!!.syncImmediately()
            true
        }
        findPreference<Preference>(PREFERENCE_FORCE_FULL_SYNC)!!.setOnPreferenceClickListener {
            SynchronizationQueue.instance!!.fullSync()
            true
        }
        findPreference<Preference>(PREFERENCE_LOGOUT)!!.setOnPreferenceClickListener {
            SynchronizationCredentials.clear()
            SynchronizationQueue.instance!!.clear()
            Snackbar.make(view!!, R.string.pref_synchronization_logout_toast, Snackbar.LENGTH_LONG).show()
            SynchronizationSettings.setSelectedSyncProvider(null)
            updateScreen()
            viewModel.onStarted()
            true
        }
    }

    private fun updateScreen() {
        val loggedIn = SynchronizationSettings.isProviderConnected()
        val preferenceHeader = findPreference<Preference>(PREFERENCE_SYNCHRONIZATION_DESCRIPTION)!!
        if (loggedIn) {
            val selectedProvider = SynchronizationProvider.fromIdentifier(selectedSyncProviderKey)
            preferenceHeader.setTitle("")
            preferenceHeader.setSummary(getProviderSummary(selectedProvider!!))
            preferenceHeader.setIcon(getProviderIcon(selectedProvider))
            preferenceHeader.onPreferenceClickListener = null
        } else {
            preferenceHeader.setTitle(R.string.synchronization_choose_title)
            preferenceHeader.setSummary(R.string.synchronization_summary_unchoosen)
            preferenceHeader.icon = null
            preferenceHeader.setOnPreferenceClickListener {
                chooseProviderAndLogin()
                true
            }
        }

        val gpodnetSetLoginPreference = findPreference<Preference>(PREFERENCE_GPODNET_SETLOGIN_INFORMATION)!!
        gpodnetSetLoginPreference.isVisible = isProviderSelected(SynchronizationProvider.GPODDER_NET)
        gpodnetSetLoginPreference.isEnabled = loggedIn
        findPreference<Preference>(PREFERENCE_SYNC)!!.isEnabled = loggedIn
        findPreference<Preference>(PREFERENCE_FORCE_FULL_SYNC)!!.isEnabled = loggedIn
        findPreference<Preference>(PREFERENCE_LOGOUT)!!.isEnabled = loggedIn
        if (loggedIn) {
            val summary = getString(
                R.string.synchronization_login_status,
                SynchronizationCredentials.getUsername(),
                SynchronizationCredentials.getHosturl()
            )
            val formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY)
            findPreference<Preference>(PREFERENCE_LOGOUT)!!.setSummary(formattedSummary)
        } else {
            findPreference<Preference>(PREFERENCE_LOGOUT)!!.setSummary(null)
        }
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun chooseProviderAndLogin() {
        val builder = MaterialAlertDialogBuilder(context!!)
        builder.setTitle(R.string.dialog_choose_sync_service_title)

        val providers = SynchronizationProvider.values()
        val adapter: ListAdapter = object : ArrayAdapter<SynchronizationProvider>(
            context!!,
            R.layout.alertdialog_sync_provider_chooser,
            providers
        ) {
            lateinit var holder: ViewHolder

            inner class ViewHolder {
                lateinit var icon: ImageView
                lateinit var title: TextView
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    view = View.inflate(context, R.layout.alertdialog_sync_provider_chooser, null)
                    holder = ViewHolder()
                    holder.icon = view.findViewById(R.id.icon)
                    holder.title = view.findViewById(R.id.title)
                    view.tag = holder
                } else {
                    holder = view.tag as ViewHolder
                }
                val synchronizationProvider = getItem(position)!!
                holder.title.setText(getProviderSummary(synchronizationProvider))
                holder.icon.setImageResource(getProviderIcon(synchronizationProvider))
                return view
            }
        }

        builder.setAdapter(adapter) { _, which ->
            when (providers[which]) {
                SynchronizationProvider.GPODDER_NET ->
                    GpodderAuthenticationFragment().show(childFragmentManager, GpodderAuthenticationFragment.TAG)
                SynchronizationProvider.NEXTCLOUD_GPODDER ->
                    NextcloudAuthenticationFragment().show(childFragmentManager, NextcloudAuthenticationFragment.TAG)
            }
            updateScreen()
        }

        builder.show()
    }

    private fun isProviderSelected(provider: SynchronizationProvider): Boolean {
        val selectedSyncProviderKey = selectedSyncProviderKey
        return provider.identifier.equals(selectedSyncProviderKey)
    }

    private val selectedSyncProviderKey: String?
        get() = SynchronizationSettings.getSelectedSyncProviderKey()

    @StringRes
    private fun getProviderSummary(provider: SynchronizationProvider): Int {
        return when (provider) {
            SynchronizationProvider.GPODDER_NET -> R.string.gpodnet_description
            SynchronizationProvider.NEXTCLOUD_GPODDER -> R.string.synchronization_summary_nextcloud
            else -> R.string.sync_status_error
        }
    }

    @DrawableRes
    private fun getProviderIcon(provider: SynchronizationProvider): Int {
        return when (provider) {
            SynchronizationProvider.GPODDER_NET -> R.drawable.gpodder_icon
            SynchronizationProvider.NEXTCLOUD_GPODDER -> R.drawable.nextcloud_logo
            else -> R.drawable.ic_error
        }
    }

    companion object {
        private const val PREFERENCE_SYNCHRONIZATION_DESCRIPTION = "preference_synchronization_description"
        private const val PREFERENCE_GPODNET_SETLOGIN_INFORMATION = "pref_gpodnet_setlogin_information"
        private const val PREFERENCE_SYNC = "pref_synchronization_sync"
        private const val PREFERENCE_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync"
        private const val PREFERENCE_LOGOUT = "pref_synchronization_logout"
    }
}
