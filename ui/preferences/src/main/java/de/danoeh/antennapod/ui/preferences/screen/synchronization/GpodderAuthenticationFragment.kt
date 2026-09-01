package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.preferences.R
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guides the user through the authentication process.
 */
open class GpodderAuthenticationFragment : DialogFragment() {

    private lateinit var viewFlipper: ViewFlipper

    private var currentStep = STEP_DEFAULT

    private var service: GpodnetService? = null

    @Volatile
    private var username: String? = null

    @Volatile
    private var password: String? = null

    @Volatile
    private var selectedDevice: GpodnetDevice? = null
    private var devices: List<GpodnetDevice>? = null

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(context!!)
        dialog.setTitle(R.string.gpodnetauth_login_butLabel)
        dialog.setNegativeButton(R.string.cancel_label, null)
        dialog.setCancelable(false)
        this.setCancelable(false)

        val root = View.inflate(context, R.layout.gpodnetauth_dialog, null)
        viewFlipper = root.findViewById(R.id.viewflipper)
        advance()
        dialog.setView(root)

        return dialog.create()
    }

    private fun setupHostView(view: View) {
        val selectHost = view.findViewById<Button>(R.id.chooseHostButton)
        val serverUrlText = view.findViewById<EditText>(R.id.serverUrlText)
        selectHost.setOnClickListener {
            if (serverUrlText.text.length == 0) {
                return@setOnClickListener
            }
            SynchronizationCredentials.clear()
            SynchronizationQueue.instance!!.clear()
            SynchronizationCredentials.setHosturl(serverUrlText.text.toString())
            service = GpodnetService(
                AntennapodHttpClient.getHttpClient(),
                SynchronizationCredentials.getHosturl(), SynchronizationCredentials.getDeviceId(),
                SynchronizationCredentials.getUsername(), SynchronizationCredentials.getPassword()
            )
            dialog!!.setTitle(SynchronizationCredentials.getHosturl())
            advance()
        }
    }

    private fun setupLoginView(view: View) {
        val username = view.findViewById<EditText>(R.id.etxtUsername)
        val password = view.findViewById<EditText>(R.id.etxtPassword)
        val login = view.findViewById<Button>(R.id.butLogin)
        val txtvError = view.findViewById<TextView>(R.id.credentialsError)
        val progressBar = view.findViewById<ProgressBar>(R.id.progBarLogin)
        val createAccountWarning = view.findViewById<TextView>(R.id.createAccountWarning)

        if (SynchronizationCredentials.getHosturl().startsWith("http://")) {
            createAccountWarning.visibility = View.VISIBLE
        }
        password.setOnEditorActionListener { _, actionID, _ ->
            actionID == EditorInfo.IME_ACTION_GO && login.performClick()
        }

        login.setOnClickListener {
            val usernameStr = username.text.toString()
            val passwordStr = password.text.toString()

            if (usernameHasUnwantedChars(usernameStr)) {
                txtvError.setText(R.string.gpodnetsync_username_characters_error)
                txtvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            login.isEnabled = false
            progressBar.visibility = View.VISIBLE
            txtvError.visibility = View.GONE
            Keyboard.hide(activity)

            lifecycleScope.launch {
                try {
                    withContext(ioDispatcher) {
                        service!!.setCredentials(usernameStr, passwordStr)
                        service!!.login()
                        devices = service!!.getDevices()
                        this@GpodderAuthenticationFragment.username = usernameStr
                        this@GpodderAuthenticationFragment.password = passwordStr
                    }
                    login.isEnabled = true
                    progressBar.visibility = View.GONE
                    advance()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    login.isEnabled = true
                    progressBar.visibility = View.GONE
                    txtvError.text = error.cause?.message ?: error.message
                    txtvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDeviceView(view: View) {
        val deviceName = view.findViewById<EditText>(R.id.deviceName)
        val devicesContainer = view.findViewById<LinearLayout>(R.id.devicesContainer)
        deviceName.setText(generateDeviceName())

        val createDeviceButton = view.findViewById<MaterialButton>(R.id.createDeviceButton)
        createDeviceButton.setOnClickListener { createDevice(view) }

        for (device in devices!!) {
            val row = View.inflate(context, R.layout.gpodnetauth_device_row, null)
            val selectDeviceButton = row.findViewById<Button>(R.id.selectDeviceButton)
            selectDeviceButton.setOnClickListener {
                selectedDevice = device
                advance()
            }
            selectDeviceButton.text = device.caption
            devicesContainer.addView(row)
        }
    }

    private fun createDevice(view: View) {
        val deviceName = view.findViewById<EditText>(R.id.deviceName)
        val txtvError = view.findViewById<TextView>(R.id.deviceSelectError)
        val progBarCreateDevice = view.findViewById<ProgressBar>(R.id.progbarCreateDevice)

        val deviceNameStr = deviceName.text.toString()
        if (isDeviceInList(deviceNameStr)) {
            return
        }
        progBarCreateDevice.visibility = View.VISIBLE
        txtvError.visibility = View.GONE
        deviceName.isEnabled = false

        lifecycleScope.launch {
            try {
                val device = withContext(ioDispatcher) {
                    val deviceId = generateDeviceId(deviceNameStr)
                    service!!.configureDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE)
                    GpodnetDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0)
                }
                progBarCreateDevice.visibility = View.GONE
                selectedDevice = device
                advance()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                deviceName.isEnabled = true
                progBarCreateDevice.visibility = View.GONE
                txtvError.text = error.message
                txtvError.visibility = View.VISIBLE
            }
        }
    }

    private fun generateDeviceName(): String {
        val baseName = getString(R.string.gpodnetauth_device_name_default, Build.MODEL)
        var name = baseName
        var num = 1
        while (isDeviceInList(name)) {
            name = baseName + " (" + num + ")"
            num++
        }
        return name
    }

    private fun generateDeviceId(name: String): String {
        // devices names must be of a certain form:
        // https://gpoddernet.readthedocs.org/en/latest/api/reference/general.html#devices
        return name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase(Locale.US)
    }

    private fun isDeviceInList(name: String): Boolean {
        if (devices == null) {
            return false
        }
        val id = generateDeviceId(name)
        for (device in devices!!) {
            if (device.id == id || device.caption == name) {
                return true
            }
        }
        return false
    }

    private fun setupFinishView(view: View) {
        val sync = view.findViewById<Button>(R.id.butSyncNow)

        sync.setOnClickListener {
            dismiss()
            SynchronizationQueue.instance!!.syncImmediately()
        }
    }

    private fun advance() {
        if (currentStep < STEP_FINISH) {
            val view = viewFlipper.getChildAt(currentStep + 1)
            when (currentStep) {
                STEP_DEFAULT -> setupHostView(view)
                STEP_HOSTNAME -> setupLoginView(view)
                STEP_LOGIN -> {
                    if (username == null || password == null) {
                        throw IllegalStateException("Username and password must not be null here")
                    } else {
                        setupDeviceView(view)
                    }
                }
                STEP_DEVICE -> {
                    if (selectedDevice == null) {
                        throw IllegalStateException("Device must not be null here")
                    } else {
                        SynchronizationSettings.setSelectedSyncProvider(
                            SynchronizationProvider.GPODDER_NET.identifier
                        )
                        SynchronizationCredentials.setUsername(username)
                        SynchronizationCredentials.setPassword(password)
                        SynchronizationCredentials.setDeviceId(selectedDevice!!.id)
                        setupFinishView(view)
                    }
                }
            }
            if (currentStep != STEP_DEFAULT) {
                viewFlipper.showNext()
            }
            currentStep++
        } else {
            dismiss()
        }
    }

    private fun usernameHasUnwantedChars(username: String): Boolean {
        val special = Pattern.compile("[!@#$%&*()+=|<>?{}\\[\\]~]")
        val containsUnwantedChars = special.matcher(username)
        return containsUnwantedChars.find()
    }

    companion object {
        const val TAG = "GpodnetAuthActivity"

        private const val STEP_DEFAULT = -1
        private const val STEP_HOSTNAME = 0
        private const val STEP_LOGIN = 1
        private const val STEP_DEVICE = 2
        private const val STEP_FINISH = 3
    }
}
