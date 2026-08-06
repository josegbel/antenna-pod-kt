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
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SynchronizationPreferencesFragment : AnimatedPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_synchronization)
        setupScreen()
        updateScreen()
    }

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onStart() {
        super.onStart()
        (activity as AppCompatActivity?)!!.supportActionBar!!.setTitle(R.string.synchronization_pref)
        updateScreen()
        updateActionBar()
        EventBus.getDefault().register(this)
    }

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = ""
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    @SuppressLint("UseRequireInsteadOfGet")
    fun syncStatusChanged(event: SyncServiceEvent) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        updateScreen()
        if (event.messageResId == R.string.sync_status_error ||
            event.messageResId == R.string.sync_status_success
        ) {
            updateLastSyncReport(
                SynchronizationSettings.isLastSyncSuccessful(),
                SynchronizationSettings.getLastSyncAttempt()
            )
        } else {
            (activity as AppCompatActivity?)!!.supportActionBar!!.setSubtitle(event.messageResId)
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
            updateActionBar()
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
    private fun updateActionBar() {
        // Do not call from onCreate; ActionBar is not yet available at that point
        if (SynchronizationSettings.isProviderConnected()) {
            updateLastSyncReport(
                SynchronizationSettings.isLastSyncSuccessful(),
                SynchronizationSettings.getLastSyncAttempt()
            )
        } else {
            (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = null
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

    @SuppressLint("UseRequireInsteadOfGet")
    private fun updateLastSyncReport(successful: Boolean, lastTime: Long) {
        val status = String.format(
            "%1\$s (%2\$s)",
            getString(
                if (successful) {
                    R.string.gpodnetsync_pref_report_successful
                } else {
                    R.string.gpodnetsync_pref_report_failed
                }
            ),
            DateUtils.getRelativeDateTimeString(
                context,
                lastTime,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_SHOW_TIME
            )
        )
        (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = status
    }

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
